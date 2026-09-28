package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.Urgency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record RegisterRecipientRequest(
        @NotBlank @Size(max = 64) String externalId,
        @NotBlank @Size(max = 128) String recipientName,
        @NotNull OrganType organType,
        @NotNull BloodType bloodType,
        @NotNull Urgency urgency,
        @NotNull @PastOrPresent Instant waitlistedAt,
        @NotNull Boolean active,
        @NotBlank @Size(max = 128) String center) {
}
