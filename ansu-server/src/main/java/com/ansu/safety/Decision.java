package com.ansu.safety;

import java.util.List;

public record Decision(
        boolean violation,
        String level,
        String channel,
        String action,
        String ruleRef,
        List<String> reasons,
        int warnSeconds,
        int thresholdW,
        int steadyW,
        int stepW,
        double pf,
        double durationS,
        String thresholdVersion
) {
    public static Decision none(String version) {
        return new Decision(false, "info", "none", "NONE", "none",
                List.of(), 0, 0, 0, 0, 0, 0, version);
    }
}
