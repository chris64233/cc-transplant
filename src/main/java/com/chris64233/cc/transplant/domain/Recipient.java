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
 * 候选受者。外部编号唯一；器官类型与血型用于供体匹配。
 */
@Entity
@Table(name = "recipient", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_recipient_external_id", columnNames = "external_id")
})
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "recipient_name", nullable = false, length = 128)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "organ_type", nullable = false, length = 16)
    private OrganType organType;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false, length = 8)
    private BloodType bloodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 16)
    private Urgency urgency;

    /** 进入等待名单的时间，越早优先级越高。 */
    @Column(name = "waitlisted_at", nullable = false)
    private Instant waitlistedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "center", nullable = false, length = 128)
    private String center;

    protected Recipient() {
    }

    public Recipient(String externalId, String recipientName, OrganType organType, BloodType bloodType,
                     Urgency urgency, Instant waitlistedAt, boolean active, String center) {
        this.externalId = externalId;
        this.recipientName = recipientName;
        this.organType = organType;
        this.bloodType = bloodType;
        this.urgency = urgency;
        this.waitlistedAt = waitlistedAt;
        this.active = active;
        this.center = center;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public OrganType getOrganType() {
        return organType;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public Instant getWaitlistedAt() {
        return waitlistedAt;
    }

    public boolean isActive() {
        return active;
    }

    /** 停用候选人：停用后即便手中有有效期内邀约也不能接受。 */
    public void deactivate() {
        this.active = false;
    }

    public String getCenter() {
        return center;
    }
}
