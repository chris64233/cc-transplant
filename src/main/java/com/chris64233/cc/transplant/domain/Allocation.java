package com.chris64233.cc.transplant.domain;

import java.time.Instant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 一次器官分配流程。创建时固化不可变候选快照，之后候选人的任何变更都不影响本次分配。
 */
@Entity
@Table(name = "allocation", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_allocation_donor", columnNames = "donor_id"),
        @jakarta.persistence.UniqueConstraint(name = "uk_allocation_no", columnNames = "allocation_no")
})
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外暴露的分配编号。 */
    @Column(name = "allocation_no", nullable = false, length = 64)
    private String allocationNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AllocationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 每份邀约的有效时长（秒），启动分配时确定，对本次流程内所有邀约一致。 */
    @Column(name = "offer_ttl_seconds", nullable = false)
    private long offerTtlSeconds;

    /** 最终接受的候选人（快照条目），终态 ALLOCATED 时非空。 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_entry_id")
    private AllocationEntry acceptedEntry;

    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private java.util.List<AllocationEntry> entries = new java.util.ArrayList<>();

    protected Allocation() {
    }

    public Allocation(String allocationNo, Donor donor, Instant createdAt, long offerTtlSeconds) {
        this.allocationNo = allocationNo;
        this.donor = donor;
        this.status = AllocationStatus.IN_PROGRESS;
        this.createdAt = createdAt;
        this.offerTtlSeconds = offerTtlSeconds;
    }

    public void addEntry(AllocationEntry entry) {
        this.entries.add(entry);
    }

    public void markAllocated(AllocationEntry entry) {
        this.status = AllocationStatus.ALLOCATED;
        this.acceptedEntry = entry;
    }

    public void markExhausted() {
        this.status = AllocationStatus.EXHAUSTED;
    }

    public Long getId() {
        return id;
    }

    public String getAllocationNo() {
        return allocationNo;
    }

    public Donor getDonor() {
        return donor;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getOfferTtlSeconds() {
        return offerTtlSeconds;
    }

    public AllocationEntry getAcceptedEntry() {
        return acceptedEntry;
    }

    public java.util.List<AllocationEntry> getEntries() {
        return entries;
    }
}
