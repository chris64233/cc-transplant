package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.Recipient;
import com.chris64233.cc.transplant.domain.Urgency;

public record RecipientResponse(
        Long id,
        String externalId,
        String recipientName,
        OrganType organType,
        BloodType bloodType,
        Urgency urgency,
        Instant waitlistedAt,
        boolean active,
        String center) {

    public static RecipientResponse from(Recipient recipient) {
        return new RecipientResponse(recipient.getId(), recipient.getExternalId(),
                recipient.getRecipientName(), recipient.getOrganType(), recipient.getBloodType(),
                recipient.getUrgency(), recipient.getWaitlistedAt(), recipient.isActive(),
                recipient.getCenter());
    }
}
