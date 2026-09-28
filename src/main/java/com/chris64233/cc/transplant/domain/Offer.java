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
 * 限时邀约。一个分配流程任意时刻最多存在一份 PENDING 邀约；
 * 仅当当前邀约被拒绝或过期后，才能为下一位候选人发出新邀约。
 */
@Entity
@Table(name = "offer", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_allocation_entry",
                columnNames = {"allocation_id", "entry_id"}),
        @jakarta.persistence.UniqueConstraint(name = "uk_offer_no", columnNames = "offer_no")
})
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_no", nullable = false, length = 64)
    private String offerNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false)
    private Allocation allocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private AllocationEntry entry;

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

    public Offer(String offerNo, Allocation allocation, AllocationEntry entry,
                 Instant issuedAt, Instant expiresAt) {
        this.offerNo = offerNo;
        this.allocation = allocation;
        this.entry = entry;
        this.status = OfferStatus.PENDING;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void decide(OfferStatus outcome, Instant decidedAt) {
        this.status = outcome;
        this.decidedAt = decidedAt;
    }

    public boolean isPending() {
        return status == OfferStatus.PENDING;
    }

    /** 是否在给定时刻仍处于有效期内（不含到期时刻本身）。 */
    public boolean isValidAt(Instant now) {
        return status == OfferStatus.PENDING && now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getOfferNo() {
        return offerNo;
    }

    public Allocation getAllocation() {
        return allocation;
    }

    public AllocationEntry getEntry() {
        return entry;
    }

    public OfferStatus getStatus() {
        return status;
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
}
