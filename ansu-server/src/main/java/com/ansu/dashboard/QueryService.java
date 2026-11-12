package com.ansu.dashboard;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ansu.common.TimeUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class QueryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public QueryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Map<String, Object>> dorms() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.room_no, d.floor, d.status, d.credit_score, d.violation_count,
                       d.lock_until,
                       (SELECT l.p_active_kw FROM load_record l
                        WHERE l.dorm_id = d.id ORDER BY l.ts DESC LIMIT 1) AS p_kw
                FROM dorm d ORDER BY d.id
                """);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", l(row.get("id")));
            item.put("room_no", s(row.get("room_no")));
            item.put("floor", l(row.get("floor")));
            item.put("status", s(row.get("status")));
            item.put("credit_score", l(row.get("credit_score")));
            item.put("violation_count", l(row.get("violation_count")));
            item.put("lock_until", TimeUtil.iso(ldt(row.get("lock_until"))));
            item.put("power_w", Math.round(d(row.get("p_kw")) * 1000));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> summary() {
        List<Map<String, Object>> dormList = dorms();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("normal", 0L);
        counts.put("warned", 0L);
        counts.put("cut_off", 0L);
        counts.put("locked", 0L);
        long totalPower = 0;
        for (Map<String, Object> dorm : dormList) {
            String status = s(dorm.get("status"));
            counts.computeIfPresent(status, (k, v) -> v + 1);
            totalPower += l(dorm.get("power_w"));
        }

        Long totalAlerts = jdbc.queryForObject("SELECT COUNT(*) FROM alert_log", Long.class);
        Long closedAlerts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_log WHERE status IN ('resolved','closed')", Long.class);
        double closureRate = totalAlerts == null || totalAlerts == 0
                ? 0.0
                : (closedAlerts == null ? 0.0 : closedAlerts.doubleValue() / totalAlerts);

        Double todayKwh = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p_active_kw) / 60.0, 0)
                FROM load_record WHERE ts >= ?
                """, Double.class, LocalDate.now(TimeUtil.ZONE).atStartOfDay());

        List<String> runs = jdbc.query(
                "SELECT id FROM demo_run WHERE status = 'active' ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("run_id", runs.isEmpty() ? null : runs.get(0));
        data.put("building", Map.of("id", 1, "name", "1 号楼", "dorm_count", dormList.size()));
        data.put("counts", counts);
        data.put("closure_rate", Math.round(closureRate * 100) / 100.0);
        data.put("total_power_w", totalPower);
        data.put("today_kwh", todayKwh == null ? 0.0 : Math.round(todayKwh * 10) / 10.0);
        data.put("alerts", alerts(20));
        data.put("dorms", dormList);
        return data;
    }

    public List<Map<String, Object>> alerts(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.dorm_id, d.room_no, a.level, a.alert_type, a.status,
                       a.trigger_value, a.threshold, a.ts, a.created_at
                FROM alert_log a JOIN dorm d ON d.id = a.dorm_id
                ORDER BY a.id DESC LIMIT ?
                """, Math.max(1, Math.min(limit, 200)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", l(row.get("id")));
            item.put("dorm_id", l(row.get("dorm_id")));
            item.put("room_no", s(row.get("room_no")));
            item.put("level", s(row.get("level")));
            item.put("type", s(row.get("alert_type")));
            item.put("status", s(row.get("status")));
            item.put("trigger_value", d(row.get("trigger_value")));
            item.put("threshold", d(row.get("threshold")));
            item.put("ts", TimeUtil.iso(ldt(row.get("ts"))));
            item.put("created_at", TimeUtil.iso(ldt(row.get("created_at"))));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> load(long dormId, int minutes) {
        int safeMinutes = Math.max(5, Math.min(minutes, 1440));
        LocalDateTime from = LocalDateTime.now(TimeUtil.ZONE).minusMinutes(safeMinutes);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ts, p_active_kw, pf, leakage_ma, temp_c, source
                FROM load_record WHERE dorm_id = ? AND ts >= ?
                ORDER BY ts ASC LIMIT 2000
                """, dormId, from);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ts", TimeUtil.iso(ldt(row.get("ts"))));
            item.put("p_w", Math.round(d(row.get("p_active_kw")) * 1000));
            item.put("pf", d(row.get("pf")));
            item.put("leakage_ma", l(row.get("leakage_ma")));
            item.put("temp_c", d(row.get("temp_c")));
            item.put("source", s(row.get("source")));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> alertDetail(long alertId, Long dormId) {
        String sql = """
                SELECT a.*, d.room_no FROM alert_log a
                JOIN dorm d ON d.id = a.dorm_id
                WHERE a.id = ?
                """ + (dormId == null ? "" : " AND a.dorm_id = ?");
        List<Map<String, Object>> rows = dormId == null
                ? jdbc.queryForList(sql, alertId)
                : jdbc.queryForList(sql, alertId, dormId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        LocalDateTime ts = ldt(row.get("ts"));
        LocalDateTime notified = ldt(row.get("notified_at"));
        LocalDateTime resolved = ldt(row.get("resolved_at"));

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", l(row.get("id")));
        alert.put("dorm_id", l(row.get("dorm_id")));
        alert.put("room_no", s(row.get("room_no")));
        alert.put("level", s(row.get("level")));
        alert.put("type", s(row.get("alert_type")));
        alert.put("status", s(row.get("status")));
        alert.put("trigger_value", d(row.get("trigger_value")));
        alert.put("threshold", d(row.get("threshold")));
        alert.put("created_at", TimeUtil.iso(ldt(row.get("created_at"))));

        List<Map<String, Object>> timeline = new ArrayList<>();
        if (ts != null) timeline.add(event(ts, "识别"));
        if (notified != null) timeline.add(event(notified, "提醒"));
        if (resolved != null) timeline.add(event(resolved, "已处理"));
        alert.put("timeline", timeline);

        JsonNode evidence = json.createObjectNode();
        Object snapshot = row.get("snapshot_json");
        if (snapshot != null) {
            try {
                evidence = json.readTree(String.valueOf(snapshot));
            } catch (Exception ignored) {
                evidence = json.createObjectNode();
            }
        }

        double steady = d(row.get("trigger_value"));
        double threshold = d(row.get("threshold"));
        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("what", TimeUtil.shortTime(ts) + " 功率阶跃到 " + Math.round(steady)
                + "W，PF 0.998，持续 5.0 秒。");
        explain.put("why", "PF ≥ 0.95；阶跃 ≥ 300W；持续 ≥ 3s；非白名单。当前处置线 "
                + Math.round(threshold) + "W。");
        explain.put("action", "拔掉设备 30 秒可恢复；如果是误判可申诉；首次违规可完成安全学习提前恢复。");
        explain.put("gen_by", "tmpl");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alert", alert);
        data.put("evidence", evidence);
        data.put("explain", explain);
        data.put("available_actions", List.of("remove", "appeal", "learn"));
        return data;
    }

    public Map<String, Object> studentOverview(long dormId, long userId) {
        List<Map<String, Object>> dorms = jdbc.queryForList("""
                SELECT id, room_no, status, credit_score, violation_count, lock_until
                FROM dorm WHERE id = ?
                """, dormId);
        if (dorms.isEmpty()) {
            return null;
        }
        Map<String, Object> dormRow = dorms.get(0);
        Map<String, Object> dorm = new LinkedHashMap<>();
        dorm.put("id", l(dormRow.get("id")));
        dorm.put("room_no", s(dormRow.get("room_no")));
        dorm.put("status", s(dormRow.get("status")));
        dorm.put("credit_score", l(dormRow.get("credit_score")));
        dorm.put("violation_count", l(dormRow.get("violation_count")));
        dorm.put("lock_until", TimeUtil.iso(ldt(dormRow.get("lock_until"))));

        List<Double> powers = jdbc.query("""
                SELECT p_active_kw FROM load_record WHERE dorm_id = ?
                ORDER BY ts DESC LIMIT 1
                """, (rs, rowNum) -> rs.getDouble(1), dormId);
        double powerKw = powers.isEmpty() ? 0.0 : powers.get(0);

        Double todayKwh = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p_active_kw) / 60.0, 0)
                FROM load_record WHERE dorm_id = ? AND ts >= ?
                """, Double.class, dormId, LocalDate.now(TimeUtil.ZONE).atStartOfDay());

        Long unread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification WHERE user_id = ? AND read_at IS NULL
                """, Long.class, userId);

        List<Map<String, Object>> activeRows = jdbc.queryForList("""
                SELECT id, level, alert_type, status, created_at FROM alert_log
                WHERE dorm_id = ? ORDER BY id DESC LIMIT 1
                """, dormId);

        Map<String, Object> activeAlert = null;
        if (!activeRows.isEmpty()) {
            Map<String, Object> a = activeRows.get(0);
            activeAlert = new LinkedHashMap<>();
            activeAlert.put("id", l(a.get("id")));
            activeAlert.put("level", s(a.get("level")));
            activeAlert.put("type", s(a.get("alert_type")));
            activeAlert.put("status", s(a.get("status")));
            activeAlert.put("title", "疑似阻性负载");
            activeAlert.put("created_at", TimeUtil.iso(ldt(a.get("created_at"))));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dorm", dorm);
        data.put("power_w", Math.round(powerKw * 1000));
        data.put("today_kwh", todayKwh == null ? 0.0 : Math.round(todayKwh * 10) / 10.0);
        data.put("unread_notifications", unread == null ? 0 : unread);
        data.put("active_alert", activeAlert);
        data.put("active_appeal", null);
        data.put("learning_task", null);
        return data;
    }

    public List<Map<String, Object>> rawAlert(long alertId, Long dormId) {
        return jdbc.queryForList("""
                SELECT a.id, a.dorm_id, a.demo_run_id, d.status AS dorm_status
                FROM alert_log a JOIN dorm d ON d.id = a.dorm_id
                WHERE a.id = ? AND a.dorm_id = ?
                """, alertId, dormId);
    }

    public void updateDormNormal(long dormId, LocalDateTime now) {
        jdbc.update("""
                UPDATE dorm SET status = 'normal', active_alert_id = NULL, updated_at = ?
                WHERE id = ?
                """, now, dormId);
    }

    public void resolveAlert(long alertId, LocalDateTime now) {
        jdbc.update("""
                UPDATE alert_log SET status = 'resolved', resolved_at = COALESCE(resolved_at, ?)
                WHERE id = ?
                """, now, alertId);
    }

    public void logAction(long alertId, String action, LocalDateTime now, String idem) {
        jdbc.update("""
                INSERT IGNORE INTO action_log
                (subject_type, subject_id, action, actor_type, ts, detail_json, request_id, idempotency_key)
                VALUES ('alert', ?, ?, 'student', ?, '{}', ?, ?)
                """, alertId, action, now, "student-" + alertId, idem);
    }

    public void forceLowPower(long dormId, LocalDateTime now, String runId) {
        jdbc.update("""
                UPDATE load_record SET p_active_kw = 0.032, pf = 0.98,
                    leakage_ma = 5, temp_c = 28.0
                WHERE dorm_id = ? AND ts >= ? AND source = 'mock_event'
                """, dormId, now.minusSeconds(30));
        for (int i = 0; i <= 30; i++) {
            insertLowPower(dormId, now.minusSeconds(30).plusSeconds(i), runId);
        }
    }

    public void insertLowPower(long dormId, LocalDateTime ts, String runId) {
        jdbc.update("""
                INSERT IGNORE INTO load_record
                (dorm_id, ts, p_active_kw, pf, leakage_ma, temp_c, source, demo_run_id)
                VALUES (?, ?, 0.032, 0.98, 5, 28.0, 'mock_event', ?)
                """, dormId, ts, runId);
    }

    private Map<String, Object> event(LocalDateTime ts, String name) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("at", TimeUtil.iso(ts));
        event.put("event", name);
        return event;
    }

    private long l(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private double d(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private String s(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime ldt(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime ldt) return ldt;
        return null;
    }
}
