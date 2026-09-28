package com.chris64233.cc.transplant.web;

import java.time.Instant;
import java.util.List;

/**
 * 统一错误响应体。
 *
 * @param code    稳定错误码
 * @param message 人类可读错误信息
 * @param path    出错请求路径
 * @param details 字段级校验错误（仅校验失败时存在）
 */
public record ErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldErrorDetail> details) {

    public record FieldErrorDetail(String field, String message) {
    }
}
