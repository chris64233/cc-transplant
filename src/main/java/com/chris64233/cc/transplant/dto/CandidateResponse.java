package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

public record CandidateResponse(
        Long id,
        String externalRef,
        OrganType organType,
        BloodType bloodType,
        int urgency,
        Instant waitlistedAt,
        boolean active,
        String centerCode) {
}
