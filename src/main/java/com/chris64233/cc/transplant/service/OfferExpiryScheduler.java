package com.chris64233.cc.transplant.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferStatus;
import com.chris64233.cc.transplant.error.BusinessException;
import com.chris64233.cc.transplant.error.ErrorCode;
import com.chris64233.cc.transplant.repo.OfferRepository;

/**
 * 定时扫描已到过期时间的 PENDING 邀约并按过期处理。
 * 使用每邀约确定性的系统事件号，保证扫描与手工过期接口之间幂等。
 */
@Component
public class OfferExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryScheduler.class);

    private final OfferRepository offerRepository;
    private final AllocationService allocationService;
    private final Clock clock;

    public OfferExpiryScheduler(OfferRepository offerRepository, AllocationService allocationService,
                                Clock clock) {
        this.offerRepository = offerRepository;
        this.allocationService = allocationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.offer.expiry-sweep-delay-ms:10000}")
    public void sweepExpiredOffers() {
        Instant now = Instant.now(clock);
        List<Offer> expired = offerRepository
                .findByStatusAndExpiresAtLessThanEqual(OfferStatus.PENDING, now);
        for (Offer offer : expired) {
            try {
                allocationService.expire(offer.getOfferNo(),
                        AllocationService.systemExpiryEventId(offer.getOfferNo()));
            } catch (BusinessException e) {
                if (e.getCode() != ErrorCode.EVENT_CONFLICT && e.getCode() != ErrorCode.RULE_VIOLATION) {
                    throw e;
                }
                // 邀约在扫描期间已被并发处理：幂等跳过。
                log.debug("扫描跳过邀约 {}（{}: {}）", offer.getOfferNo(), e.getCode(), e.getMessage());
            }
        }
    }
}
