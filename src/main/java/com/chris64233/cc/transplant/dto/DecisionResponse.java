package com.chris64233.cc.transplant.dto;

import com.chris64233.cc.transplant.domain.OfferDecision;
import com.chris64233.cc.transplant.domain.OfferStatus;
import java.time.Instant;

/**
 * 一次幂等决定事件的处理结果。
 */
public record DecisionResponse(
        String eventId,
        String allocationNo,
        String offerNo,
        OfferDecision decision,
        OfferStatus resultStatus,
        boolean replayed,
        Instant processedAt,
        /** 处理该决定后是否已自动向下一位候选人发出新邀约。 */
        String nextOfferNo) {
}
