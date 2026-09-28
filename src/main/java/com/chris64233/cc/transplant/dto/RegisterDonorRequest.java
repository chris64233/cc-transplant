package com.chris64233.cc.transplant.dto;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 登记器官供体请求。
 */
public record RegisterDonorRequest(
        @NotBlank @Size(max = 64) String externalRef,
        @NotNull OrganType organType,
        @NotNull BloodType bloodType,
        @NotBlank @Size(max = 32) String centerCode) {
}
