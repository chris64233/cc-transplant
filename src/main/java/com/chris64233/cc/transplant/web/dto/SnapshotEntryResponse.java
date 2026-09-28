package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.AllocationEntry;
import com.chris64233.cc.transplant.domain.Urgency;

public record SnapshotEntryResponse(
        int position,
        String recipientExternalId,
        String recipientName,
        Urgency urgency,
        Instant waitlistedAt,
        boolean activeAtSnapshot,
        String center) {

    public static SnapshotEntryResponse from(AllocationEntry entry) {
        return new SnapshotEntryResponse(entry.getPosition(), entry.getRecipientExternalId(),
                entry.getRecipientName(), entry.getUrgency(), entry.getWaitlistedAt(),
                entry.isActiveAtSnapshot(), entry.getCenter());
    }
}
