package com.chris64233.cc.transplant.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 器官供体。外部编号在系统内唯一。
 */
@Entity
@Table(name = "donor", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_donor_external_ref", columnNames = "external_ref")
}, indexes = {
        @Index(name = "idx_donor_organ_blood", columnList = "organ_type,blood_type")
})
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部系统编号，唯一。 */
    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "organ_type", nullable = false, length = 16)
    private OrganType organType;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false, length = 8)
    private BloodType bloodType;

    @Column(name = "center_code", nullable = false, length = 32)
    private String centerCode;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Donor() {
    }

    public Donor(String externalRef, OrganType organType, BloodType bloodType, String centerCode,
                 Instant registeredAt) {
        this.externalRef = externalRef;
        this.organType = organType;
        this.bloodType = bloodType;
        this.centerCode = centerCode;
        this.registeredAt = registeredAt;
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

    public String getCenterCode() {
        return centerCode;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
