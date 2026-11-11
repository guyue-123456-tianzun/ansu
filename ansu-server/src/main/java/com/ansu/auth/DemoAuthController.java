package com.ansu.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ansu.common.ErrorCode;
import com.ansu.common.Result;

@RestController
@RequestMapping("/api/auth")
public class DemoAuthController {

    private static final Set<String> ROLES = Set.of(
            "student", "manager", "counselor", "repair", "admin", "guest");

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;

    public DemoAuthController(JdbcTemplate jdbc, JwtService jwtService) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
    }

    public record DemoLoginRequest(String role, Long dormId) {
    }

    @PostMapping("/demo")
    public ResponseEntity<Result<Map<String, Object>>> demo(@RequestBody DemoLoginRequest request) {
        String role = request.role();
        if (role == null || !ROLES.contains(role)) {
            return ResponseEntity.badRequest().body(
                    Result.error(ErrorCode.PARAM_ERROR.code(), "role 不合法", null));
        }
        if ("student".equals(role) && request.dormId() == null) {
            return ResponseEntity.badRequest().body(
                    Result.error(ErrorCode.PARAM_ERROR.code(), "学生演示身份需要 dormId", null));
        }

        String sql;
        Object[] args;
        if (request.dormId() == null) {
            sql = "SELECT id, role, display_name, dorm_id FROM user_account "
                    + "WHERE role = ? AND status = 'active' AND dorm_id IS NULL ORDER BY id LIMIT 1";
            args = new Object[]{role};
        } else {
            sql = "SELECT id, role, display_name, dorm_id FROM user_account "
                    + "WHERE role = ? AND status = 'active' AND dorm_id = ? ORDER BY id LIMIT 1";
            args = new Object[]{role, request.dormId()};
        }

        List<UserPrincipal> users = jdbc.query(sql, (rs, rowNum) -> new UserPrincipal(
                rs.getLong("id"),
                rs.getString("role"),
                rs.getObject("dorm_id", Long.class),
                rs.getString("display_name")), args);

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Result.error(ErrorCode.NOT_FOUND.code(), "演示身份不存在", null));
        }

        UserPrincipal user = users.get(0);
        String token = jwtService.issue(user);

        Map<String, Object> userView = new LinkedHashMap<>();
        userView.put("id", user.id());
        userView.put("role", user.role());
        userView.put("display_name", user.displayName());
        userView.put("dorm_id", user.dormId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("expires_in", jwtService.expireSeconds());
        data.put("user", userView);

        return ResponseEntity.ok(Result.ok(data));
    }
}
