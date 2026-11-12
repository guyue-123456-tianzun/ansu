package com.ansu.appeal;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.Result;

@RestController
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/api/student/appeals")
    public Result<Map<String, Object>> submit(@RequestBody AppealService.SubmitRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        return Result.ok(appealService.submit(principal, request));
    }

    @GetMapping("/api/student/appeals")
    public Result<List<Map<String, Object>>> listStudent(@AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        return Result.ok(appealService.listStudent(principal.dormId()));
    }

    @GetMapping("/api/manager/appeals")
    public Result<List<Map<String, Object>>> listManager(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireManager(principal);
        return Result.ok(appealService.listManager(status));
    }

    @PostMapping("/api/manager/appeals/{id}/review")
    public Result<Map<String, Object>> review(@PathVariable long id,
                                              @RequestBody AppealService.ReviewRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        requireManager(principal);
        return Result.ok(appealService.review(principal, id, request));
    }

    private void requireStudent(UserPrincipal principal) {
        if (principal == null || !"student".equals(principal.role()) || principal.dormId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要学生身份");
        }
    }

    private void requireManager(UserPrincipal principal) {
        if (principal == null || !List.of("manager", "admin").contains(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要宿管或管理员角色");
        }
    }
}
