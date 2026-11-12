package com.ansu.learning;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LearningService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final QueryService queryService;
    private final Map<String, Course> courses = new LinkedHashMap<>();

    public LearningService(JdbcTemplate jdbc, ObjectMapper json, QueryService queryService) {
        this.jdbc = jdbc;
        this.json = json;
        this.queryService = queryService;
        initCourses();
    }

    public record Section(String title, String content) {
    }

    public record Question(String stem, List<String> options, int answerIndex, String explanation) {
    }

    public record Course(String id, String title, String type, List<Section> sections, List<Question> questions) {
    }

    public record CompleteRequest(Long alertId, String courseId, List<Integer> answers, Integer attemptNo) {
    }

    public Map<String, Object> task(UserPrincipal student, long alertId) {
        Map<String, Object> alert = alert(student, alertId);
        Course course = courseFor(String.valueOf(alert.get("alert_type")));
        int attempt = nextAttempt(student.id(), course.id(), alertId);

        List<Map<String, Object>> questions = new ArrayList<>();
        int no = 1;
        for (Question q : course.questions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("no", no++);
            item.put("type", "single");
            item.put("stem", q.stem());
            item.put("options", q.options());
            questions.add(item);
        }

        List<Map<String, Object>> material = new ArrayList<>();
        for (Section section : course.sections()) {
            material.add(Map.of("title", section.title(), "content", section.content()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alert_id", alertId);
        data.put("course_id", course.id());
        data.put("title", course.title());
        data.put("material", material);
        data.put("questions", questions);
        data.put("pass_score", 80);
        data.put("attempt_no", attempt);
        data.put("time_limit_s", 600);
        return data;
    }

    @Transactional
    public Map<String, Object> complete(UserPrincipal student, CompleteRequest request) {
        if (request.alertId() == null || request.answers() == null || request.answers().size() != 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需要 5 个答案");
        }
        Map<String, Object> alert = alert(student, request.alertId());
        Course course = courseFor(String.valueOf(alert.get("alert_type")));
        if (!course.id().equals(request.courseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId 与告警类型不匹配");
        }
        int attempt = request.attemptNo() == null ? 1 : request.attemptNo();
        if (attempt < 1 || attempt > 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只允许两次测验");
        }

        long dormId = ((Number) alert.get("dorm_id")).longValue();
        String runId = alert.get("demo_run_id") == null ? null : String.valueOf(alert.get("demo_run_id"));
        int violationCount = ((Number) alert.get("violation_count")).intValue();
        if (violationCount >= 3) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "第 3 次违规学习不能解锁");
        }

        int score = 0;
        for (int i = 0; i < 5; i++) {
            if (request.answers().get(i) == course.questions().get(i).answerIndex()) {
                score += 20;
            }
        }
        boolean passed = score >= 80;

        String idem = "learning:" + request.alertId() + ":" + course.id() + ":" + attempt;
        List<Long> existing = jdbc.query("SELECT id FROM learning_record WHERE idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1), idem);
        if (!existing.isEmpty()) {
            return result(score, passed, attempt, 0, currentCredit(dormId),
                    passed ? "restored" : "retry", null, true);
        }

        String answersJson;
        try {
            answersJson = json.writeValueAsString(request.answers());
        } catch (Exception e) {
            throw new IllegalStateException("答案序列化失败", e);
        }
        String certificate = passed
                ? "cert_" + LocalDateTime.now(TimeUtil.ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "_" + dormId
                : null;

        jdbc.update("""
                INSERT INTO learning_record
                (user_id, dorm_id, alert_id, course_id, course_type, score, answers_json,
                 passed, attempt_no, certificate_id, material_viewed, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """, student.id(), dormId, request.alertId(), course.id(), course.type(),
                score, answersJson, passed ? 1 : 0, attempt, certificate, idem);

        Long recordId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        int creditDelta = 0;

        if (passed) {
            if (violationCount <= 1) {
                queryService.forceLowPower(dormId, now, runId);
                jdbc.update("UPDATE dorm SET status = 'normal', active_alert_id = NULL, updated_at = ? WHERE id = ?",
                        now, dormId);
                jdbc.update("UPDATE alert_log SET status = 'resolved', resolved_at = COALESCE(resolved_at, ?) WHERE id = ?",
                        now, request.alertId());
                creditDelta = awardLearningCredit(dormId, course.id(), recordId, now);
            } else {
                jdbc.update("UPDATE alert_log SET status = 'reviewing' WHERE id = ?", request.alertId());
            }
            jdbc.update("""
                    INSERT IGNORE INTO action_log
                    (subject_type, subject_id, action, actor_type, actor_id, ts, detail_json, request_id, idempotency_key)
                    VALUES ('learning', ?, 'learning_passed', 'student', ?, ?, '{}', ?, ?)
                    """, recordId, student.id(), now, "learning-" + recordId, "learning_pass:" + recordId);
        } else {
            if (attempt == 1) {
                jdbc.update("""
                        INSERT IGNORE INTO action_log
                        (subject_type, subject_id, action, actor_type, actor_id, ts, detail_json, request_id, idempotency_key)
                        VALUES ('learning', ?, 'learning_failed', 'student', ?, ?, '{}', ?, ?)
                        """, recordId, student.id(), now, "learning-" + recordId, "learning_fail:" + recordId);
            } else {
                jdbc.update("UPDATE alert_log SET status = 'reviewing' WHERE id = ?", request.alertId());
            }
        }

        String next = passed ? "restored" : (attempt == 1 ? "retry" : "reviewing");
        return result(score, passed, attempt, creditDelta, currentCredit(dormId), next, certificate, false);
    }

    public List<Map<String, Object>> records(long dormId) {
        return jdbc.queryForList("""
                SELECT id, alert_id, course_id, score, passed, attempt_no, credit_awarded,
                       certificate_id, completed_at
                FROM learning_record WHERE dorm_id = ? ORDER BY id DESC LIMIT 50
                """, dormId);
    }

    private int awardLearningCredit(long dormId, String courseId, long recordId, LocalDateTime now) {
        String month = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String idem = "learning_credit:" + dormId + ":" + courseId + ":" + month;
        List<Long> existing = jdbc.query("SELECT id FROM credit_ledger WHERE idempotency_key = ?",
                (rs, rowNum) -> rs.getLong(1), idem);
        if (!existing.isEmpty()) {
            return 0;
        }
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        Integer awarded = jdbc.queryForObject("""
                SELECT COALESCE(SUM(delta), 0) FROM credit_ledger
                WHERE dorm_id = ? AND reason = 'learning' AND created_at >= ?
                """, Integer.class, dormId, monthStart);
        int remaining = Math.max(0, 6 - (awarded == null ? 0 : awarded));
        if (remaining <= 0) {
            return 0;
        }
        int delta = Math.min(3, remaining);
        int before = currentCredit(dormId);
        int after = Math.min(120, before + delta);
        jdbc.update("""
                INSERT INTO credit_ledger
                (dorm_id, delta, reason, ref_type, ref_id, balance_after, idempotency_key)
                VALUES (?, ?, 'learning', 'learning', ?, ?, ?)
                """, dormId, delta, recordId, after, idem);
        jdbc.update("UPDATE dorm SET credit_score = ?, updated_at = ? WHERE id = ?", after, now, dormId);
        jdbc.update("UPDATE learning_record SET credit_awarded = ? WHERE id = ?", delta, recordId);
        return delta;
    }

    private Map<String, Object> result(int score, boolean passed, int attempt, int creditDelta,
                                       int creditAfter, String next, String certificate, boolean replayed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", score);
        data.put("passed", passed);
        data.put("attempt_no", attempt);
        data.put("credit_delta", creditDelta);
        data.put("credit_after", creditAfter);
        data.put("dorm_status", passed ? "normal" : "cut_off");
        data.put("next_action", next);
        data.put("certificate_id", certificate);
        data.put("replayed", replayed);
        return data;
    }

    private Map<String, Object> alert(UserPrincipal student, long alertId) {
        List<Map<String, Object>> alerts = jdbc.queryForList("""
                SELECT a.id, a.dorm_id, a.alert_type, a.demo_run_id, d.violation_count
                FROM alert_log a JOIN dorm d ON d.id = a.dorm_id
                WHERE a.id = ? AND a.dorm_id = ?
                """, alertId, student.dormId());
        if (alerts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在或不属于本宿舍");
        }
        return alerts.get(0);
    }

    private int nextAttempt(long userId, String courseId, long alertId) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_no), 0) FROM learning_record
                WHERE user_id = ? AND course_id = ? AND alert_id = ?
                """, Integer.class, userId, courseId, alertId);
        return (max == null ? 0 : max) + 1;
    }

    private int currentCredit(long dormId) {
        Integer credit = jdbc.queryForObject(
                "SELECT credit_score FROM dorm WHERE id = ?", Integer.class, dormId);
        return credit == null ? 100 : credit;
    }

    private Course courseFor(String alertType) {
        return switch (alertType) {
            case "overload" -> courses.get("L-PWR-01");
            case "leakage", "temp" -> courses.get("L-SAFE-01");
            default -> courses.get("L-RES-01");
        };
    }

    private void initCourses() {
        courses.put("L-RES-01", new Course("L-RES-01", "阻性负载：为什么热得快容易被识别", "resistive",
                List.of(
                        new Section("规定怎么说", "宿舍内不得使用热得快、电炉等大功率阻性电器；首次违规可通过安全学习提前恢复用电。"),
                        new Section("为什么", "阻性负载功率因数接近 1，通电后功率会快速阶跃；这类设备发热集中，容易引发线路过载。"),
                        new Section("现在怎么做", "拔掉设备并观察功率回落；如为误判可申诉；首次违规完成 5 题测验（≥80 分）可恢复。")
                ),
                List.of(
                        new Question("下列哪类电器最容易被判为阻性负载？",
                                List.of("热得快", "吹风机", "饮水机", "台灯"), 0, "热得快功率因数高、阶跃明显。"),
                        new Question("阻性负载的功率因数通常具有什么特征？",
                                List.of("接近 0", "接近 1", "等于 0.8", "没有规律"), 1, "阻性负载 PF 接近 1。"),
                        new Question("为什么不能只看功率大小判断违规？",
                                List.of("合规电器也可能功率较高", "功率越高越安全", "只看功率就足够", "功率与判定无关"), 0, "合规电器也可能高功率，需要组合判据。"),
                        new Question("发现疑似违规电器后，第一步应该怎么做？",
                                List.of("继续使用", "直接申诉", "拔掉设备并观察功率", "关闭手机"), 2, "撤除设备后观察功率是否回落。"),
                        new Question("首次违规完成安全学习后，通常可以获得什么？",
                                List.of("免于任何记录", "提前恢复用电", "增加信用分到 120", "自动解除所有告警"), 1, "首次学习通过可提前恢复用电并 +3 信用。")
                )));
        courses.put("L-PWR-01", new Course("L-PWR-01", "总功率：错峰使用与回路容量", "overload",
                List.of(
                        new Section("规定怎么说", "宿舍总功率按分时政策管理；洗漱时段与非洗漱时段限额不同，超限先提醒。"),
                        new Section("为什么", "多个小功率电器同时使用也可能超过回路容量，导致线路发热。"),
                        new Section("现在怎么做", "错峰使用大功率电器；发现插座发热、焦味立即停用并报修。")
                ),
                List.of(
                        new Question("宿舍总功率超限最直接的后果是什么？",
                                List.of("线路过载发热", "电费降低", "电压升高", "没有影响"), 0, "过载会导致线路发热。"),
                        new Question("多个小功率电器同时使用是否可能超限？",
                                List.of("可能", "不会", "一定不会", "只看单个电器即可"), 0, "累加后可能超过限额。"),
                        new Question("吹风机在洗漱时段使用，系统通常会怎样？",
                                List.of("按白名单/总功率规则处理", "一定分闸", "完全免费", "没有任何限制"), 0, "洗漱时段按白名单与总功率规则处理。"),
                        new Question("夜间总功率提醒线通常比日间更严格，原因是？",
                                List.of("夜间人员反应更慢、风险更高", "夜间电费更高", "夜间电压更低", "夜间设备更少"), 0, "夜间发现和处置更慢，风险更高。"),
                        new Question("发现总功率接近上限时，最合理的做法是？",
                                List.of("错峰使用大功率电器", "同时开启更多设备", "拔掉漏电保护器", "忽略提醒"), 0, "错峰可以降低瞬时总功率。")
                )));
        courses.put("L-SAFE-01", new Course("L-SAFE-01", "漏电与温度：先停用，再报修", "safety",
                List.of(
                        new Section("规定怎么说", "发现漏电、插座发热、焦味等隐患应立即停用并报修，不得自行拆修。"),
                        new Section("为什么", "漏电和高温可能引发电击或电气火灾，必须优先保证人身安全。"),
                        new Section("现在怎么做", "停止使用相关插座；不要自行拆修；等待管理员或维修处理。")
                ),
                List.of(
                        new Question("漏电保护器的作用是什么？",
                                List.of("检测剩余电流并切断电源", "提高电压", "节省电费", "调节温度"), 0, "漏保检测剩余电流并动作。"),
                        new Question("插座发热到多少度应引起高度警惕？",
                                List.of("30℃", "50℃", "70℃ 以上", "100℃ 以下都正常"), 2, "70℃ 以上应高度警惕。"),
                        new Question("发现插座发烫、焦味时，首先应该？",
                                List.of("继续使用", "浇水降温", "停止使用并报修", "自行拆开"), 2, "先停用并报修。"),
                        new Question("漏电 500mA 属于什么等级？",
                                List.of("提示", "正常", "紧急", "无关"), 2, "500mA 属于 urgent。"),
                        new Question("为什么不能私自更换大功率保险丝？",
                                List.of("会失去过载保护", "会更安全", "会更省电", "没有影响"), 0, "擅自增大保险丝会失去保护。")
                )));
    }
}
