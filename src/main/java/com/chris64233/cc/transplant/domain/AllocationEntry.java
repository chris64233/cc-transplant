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
 * 候选快照条目：分配启动那一刻的候选人状态，之后不可变。
 * position 从 1 开始，表示邀约发出顺序。
 */
@Entity
@Table(name = "allocation_entry", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_entry_allocation_position",
                columnNames = {"allocation_id", "position"})
})
public class AllocationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false)
    private Allocation allocation;

    @Column(name = "position", nullable = false)
    private int position;

    /** 冗余的受者主键，便于回查；快照字段均以本实体为准。 */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_external_id", nullable = false, length = 64)
    private String recipientExternalId;

    @Column(name = "recipient_name", nullable = false, length = 128)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 16)
    private Urgency urgency;

    @Column(name = "waitlisted_at", nullable = false)
    private Instant waitlistedAt;

    @Column(name = "active_at_snapshot", nullable = false)
    private boolean activeAtSnapshot;

    @Column(name = "center", nullable = false, length = 128)
    private String center;

    protected AllocationEntry() {
    }

    public AllocationEntry(Allocation allocation, int position, Recipient recipient) {
        this.allocation = allocation;
        this.position = position;
        this.recipientId = recipient.getId();
        this.recipientExternalId = recipient.getExternalId();
        this.recipientName = recipient.getRecipientName();
        this.urgency = recipient.getUrgency();
        this.waitlistedAt = recipient.getWaitlistedAt();
        this.activeAtSnapshot = recipient.isActive();
        this.center = recipient.getCenter();
    }

    public Long getId() {
        return id;
    }

    public Allocation getAllocation() {
        return allocation;
    }

    public int getPosition() {
        return position;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getRecipientExternalId() {
        return recipientExternalId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public Instant getWaitlistedAt() {
        return waitlistedAt;
    }

    public boolean isActiveAtSnapshot() {
        return activeAtSnapshot;
    }

    public String getCenter() {
        return center;
    }
}
