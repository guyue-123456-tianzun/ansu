package com.ansu.alert;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ansu.safety.Decision;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AlertService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AlertService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public long createResistiveAlert(long dormId, String runId, Decision decision,
                                     List<Map<String, Object>> points, LocalDateTime eventTime) {
        String idem = "alert:" + runId + ":" + dormId + ":resistive:" + eventTime;
        List<Long> existing = jdbc.query("SELECT id FROM alert_log WHERE idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1), idem);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("before_s", 300);
        window.put("after_s", 300);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("window", window);
        snapshot.put("points", points);

        String snapshotJson;
        try {
            snapshotJson = json.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("证据快照序列化失败", e);
        }

        jdbc.update("""
                INSERT INTO alert_log
                (dorm_id, ts, level, alert_type, trigger_value, threshold, snapshot_json,
                 status, rule_version, demo_run_id, idempotency_key, notified_at)
                VALUES (?, ?, ?, 'resistive', ?, ?, ?, 'notified', ?, ?, ?, ?)
                """,
                dormId, eventTime, decision.level(), decision.steadyW(), decision.thresholdW(),
                snapshotJson, decision.thresholdVersion(), runId, idem, eventTime);

        Long alertId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                UPDATE dorm
                SET status = 'warned', active_alert_id = ?, active_run_id = ?, updated_at = ?
                WHERE id = ?
                """, alertId, runId, eventTime, dormId);

        List<Long> users = jdbc.query(
                "SELECT id FROM user_account WHERE dorm_id = ? AND role = 'student' AND status = 'active' LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), dormId);

        if (!users.isEmpty()) {
            long userId = users.get(0);
            String notifyIdem = "notify:" + userId + ":alert:" + alertId + ":alert";
            jdbc.update("""
                    INSERT IGNORE INTO notification
                    (user_id, type, title, body, ref_type, ref_id, status, idempotency_key)
                    VALUES (?, 'alert', ?, ?, 'alert', ?, 'sent', ?)
                    """,
                    userId,
                    "疑似阻性负载 " + decision.steadyW() + "W",
                    dormId + " 宿舍检测到功率阶跃，5 秒内未撤除将模拟分闸。",
                    alertId, notifyIdem);
        }

        jdbc.update("""
                INSERT IGNORE INTO action_log
                (subject_type, subject_id, action, actor_type, ts, detail_json, request_id, idempotency_key)
                VALUES ('alert', ?, 'alert_created', 'system', ?, '{}', ?, ?)
                """, alertId, eventTime, "alert-" + alertId, "create:" + alertId);

        return alertId;
    }
}
