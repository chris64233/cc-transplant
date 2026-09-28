package com.chris64233.cc.transplant.dto;

import java.time.Instant;
import java.util.List;

import com.chris64233.cc.transplant.domain.AllocationStatus;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

/**
 * 分配详情：候选快照、当前邀约、每次邀约与决定、最终受者。
 */
public record AllocationDetailResponse(
        String allocationNo,
        String donorRef,
        OrganType organType,
        BloodType donorBloodType,
        AllocationStatus status,
        Instant createdAt,
        Instant finishedAt,
        List<SnapshotCandidateResponse> snapshot,
        OfferResponse currentOffer,
        List<OfferResponse> offers,
        List<OfferEventResponse> events,
        Integer recipientPosition,
        String recipientRef) {
}
