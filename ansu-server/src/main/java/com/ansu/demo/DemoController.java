package com.ansu.demo;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.auth.UserPrincipal;
import com.ansu.common.Result;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    public record SwitchRequest(String scene, Long seed) {
    }

    public record ResetRequest(String confirm) {
    }

    @PostMapping("/switch")
    public Result<Map<String, Object>> switchScene(@RequestBody SwitchRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        requireManager(principal);
        String runId = demoService.switchScene(request.scene(), request.seed());
        return Result.ok(Map.of("run_id", runId, "scene", request.scene() == null ? "violation" : request.scene()));
    }

    @PostMapping("/inject")
    public Result<Map<String, Object>> inject(@RequestBody DemoService.InjectRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        requireManager(principal);
        try {
            return Result.ok(demoService.inject(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/reset")
    public Result<Map<String, Object>> reset(@RequestBody(required = false) ResetRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        requireManager(principal);
        if (request != null && request.confirm() != null && !"RESET-DEMO".equals(request.confirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirm 必须为 RESET-DEMO");
        }
        String runId = demoService.reset();
        return Result.ok(Map.of("run_id", runId, "status", "reset"));
    }

    private void requireManager(UserPrincipal principal) {
        if (principal == null || !List.of("manager", "admin").contains(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要宿管或管理员角色");
        }
    }
}
