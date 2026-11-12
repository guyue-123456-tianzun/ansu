package com.ansu.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ansu.common.Result;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final QueryService queryService;

    public DashboardController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/buildings")
    public Result<List<Map<String, Object>>> buildings() {
        return Result.ok(List.of(Map.of(
                "id", 1,
                "code", "B1",
                "name", "1 号楼",
                "dorm_count", queryService.dorms().size()
        )));
    }

    @GetMapping("/dorms")
    public Result<List<Map<String, Object>>> dorms() {
        return Result.ok(queryService.dorms());
    }

    @GetMapping("/load")
    public Result<List<Map<String, Object>>> load(@RequestParam long dormId,
                                                  @RequestParam(defaultValue = "60") int minutes) {
        return Result.ok(queryService.load(dormId, minutes));
    }

    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(queryService.summary());
    }

    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(queryService.alerts(limit));
    }

    @GetMapping("/alerts/{id}")
    public Result<Map<String, Object>> alertDetail(@PathVariable long id) {
        Map<String, Object> data = queryService.alertDetail(id, null);
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在");
        }
        return Result.ok(data);
    }
}
