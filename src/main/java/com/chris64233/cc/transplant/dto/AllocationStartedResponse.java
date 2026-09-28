package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.AllocationStatus;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;

/**
 * 启动分配的返回：分配单信息及首个限时邀约。
 */
public record AllocationStartedResponse(
        String allocationNo,
        String donorRef,
        OrganType organType,
        BloodType donorBloodType,
        AllocationStatus status,
        Instant createdAt,
        int candidateCount,
        OfferResponse currentOffer) {
}
