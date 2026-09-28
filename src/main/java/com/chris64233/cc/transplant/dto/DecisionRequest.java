package com.chris64233.cc.transplant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 对指定邀约作出决定的请求。eventId 为调用方提供的幂等标识。
 */
public record DecisionRequest(
        @NotBlank String offerNo,
        @NotBlank String eventId) {
}
