package com.chris64233.cc.transplant.web;

import java.time.Instant;
import java.util.List;

/**
 * 统一错误响应结构。
 *
 * @param timestamp 错误发生时间
 * @param status    HTTP 状态码
 * @param error     错误码（机器可读）
 * @param message   错误描述（人类可读）
 * @param path      请求路径
 * @param fieldErrors 字段级校验错误（仅参数校验场景）
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message, Object rejectedValue) {
    }
}
