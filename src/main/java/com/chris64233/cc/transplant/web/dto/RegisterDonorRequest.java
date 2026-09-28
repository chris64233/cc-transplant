package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record RegisterDonorRequest(
        @NotBlank @Size(max = 64) String externalId,
        @NotBlank @Size(max = 128) String donorName,
        @NotNull OrganType organType,
        @NotNull BloodType bloodType,
        /** 可选，缺省取服务端当前时间。 */
        @PastOrPresent Instant registeredAt) {
}
