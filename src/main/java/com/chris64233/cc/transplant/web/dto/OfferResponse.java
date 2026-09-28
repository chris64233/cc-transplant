package com.chris64233.cc.transplant.web.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferStatus;

public record OfferResponse(
        String offerNo,
        int position,
        String recipientExternalId,
        String recipientName,
        OfferStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant decidedAt) {

    public static OfferResponse from(Offer offer) {
        return new OfferResponse(offer.getOfferNo(), offer.getEntry().getPosition(),
                offer.getEntry().getRecipientExternalId(), offer.getEntry().getRecipientName(),
                offer.getStatus(), offer.getIssuedAt(), offer.getExpiresAt(), offer.getDecidedAt());
    }
}
