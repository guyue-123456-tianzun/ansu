package com.ansu.safety;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SafetyEngine {

    private final ThresholdService thresholds;

    public SafetyEngine(ThresholdService thresholds) {
        this.thresholds = thresholds;
    }

    public Decision evaluate(int powerW, double pf, int stepW, double durationS,
                             int leakageMa, double tempC, LocalTime now) {
        List<String> reasons = new ArrayList<>();

        boolean night = !now.isBefore(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(6, 0));
        boolean wash = !now.isBefore(LocalTime.of(19, 0)) && now.isBefore(LocalTime.of(23, 0));

        int cutoff = night
                ? thresholds.intValue("resistive.night_cutoff_w", 400)
                : thresholds.intValue("resistive.cutoff_w", 500);
        double pfMin = thresholds.doubleValue("resistive.pf_min", 0.95);
        int stepMin = thresholds.intValue("resistive.step_w", 300);
        double durationMin = thresholds.doubleValue("resistive.duration_s", 3);
        int identify = thresholds.intValue("resistive.identify_w", 300);

        if (pf >= pfMin && stepW >= stepMin && durationS >= durationMin
                && powerW >= identify && powerW >= cutoff) {
            reasons.add("PF ≥ " + pfMin);
            reasons.add("阶跃 ≥ " + stepMin + "W");
            reasons.add("持续 ≥ " + durationMin + "s");
            reasons.add("功率 ≥ " + cutoff + "W");
            return new Decision(true, "warn", "resistive", "WARN_THEN_CUT",
                    "resistive_ge_cutoff_for_3s", reasons,
                    thresholds.intValue("resistive.warn_s", 5),
                    cutoff, powerW, stepW, pf, durationS, thresholds.version());
        }

        int limit = night
                ? thresholds.intValue("power_policy.night_limit_w", 400)
                : wash
                    ? thresholds.intValue("power_policy.wash_limit_w", 2000)
                    : thresholds.intValue("power_policy.non_wash_limit_w", 1500);

        if (powerW >= limit) {
            reasons.add("当前时段总功率 ≥ " + limit + "W");
            return new Decision(false, "warn", "overload", "NOTIFY", "power_policy",
                    reasons, 0, limit, powerW, stepW, pf, durationS, thresholds.version());
        }

        if (leakageMa >= thresholds.intValue("leakage.urgent_ma", 500)) {
            reasons.add("剩余电流 ≥ 500mA");
            return new Decision(false, "urgent", "leakage", "NOTIFY", "leakage_urgent",
                    reasons, 0, 500, powerW, stepW, pf, durationS, thresholds.version());
        }

        if (tempC >= thresholds.intValue("temp.urgent_c", 90)) {
            reasons.add("温度 ≥ 90℃");
            return new Decision(false, "urgent", "temp", "NOTIFY", "temp_urgent",
                    reasons, 0, 90, powerW, stepW, pf, durationS, thresholds.version());
        }

        return Decision.none(thresholds.version());
    }
}
