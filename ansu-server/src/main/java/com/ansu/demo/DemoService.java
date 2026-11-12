package com.ansu.demo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ansu.alert.AlertService;
import com.ansu.common.TimeUtil;
import com.ansu.safety.Decision;
import com.ansu.safety.SafetyEngine;

@Service
public class DemoService {

    private final JdbcTemplate jdbc;
    private final SafetyEngine safetyEngine;
    private final AlertService alertService;

    public DemoService(JdbcTemplate jdbc, SafetyEngine safetyEngine, AlertService alertService) {
        this.jdbc = jdbc;
        this.safetyEngine = safetyEngine;
        this.alertService = alertService;
    }

    public record InjectRequest(String demoRunId, Long dormId, String device,
                                Integer durationS, String scene) {
    }

    private record DeviceProfile(int watts, double pf, int stepW) {
    }

    private DeviceProfile profile(String device) {
        if (device == null) {
            throw new IllegalArgumentException("device 不能为空");
        }
        return switch (device) {
            case "kettle_800", "heat_stick" -> new DeviceProfile(812, 0.998, 780);
            case "hair_dryer_1800" -> new DeviceProfile(1800, 0.80, 1750);
            case "pc_1200" -> new DeviceProfile(1200, 0.97, 900);
            default -> throw new IllegalArgumentException("不支持的设备: " + device);
        };
    }

    @Transactional
    public Map<String, Object> inject(InjectRequest request) {
        if (request.dormId() == null) {
            throw new IllegalArgumentException("dormId 不能为空");
        }
        long dormId = request.dormId();
        Integer dormCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dorm WHERE id = ?", Integer.class, dormId);
        if (dormCount == null || dormCount == 0) {
            throw new IllegalArgumentException("寝室不存在: " + dormId);
        }

        String runId = request.demoRunId();
        if (runId == null || runId.isBlank()) {
            runId = activeRunId();
        }

        DeviceProfile device = profile(request.device());
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE).withNano(0);
        String idem = "alert:" + runId + ":" + dormId + ":resistive:" + now;

        List<Long> existing = jdbc.query(
                "SELECT id FROM alert_log WHERE idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1), idem);
        if (!existing.isEmpty()) {
            long alertId = existing.get(0);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("run_id", runId);
            data.put("alert_id", alertId);
            data.put("target_state", "warned");
            data.put("expected", "cut_off_after_5s");
            data.put("replayed", true);
            return data;
        }

        List<Map<String, Object>> points = new ArrayList<>();
        long lastEventId = 0;
        for (int i = 0; i < 8; i++) {
            LocalDateTime ts = now.minusSeconds(7L - i);
            boolean hot = i >= 3;
            int powerW = hot ? device.watts() : 32;
            double pf = hot ? device.pf() : 0.98;
            double powerKw = powerW / 1000.0;
            jdbc.update("""
                    INSERT IGNORE INTO load_record
                    (dorm_id, ts, p_active_kw, pf, leakage_ma, temp_c, source, demo_run_id)
                    VALUES (?, ?, ?, ?, ?, ?, 'mock_event', ?)
                    """, dormId, ts, powerKw, pf, 18, hot ? 42.0 : 28.0, runId);
            Long eventId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (eventId != null && eventId > 0) {
                lastEventId = eventId;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("ts", TimeUtil.iso(ts));
            point.put("p_w", powerW);
            point.put("pf", pf);
            point.put("leakage_ma", 18);
            point.put("temp_c", hot ? 42.0 : 28.0);
            points.add(point);
        }

        Decision decision = safetyEngine.evaluate(device.watts(), device.pf(), device.stepW(),
                5.0, 18, 42, now.toLocalTime());

        Long alertId = null;
        if (decision.violation()) {
            alertId = alertService.createResistiveAlert(dormId, runId, decision, points, now);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("run_id", runId);
        data.put("event_id", lastEventId);
        data.put("alert_id", alertId);
        data.put("target_state", decision.violation() ? "warned" : "normal");
        data.put("expected", decision.violation() ? "cut_off_after_5s" : "none");
        data.put("decision", Map.of(
                "channel", decision.channel(),
                "level", decision.level(),
                "action", decision.action(),
                "rule_ref", decision.ruleRef(),
                "threshold_version", decision.thresholdVersion(),
                "evidence", Map.of(
                        "pf", decision.pf(),
                        "step_w", decision.stepW(),
                        "steady_w", decision.steadyW(),
                        "duration_s", decision.durationS(),
                        "threshold_w", decision.thresholdW()
                )
        ));
        data.put("replayed", false);
        return data;
    }

    @Transactional
    public String switchScene(String scene, Long seed) {
        String safeScene = scene == null ? "violation" : scene;
        if (!List.of("normal", "violation", "appeal", "night").contains(safeScene)) {
            throw new IllegalArgumentException("不支持的场景: " + safeScene);
        }
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        jdbc.update("UPDATE demo_run SET status = 'archived', ended_at = ? WHERE status = 'active'", now);
        jdbc.update("DELETE FROM notification WHERE ref_type = 'alert'");
        jdbc.update("""
                DELETE p FROM appeal p JOIN alert_log a ON a.id = p.alert_id
                WHERE a.demo_run_id IS NOT NULL
                """);
        jdbc.update("""
                DELETE lr FROM learning_record lr JOIN alert_log a ON a.id = lr.alert_id
                WHERE a.demo_run_id IS NOT NULL
                """);
        jdbc.update("DELETE FROM credit_ledger");
        jdbc.update("DELETE FROM alert_log WHERE demo_run_id IS NOT NULL");
        jdbc.update("DELETE FROM load_record WHERE demo_run_id IS NOT NULL");

        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbc.update("""
                INSERT INTO demo_run (id, scene, seed, status, started_at)
                VALUES (?, ?, ?, 'active', ?)
                """, runId, safeScene, seed == null ? 20261110L : seed, now);
        jdbc.update("""
                UPDATE dorm
                SET status = 'normal', active_alert_id = NULL, active_run_id = ?,
                    violation_count = 0, credit_score = 100,
                    last_violation_at = NULL, lock_until = NULL, updated_at = ?
                """, runId, now);
        return runId;
    }

    public String activeRunId() {
        List<String> runs = jdbc.query(
                "SELECT id FROM demo_run WHERE status = 'active' ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1));
        if (!runs.isEmpty()) {
            return runs.get(0);
        }
        return switchScene("violation", 20261110L);
    }

    @Transactional
    public String reset() {
        return switchScene("normal", 20261110L);
    }
}
