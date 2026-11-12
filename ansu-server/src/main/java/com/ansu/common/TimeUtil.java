package com.ansu.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private TimeUtil() {
    }

    public static String iso(LocalDateTime ts) {
        return ts == null ? null : ts.atZone(ZONE).format(ISO);
    }

    public static String shortTime(LocalDateTime ts) {
        return ts == null ? "" : ts.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
