package com.chris64233.cc.transplant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启动分配请求。邀约有效期单位为秒，必须为正数。
 */
public record StartAllocationRequest(
        @NotBlank String donorExternalRef,
        @NotNull @Min(1) Long offerTtlSeconds) {
}
