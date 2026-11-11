package com.ansu.common;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.MDC;

public record Result<T>(int code, String message, T data, String requestId, Meta meta) {

    public record Meta(boolean degraded, String genBy, String serverTime) {
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data, currentRequestId(), new Meta(false, null, now()));
    }

    public static <T> Result<T> degraded(T data, String genBy) {
        return new Result<>(0, "ok", data, currentRequestId(), new Meta(true, genBy, now()));
    }

    public static <T> Result<T> error(int code, String message, T detail) {
        return new Result<>(code, message, detail, currentRequestId(), new Meta(false, null, now()));
    }

    private static String currentRequestId() {
        String id = MDC.get("requestId");
        return id == null ? "" : id;
    }

    private static String now() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
