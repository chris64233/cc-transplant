package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;
import java.util.List;

import com.chris64233.cc.transplant.domain.Allocation;
import com.chris64233.cc.transplant.domain.AllocationStatus;

/**
 * 分配详情：基本信息、不可变候选快照、当前有效邀约、每次决定、最终受者。
 */
public record AllocationDetailResponse(
        String allocationNo,
        DonorResponse donor,
        AllocationStatus status,
        Instant createdAt,
        long offerTtlSeconds,
        List<SnapshotEntryResponse> snapshot,
        OfferResponse currentOffer,
        List<DecisionEventResponse> decisions,
        String acceptedRecipientExternalId,
        String acceptedRecipientName) {

    public static AllocationDetailResponse of(Allocation allocation,
                                              List<SnapshotEntryResponse> snapshot,
                                              OfferResponse currentOffer,
                                              List<DecisionEventResponse> decisions) {
        String acceptedExternalId = null;
        String acceptedName = null;
        if (allocation.getAcceptedEntry() != null) {
            acceptedExternalId = allocation.getAcceptedEntry().getRecipientExternalId();
            acceptedName = allocation.getAcceptedEntry().getRecipientName();
        }
        return new AllocationDetailResponse(allocation.getAllocationNo(),
                DonorResponse.from(allocation.getDonor()), allocation.getStatus(),
                allocation.getCreatedAt(), allocation.getOfferTtlSeconds(), snapshot, currentOffer,
                decisions, acceptedExternalId, acceptedName);
    }
}
