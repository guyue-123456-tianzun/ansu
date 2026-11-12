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
            int creditAfter = Math.max(0, Math.min(120, creditBefore - 8));

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
}
