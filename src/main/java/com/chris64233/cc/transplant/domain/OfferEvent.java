package com.chris64233.cc.transplant.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 邀约决定事件。eventId 由客户端提供，全局唯一，用于接受/拒绝/过期的幂等处理：
 * 相同 eventId 重放返回原结果；相同 eventId 但内容冲突返回 409。
 */
@Entity
@Table(name = "offer_event", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_event_event_id", columnNames = "event_id")
})
public class OfferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户端提供的幂等标识，唯一。 */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "offer_no", nullable = false, length = 64)
    private String offerNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private DecisionType decision;

    /** 事件首次处理后邀约/分配的结果状态，用于幂等重放。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 16)
    private OfferStatus resultStatus;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    protected OfferEvent() {
    }

    public OfferEvent(String eventId, Offer offer, DecisionType decision, OfferStatus resultStatus,
                      Instant recordedAt) {
        this.eventId = eventId;
        this.offer = offer;
        this.offerNo = offer.getOfferNo();
        this.decision = decision;
        this.resultStatus = resultStatus;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOfferNo() {
        return offerNo;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public OfferStatus getResultStatus() {
        return resultStatus;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Offer getOffer() {
        return offer;
    }
}
