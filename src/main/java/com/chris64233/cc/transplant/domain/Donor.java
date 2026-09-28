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
 * 供体器官。外部编号在全系统内唯一；同一供体器官最多进入一次分配流程。
 */
@Entity
@Table(name = "donor", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_donor_external_id", columnNames = "external_id")
})
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部系统编号，唯一。 */
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "donor_name", nullable = false, length = 128)
    private String donorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "organ_type", nullable = false, length = 16)
    private OrganType organType;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false, length = 8)
    private BloodType bloodType;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Donor() {
    }

    public Donor(String externalId, String donorName, OrganType organType, BloodType bloodType,
                 Instant registeredAt) {
        this.externalId = externalId;
        this.donorName = donorName;
        this.organType = organType;
        this.bloodType = bloodType;
        this.registeredAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDonorName() {
        return donorName;
    }

    public OrganType getOrganType() {
        return organType;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
