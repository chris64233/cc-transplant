package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

public record DonorResponse(
        Long id,
        String externalRef,
        OrganType organType,
        BloodType bloodType,
        String centerCode,
        Instant registeredAt) {
}
