package com.chris64233.cc.transplant.dto;

import com.chris64233.cc.transplant.domain.OfferDecision;
import com.chris64233.cc.transplant.domain.OfferStatus;
import java.time.Instant;

/**
 * 决定事件记录（审计/查询用）。
 */
public record OfferEventResponse(
        String eventId,
        String offerNo,
        OfferDecision decision,
        OfferStatus resultStatus,
        Instant processedAt) {
}
