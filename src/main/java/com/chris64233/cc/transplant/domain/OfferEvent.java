package com.chris64233.cc.transplant.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 幂等事件记录。同一个 eventId 只能处理一次：
 * 重放返回原结果；相同 eventId 但事件内容不同返回 409。
 */
@Entity
@Table(name = "offer_event", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_event_id", columnNames = "event_id")
}, indexes = {
        @jakarta.persistence.Index(name = "idx_event_allocation", columnList = "allocation_id")
})
public class OfferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 调用方提供的幂等标识，全局唯一。 */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    /** 事件目标邀约；冗余自首次处理，用于重放/冲突校验。 */
    @Column(name = "offer_no", nullable = false, length = 40)
    private String offerNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private OfferDecision decision;

    /** 决定结果状态：与邀约状态一致（ACCEPTED/DECLINED/EXPIRED）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 16)
    private OfferStatus resultStatus;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected OfferEvent() {
    }

    public OfferEvent(String eventId, Long allocationId, String offerNo, OfferDecision decision,
                      OfferStatus resultStatus, Instant processedAt) {
        this.eventId = eventId;
        this.allocationId = allocationId;
        this.offerNo = offerNo;
        this.decision = decision;
        this.resultStatus = resultStatus;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public String getOfferNo() {
        return offerNo;
    }

    public OfferDecision getDecision() {
        return decision;
    }

    public OfferStatus getResultStatus() {
        return resultStatus;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
