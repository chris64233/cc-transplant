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
import jakarta.persistence.Version;

/**
 * 一次器官分配流程。所有决定操作都以该行的悲观写锁串行化。
 */
@Entity
@Table(name = "allocation", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_allocation_no", columnNames = "allocation_no"),
        @jakarta.persistence.UniqueConstraint(name = "uk_allocation_donor", columnNames = "donor_id")
})
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分配单号，对外暴露，唯一。 */
    @Column(name = "allocation_no", nullable = false, length = 40)
    private String allocationNo;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(name = "donor_ref", nullable = false, length = 64)
    private String donorRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "organ_type", nullable = false, length = 16)
    private OrganType organType;

    @Enumerated(EnumType.STRING)
    @Column(name = "donor_blood_type", nullable = false, length = 8)
    private BloodType donorBloodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AllocationStatus status;

    /** 该分配每个邀约的有效期秒数，启动时固定，后续邀约沿用。 */
    @Column(name = "offer_ttl_seconds", nullable = false)
    private long offerTtlSeconds;

    /** 当前持有效邀约的快照位置（0 起），无有效邀约时为 null。 */
    @Column(name = "current_position")
    private Integer currentPosition;

    /** 最终受者快照位置。 */
    @Column(name = "recipient_position")
    private Integer recipientPosition;

    @Column(name = "recipient_ref", length = 64)
    private String recipientRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Allocation() {
    }

    public Allocation(String allocationNo, Long donorId, String donorRef, OrganType organType,
                      BloodType donorBloodType, AllocationStatus status, long offerTtlSeconds,
                      Instant createdAt) {
        this.allocationNo = allocationNo;
        this.donorId = donorId;
        this.donorRef = donorRef;
        this.organType = organType;
        this.donorBloodType = donorBloodType;
        this.status = status;
        this.offerTtlSeconds = offerTtlSeconds;
        this.createdAt = createdAt;
    }

    public long getOfferTtlSeconds() {
        return offerTtlSeconds;
    }

    public Long getId() {
        return id;
    }

    public String getAllocationNo() {
        return allocationNo;
    }

    public Long getDonorId() {
        return donorId;
    }

    public String getDonorRef() {
        return donorRef;
    }

    public OrganType getOrganType() {
        return organType;
    }

    public BloodType getDonorBloodType() {
        return donorBloodType;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public void setStatus(AllocationStatus status) {
        this.status = status;
    }

    public Integer getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(Integer currentPosition) {
        this.currentPosition = currentPosition;
    }

    public Integer getRecipientPosition() {
        return recipientPosition;
    }

    public String getRecipientRef() {
        return recipientRef;
    }

    public void markRecipient(int position, String recipientRef, Instant finishedAt) {
        this.recipientPosition = position;
        this.recipientRef = recipientRef;
        this.status = AllocationStatus.ALLOCATED;
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void markFinished(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
