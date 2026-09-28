package com.chris64233.cc.transplant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邀约决定请求（接受/拒绝）。eventId 为客户端幂等标识。
 */
public record DecisionRequest(
        @NotBlank @Size(max = 64) String eventId) {
}
