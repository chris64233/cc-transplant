package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.OfferStatus;

/**
 * 邀约信息。
 */
public record OfferResponse(
        String offerNo,
        int sequenceNo,
        int position,
        String candidateRef,
        OfferStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant decidedAt) {
}
