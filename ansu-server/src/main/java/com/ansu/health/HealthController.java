package com.ansu.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ansu.common.Result;
import com.ansu.safety.ThresholdService;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final ThresholdService thresholdService;
    private final boolean demoEnabled;
    private final long demoSeed;

    public HealthController(JdbcTemplate jdbc,
                            ThresholdService thresholdService,
                            @Value("${ansu.demo.enabled:true}") boolean demoEnabled,
                            @Value("${ansu.demo.seed:20261110}") long demoSeed) {
        this.jdbc = jdbc;
        this.thresholdService = thresholdService;
        this.demoEnabled = demoEnabled;
        this.demoSeed = demoSeed;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        String db = "ok";
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            db = "down";
        }

        Map<String, Object> threshold = new LinkedHashMap<>();
        threshold.put("version", thresholdService.version());
        threshold.put("active", true);

        Map<String, Object> demo = new LinkedHashMap<>();
        demo.put("enabled", demoEnabled);
        demo.put("seed", demoSeed);

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("db", db);
        components.put("python", "not_checked");
        components.put("threshold", threshold);
        components.put("demo", demo);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok".equals(db) ? "ok" : "degraded");
        data.put("components", components);
        return Result.ok(data);
    }
}
