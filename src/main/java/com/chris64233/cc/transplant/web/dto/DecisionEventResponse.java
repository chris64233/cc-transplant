package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.DecisionType;
import com.chris64233.cc.transplant.domain.OfferEvent;
import com.chris64233.cc.transplant.domain.OfferStatus;

/**
 * 一次邀约决定（接受/拒绝/过期）的记录。
 */
public record DecisionEventResponse(
        String eventId,
        String offerNo,
        DecisionType decision,
        OfferStatus resultStatus,
        Instant recordedAt) {

    public static DecisionEventResponse from(OfferEvent event) {
        return new DecisionEventResponse(event.getEventId(), event.getOfferNo(), event.getDecision(),
                event.getResultStatus(), event.getRecordedAt());
    }
}
