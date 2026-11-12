package com.ansu.alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ansu.common.TimeUtil;

@Component
public class AlertScheduler {

    private final JdbcTemplate jdbc;

    public AlertScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 500)
    public void cutUnremoved() {
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        LocalDateTime deadline = now.minusSeconds(5);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, dorm_id FROM alert_log
                WHERE alert_type = 'resistive' AND status = 'notified' AND created_at <= ?
                ORDER BY id LIMIT 10
                """, deadline);

        for (Map<String, Object> row : rows) {
            long alertId = ((Number) row.get("id")).longValue();
            long dormId = ((Number) row.get("dorm_id")).longValue();

            Integer warns = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dorm
                    WHERE id = ? AND status = 'warned' AND active_alert_id = ?
                    """, Integer.class, dormId, alertId);
            if (warns == null || warns == 0) {
                continue;
            }

            Integer before = jdbc.queryForObject(
                    "SELECT credit_score FROM dorm WHERE id = ?", Integer.class, dormId);
            int creditBefore = before == null ? 100 : before;
            int creditAfter = Math.max(0, Math.min(100, creditBefore - 8));

            jdbc.update("""
                    UPDATE dorm
                    SET status = 'cut_off', violation_count = violation_count + 1,
                        last_violation_at = ?, credit_score = ?, updated_at = ?
                    WHERE id = ?
                    """, now, creditAfter, now, dormId);

            jdbc.update("UPDATE alert_log SET status = 'resolved', resolved_at = ? WHERE id = ?",
                    now, alertId);

            jdbc.update("""
                    INSERT IGNORE INTO credit_ledger
                    (dorm_id, delta, reason, ref_type, ref_id, balance_after, idempotency_key)
                    VALUES (?, -8, 'violation', 'alert', ?, ?, ?)
                    """, dormId, alertId, creditAfter, "violation:" + alertId);

            jdbc.update("""
                    INSERT IGNORE INTO action_log
                    (subject_type, subject_id, action, actor_type, ts, detail_json, request_id, idempotency_key)
                    VALUES ('alert', ?, 'cut_off', 'system', ?, '{}', ?, ?)
                    """, alertId, now, "cut-" + alertId, "cut:" + alertId);

            List<Long> users = jdbc.query(
                    "SELECT id FROM user_account WHERE dorm_id = ? AND role = 'student' LIMIT 1",
                    (rs, rowNum) -> rs.getLong(1), dormId);
            if (!users.isEmpty()) {
                long userId = users.get(0);
                jdbc.update("""
                        INSERT IGNORE INTO notification
                        (user_id, type, title, body, ref_type, ref_id, status, idempotency_key)
                        VALUES (?, 'alert', ?, ?, 'alert', ?, 'sent', ?)
                        """, userId, "已模拟分闸", dormId + " 宿舍未在 5 秒内撤除，已模拟分闸。",
                        alertId, "notify:" + userId + ":alert:" + alertId + ":cut");
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void recoverCutOff() {
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, active_alert_id FROM dorm
                WHERE status = 'cut_off' AND active_alert_id IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            long dormId = ((Number) row.get("id")).longValue();
            long alertId = ((Number) row.get("active_alert_id")).longValue();
            List<Double> powers = jdbc.query("""
                    SELECT p_active_kw FROM load_record
                    WHERE dorm_id = ? AND ts >= ?
                    ORDER BY ts DESC LIMIT 30
                    """, (rs, rowNum) -> rs.getDouble(1), dormId, now.minusSeconds(30));
            if (powers.size() < 25 || powers.stream().anyMatch(p -> p >= 0.05)) {
                continue;
            }
            jdbc.update("""
                    UPDATE dorm SET status = 'normal', active_alert_id = NULL, updated_at = ?
                    WHERE id = ? AND status = 'cut_off'
                    """, now, dormId);
            jdbc.update("UPDATE alert_log SET status = 'resolved', resolved_at = COALESCE(resolved_at, ?) WHERE id = ?",
                    now, alertId);
            jdbc.update("""
                    INSERT IGNORE INTO action_log
                    (subject_type, subject_id, action, actor_type, ts, detail_json, request_id, idempotency_key)
                    VALUES ('alert', ?, 'auto_recover', 'system', ?, '{}', ?, ?)
                    """, alertId, now, "recover-" + alertId, "auto_recover:" + alertId);
        }
    }
}
