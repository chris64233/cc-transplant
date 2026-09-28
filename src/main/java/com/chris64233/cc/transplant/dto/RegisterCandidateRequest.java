package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 登记候选受者请求。
 *
 * @param urgency      紧急等级，1（最低）~ 5（最高）
 * @param waitlistedAt 进入等待名单时间
 * @param active       是否激活
 */
public record RegisterCandidateRequest(
        @NotBlank @Size(max = 64) String externalRef,
        @NotNull OrganType organType,
        @NotNull BloodType bloodType,
        @Min(1) @Max(5) @NotNull Integer urgency,
        @NotNull Instant waitlistedAt,
        @NotNull Boolean active,
        @NotBlank @Size(max = 32) String centerCode) {
}
