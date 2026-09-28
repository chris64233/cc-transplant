package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.Donor;
import com.chris64233.cc.transplant.domain.OrganType;

public record DonorResponse(
        Long id,
        String externalId,
        String donorName,
        OrganType organType,
        BloodType bloodType,
        Instant registeredAt) {

    public static DonorResponse from(Donor donor) {
        return new DonorResponse(donor.getId(), donor.getExternalId(), donor.getDonorName(),
                donor.getOrganType(), donor.getBloodType(), donor.getRegisteredAt());
    }
}
