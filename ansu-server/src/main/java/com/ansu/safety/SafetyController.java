package com.ansu.safety;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ansu.common.Result;

@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    private final ThresholdService thresholdService;

    public SafetyController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    @GetMapping("/thresholds")
    public Result<Map<String, Object>> thresholds() {
        return Result.ok(thresholdService.asMap());
    }
}
