package com.ansu.student;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.Result;
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
