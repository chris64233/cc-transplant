package com.chris64233.cc.transplant.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 启动分配请求。offerTtlSeconds 为每份邀约的有效时长（秒）。
 */
public record StartAllocationRequest(
        @NotBlank @Size(max = 64) String donorExternalId,
        @NotNull @Min(value = 1, message = "邀约有效期至少为 1 秒") Long offerTtlSeconds) {
}
