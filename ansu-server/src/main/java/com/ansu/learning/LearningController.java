package com.ansu.learning;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.Result;

@RestController
@RequestMapping("/api/student/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/task")
    public Result<Map<String, Object>> task(@RequestParam("alert_id") long alertId,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        return Result.ok(learningService.task(principal, alertId));
    }

    @PostMapping("/complete")
    public Result<Map<String, Object>> complete(@RequestBody LearningService.CompleteRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        return Result.ok(learningService.complete(principal, request));
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(@AuthenticationPrincipal UserPrincipal principal) {
        requireStudent(principal);
        return Result.ok(learningService.records(principal.dormId()));
    }

    private void requireStudent(UserPrincipal principal) {
        if (principal == null || !"student".equals(principal.role()) || principal.dormId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要学生身份");
        }
    }
}
