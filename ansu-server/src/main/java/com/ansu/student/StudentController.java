package com.ansu.student;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.Result;
import com.ansu.common.TimeUtil;
import com.ansu.dashboard.QueryService;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final QueryService queryService;

    public StudentController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        Map<String, Object> data = queryService.studentOverview(principal.dormId(), principal.id());
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "宿舍不存在");
        }
        return Result.ok(data);
    }

    public record RespondRequest(String action) {
    }

    @PostMapping("/alerts/{id}/respond")
    @Transactional
    public Result<Map<String, Object>> respond(@PathVariable long id,
                                               @RequestBody RespondRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        if (request == null || !"removed".equals(request.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 只支持 removed");
        }
        List<Map<String, Object>> alerts = queryService.rawAlert(id, principal.dormId());
        if (alerts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在或不属于本宿舍");
        }
        Map<String, Object> alert = alerts.get(0);
        long dormId = ((Number) alert.get("dorm_id")).longValue();
        String status = String.valueOf(alert.get("dorm_status"));
        LocalDateTime now = LocalDateTime.now(TimeUtil.ZONE);
        String runId = alert.get("demo_run_id") == null ? null : String.valueOf(alert.get("demo_run_id"));

        if ("locked".equals(status)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "锁定中不能自行恢复");
        }

        if ("warned".equals(status)) {
            queryService.updateDormNormal(dormId, now);
            queryService.resolveAlert(id, now);
            queryService.logAction(id, "student_removed", now, "remove:" + id);
        } else if ("cut_off".equals(status)) {
            queryService.forceLowPower(dormId, now, runId);
            queryService.logAction(id, "student_removed", now, "remove:" + id);
        } else {
            queryService.logAction(id, "student_removed_noop", now, "remove:" + id);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alert_id", id);
        data.put("dorm_status", "cut_off".equals(status) ? "recovering" : "normal");
        data.put("message", "cut_off".equals(status) ? "已撤除，30 秒内恢复用电" : "已撤除");
        return Result.ok(data);
    }

    @GetMapping("/alerts/{id}")
    public Result<Map<String, Object>> alertDetail(@PathVariable long id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        Map<String, Object> data = queryService.alertDetail(id, principal.dormId());
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在或不属于本宿舍");
        }
        return Result.ok(data);
    }

    private void requireStudent(UserPrincipal principal) {
        if (principal == null || !"student".equals(principal.role()) || principal.dormId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要学生身份");
        }
    }
}
