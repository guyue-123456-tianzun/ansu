package com.ansu.common;

public enum ErrorCode {
    OK(0, "ok"),
    PARAM_ERROR(40001, "参数错误"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40301, "无权访问"),
    NOT_FOUND(40401, "资源不存在"),
    CONFLICT(40901, "幂等冲突"),
    BUSINESS_REJECTED(42201, "业务不可执行"),
    RATE_LIMITED(42901, "请求过于频繁"),
    DEGRADED(50301, "已降级"),
    DATA_NOT_READY(50302, "数据未就绪"),
    INTERNAL_ERROR(50001, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
