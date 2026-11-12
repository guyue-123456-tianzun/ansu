package com.ansu.appeal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.TimeUtil;
import com.ansu.dashboard.QueryService;

@Service
public class AppealService {

    private final JdbcTemplate jdbc;
    private final QueryService queryService;

    public AppealService(JdbcTemplate jdbc, QueryService queryService) {
        this.jdbc = jdbc;
        this.queryService = queryService;
    }

    public record SubmitRequest(Long alertId, String reasonType, String statement) {
    }

    public record ReviewRequest(String decision, String note) {
    }

    @Transactional
    public Map<String, Object> submit(UserPrincipal student, SubmitRequest request) {
        if (request.alertId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "alertId 不能为空");
        }
        if (!List.of("misjudge", "device_fault", "special").contains(request.reasonType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reasonType 不合法");
        }
        if (request.statement() == null || request.statement().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写申诉说明");
        }

        List<Map<String, Object>> alerts = jdbc.queryForList("""
                SELECT a.id, a.dorm_id, a.snapshot_json
                FROM alert_log a WHERE a.id = ? AND a.dorm_id = ?
                """, request.alertId(), student.dormId());
        if (alerts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在或不属于本宿舍");
        }
        Map<String, Object> alert = alerts.get(0);

        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, status, deadline_at FROM appeal
                WHERE alert_id = ? ORDER BY id DESC LIMIT 1
                """, request.alertId());
        if (!existing.isEmpty()) {
            Map<String, Object> old = existing.get(0);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("appeal_id", old.get("id"));
            data.put("status", old.get("status"));
            data.put("deadline_at", TimeUtil.iso(toLdt(old.get("deadline_at"))));
            data.put("replayed", true);
            return data;
        }

        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        LocalDateTime deadline = now.plusHours(24);
        if (now.toLocalTime().isAfter(LocalTime.of(22, 0)) || now.toLocalTime().isBefore(LocalTime.of(6, 0))) {
            LocalDateTime nextMorning = now.toLocalDate().plusDays(1).atTime(10, 0);
            if (nextMorning.isAfter(deadline)) {
                deadline = nextMorning;
            }
        }

        List<Integer> penalties = jdbc.query("""
                SELECT -delta FROM credit_ledger
                WHERE ref_type = 'alert' AND ref_id = ? AND reason = 'violation'
                ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getInt(1), request.alertId());
        int originalPenalty = penalties.isEmpty() ? 8 : penalties.get(0);

        String idem = "appeal:" + request.alertId();
        jdbc.update("""
                INSERT IGNORE INTO appeal
                (alert_id, dorm_id, submitted_by, reason_type, statement, evidence_json,
                 status, deadline_at, original_penalty, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, 'submitted', ?, ?, ?)
                """, request.alertId(), student.dormId(), student.id(), request.reasonType(),
                request.statement(), alert.get("snapshot_json"), deadline, originalPenalty, idem);

        List<Long> ids = jdbc.query("SELECT id FROM appeal WHERE idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1), idem);
        long appealId = ids.get(0);

        jdbc.update("UPDATE alert_log SET status = 'reviewing' WHERE id = ?", request.alertId());
        jdbc.update("""
                INSERT IGNORE INTO action_log
                (subject_type, subject_id, action, actor_type, actor_id, ts, detail_json, request_id, idempotency_key)
                VALUES ('appeal', ?, 'appeal_submitted', 'student', ?, ?, '{}', ?, ?)
                """, appealId, student.id(), now, "appeal-" + appealId, "appeal_submit:" + appealId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appeal_id", appealId);
        data.put("status", "submitted");
        data.put("deadline_at", TimeUtil.iso(deadline));
        data.put("replayed", false);
        return data;
    }

    public List<Map<String, Object>> listStudent(long dormId) {
        return jdbc.queryForList("""
                SELECT p.id, p.alert_id, p.reason_type, p.statement, p.status,
                       p.deadline_at, p.review_note, p.created_at, d.room_no
                FROM appeal p JOIN dorm d ON d.id = p.dorm_id
                WHERE p.dorm_id = ? ORDER BY p.id DESC
                """, dormId);
    }

    public List<Map<String, Object>> listManager(String status) {
        String where = status == null || status.isBlank() ? "" : " WHERE p.status = ?";
        String sql = """
                SELECT p.id, p.alert_id, p.dorm_id, d.room_no, p.reason_type, p.statement,
                       p.status, p.deadline_at, p.review_note, p.created_at,
                       u.display_name AS submitter
                FROM appeal p
                JOIN dorm d ON d.id = p.dorm_id
                JOIN user_account u ON u.id = p.submitted_by
                """ + where + " ORDER BY p.id DESC LIMIT 100";
        return status == null || status.isBlank()
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, status);
    }

    @Transactional
    public Map<String, Object> review(UserPrincipal reviewer, long appealId, ReviewRequest request) {
        String decision = request.decision();
        if (!List.of("approved", "rejected").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 只支持 approved/rejected");
        }
        if ("rejected".equals(decision) && (request.note() == null || request.note().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回必须写理由");
        }

        List<Map<String, Object>> appeals = jdbc.queryForList("""
                SELECT p.*, d.credit_score, d.status AS dorm_status, a.demo_run_id
                FROM appeal p
                JOIN dorm d ON d.id = p.dorm_id
                JOIN alert_log a ON a.id = p.alert_id
                WHERE p.id = ?
                """, appealId);
        if (appeals.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申诉不存在");
        }
        Map<String, Object> appeal = appeals.get(0);
        String status = String.valueOf(appeal.get("status"));
        if (!List.of("submitted", "reviewing").contains(status)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "申诉已处理");
        }

        long alertId = ((Number) appeal.get("alert_id")).longValue();
        long dormId = ((Number) appeal.get("dorm_id")).longValue();
        long studentId = ((Number) appeal.get("submitted_by")).longValue();
        int originalPenalty = ((Number) appeal.get("original_penalty")).intValue();
        int credit = ((Number) appeal.get("credit_score")).intValue();
        String runId = appeal.get("demo_run_id") == null ? null : String.valueOf(appeal.get("demo_run_id"));
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);

        jdbc.update("""
                UPDATE appeal SET status = ?, reviewer_id = ?, review_note = ?, reviewed_at = ?
                WHERE id = ?
                """, decision, reviewer.id(), request.note(), now, appealId);

        if ("approved".equals(decision)) {
            queryService.forceLowPower(dormId, now, runId);
            int after = Math.max(0, Math.min(100, credit + originalPenalty));
            jdbc.update("""
                    UPDATE dorm SET status = 'normal', active_alert_id = NULL,
                        credit_score = ?, updated_at = ?
                    WHERE id = ?
                    """, after, now, dormId);
            jdbc.update("""
                    UPDATE alert_log SET status = 'resolved', penalty_reverted = 1,
                        resolved_at = COALESCE(resolved_at, ?)
                    WHERE id = ?
                    """, now, alertId);
            jdbc.update("""
                    INSERT IGNORE INTO credit_ledger
                    (dorm_id, delta, reason, ref_type, ref_id, balance_after, idempotency_key)
                    VALUES (?, ?, 'appeal_revert', 'appeal', ?, ?, ?)
                    """, dormId, originalPenalty, appealId, after, "appeal_revert:" + appealId);
            notify(studentId, "申诉已通过", "本次扣分已撤销，用电已恢复。", "appeal", appealId);
        } else {
            jdbc.update("UPDATE alert_log SET status = 'resolved', resolved_at = COALESCE(resolved_at, ?) WHERE id = ?",
                    now, alertId);
            notify(studentId, "申诉被驳回", "理由：" + request.note(), "appeal", appealId);
        }

        jdbc.update("""
                INSERT IGNORE INTO action_log
                (subject_type, subject_id, action, actor_type, actor_id, ts, detail_json, request_id, idempotency_key)
                VALUES ('appeal', ?, ?, 'manager', ?, ?, '{}', ?, ?)
                """, appealId, "appeal_" + decision, reviewer.id(), now,
                "review-" + appealId, "review:" + appealId + ":" + decision);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appeal_id", appealId);
        data.put("status", decision);
        data.put("dorm_status", "approved".equals(decision) ? "normal" : "cut_off");
        data.put("credit_reverted", "approved".equals(decision) ? originalPenalty : 0);
        return data;
    }

    private void notify(long userId, String title, String body, String refType, long refId) {
        jdbc.update("""
                INSERT IGNORE INTO notification
                (user_id, type, title, body, ref_type, ref_id, status, idempotency_key)
                VALUES (?, 'appeal', ?, ?, ?, ?, 'sent', ?)
                """, userId, title, body, refType, refId,
                "notify:" + userId + ":appeal:" + refId + ":" + title);
    }

    private LocalDateTime toLdt(Object value) {
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime ldt) return ldt;
        return null;
    }
}
