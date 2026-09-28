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
 * 候选受者。外部编号在系统内唯一。
 */
@Entity
@Table(name = "candidate", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_candidate_external_ref", columnNames = "external_ref")
}, indexes = {
        @jakarta.persistence.Index(name = "idx_candidate_match",
                columnList = "organ_type,blood_type,active,urgency,waitlisted_at")
})
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "organ_type", nullable = false, length = 16)
    private OrganType organType;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false, length = 8)
    private BloodType bloodType;

    /** 紧急等级，1（最低）~ 5（最高）。 */
    @Column(name = "urgency", nullable = false)
    private int urgency;

    @Column(name = "waitlisted_at", nullable = false)
    private Instant waitlistedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "center_code", nullable = false, length = 32)
    private String centerCode;

    protected Candidate() {
    }

    public Candidate(String externalRef, OrganType organType, BloodType bloodType, int urgency,
                     Instant waitlistedAt, boolean active, String centerCode) {
        this.externalRef = externalRef;
        this.organType = organType;
        this.bloodType = bloodType;
        this.urgency = urgency;
        this.waitlistedAt = waitlistedAt;
        this.active = active;
        this.centerCode = centerCode;
    }

    public Long getId() {
        return id;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public OrganType getOrganType() {
        return organType;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public int getUrgency() {
        return urgency;
    }

    public Instant getWaitlistedAt() {
        return waitlistedAt;
    }

    public boolean isActive() {
        return active;
    }

    public String getCenterCode() {
        return centerCode;
    }

    public void setUrgency(int urgency) {
        this.urgency = urgency;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCenterCode(String centerCode) {
        this.centerCode = centerCode;
    }
}
