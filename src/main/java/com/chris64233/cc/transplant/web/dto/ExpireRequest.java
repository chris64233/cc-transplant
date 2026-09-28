package com.chris64233.cc.transplant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 过期处理请求。eventId 为客户端幂等标识，重复重放返回首次结果。
 */
public record ExpireRequest(
        @NotBlank @Size(max = 64) String eventId) {
}
