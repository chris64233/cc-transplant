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
 * 向某位快照候选人发出的限时邀约。同一分配一次至多一条 PENDING 邀约。
 */
@Entity
@Table(name = "offer", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_no", columnNames = "offer_no"),
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_allocation_position",
                columnNames = {"allocation_id", "position"})
}, indexes = {
        @jakarta.persistence.Index(name = "idx_offer_allocation", columnList = "allocation_id,sequence_no")
})
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_no", nullable = false, length = 40)
    private String offerNo;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    /** 该分配内的邀约序号，从 1 开始，严格按候选顺序递增，不跳号。 */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "candidate_ref", nullable = false, length = 64)
    private String candidateRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OfferStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Offer() {
    }

    public Offer(String offerNo, Long allocationId, int sequenceNo, int position, String candidateRef,
                 OfferStatus status, Instant issuedAt, Instant expiresAt) {
        this.offerNo = offerNo;
        this.allocationId = allocationId;
        this.sequenceNo = sequenceNo;
        this.position = position;
        this.candidateRef = candidateRef;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getOfferNo() {
        return offerNo;
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public int getPosition() {
        return position;
    }

    public String getCandidateRef() {
        return candidateRef;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
