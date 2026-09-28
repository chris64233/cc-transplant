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
 * 分配启动时生成的不可变候选快照行。排序结果固化为 position，后续不随候选人登记变化而变化。
 */
@Entity
@Table(name = "allocation_candidate", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_snapshot_position",
                columnNames = {"allocation_id", "position"}),
        @jakarta.persistence.UniqueConstraint(name = "uk_snapshot_candidate",
                columnNames = {"allocation_id", "candidate_id"})
})
public class AllocationCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    /** 在该次分配中的候选顺序，0 起，越小越优先。 */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "candidate_ref", nullable = false, length = 64)
    private String candidateRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false, length = 8)
    private BloodType bloodType;

    @Column(name = "urgency", nullable = false)
    private int urgency;

    @Column(name = "waitlisted_at", nullable = false)
    private Instant waitlistedAt;

    @Column(name = "center_code", nullable = false, length = 32)
    private String centerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private CandidateState state;

    protected AllocationCandidate() {
    }

    public AllocationCandidate(Long allocationId, int position, Candidate candidate) {
        this.allocationId = allocationId;
        this.position = position;
        this.candidateId = candidate.getId();
        this.candidateRef = candidate.getExternalRef();
        this.bloodType = candidate.getBloodType();
        this.urgency = candidate.getUrgency();
        this.waitlistedAt = candidate.getWaitlistedAt();
        this.centerCode = candidate.getCenterCode();
        this.state = CandidateState.WAITING;
    }

    public Long getId() {
        return id;
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public int getPosition() {
        return position;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public String getCandidateRef() {
        return candidateRef;
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

    public String getCenterCode() {
        return centerCode;
    }

    public CandidateState getState() {
        return state;
    }

    public void setState(CandidateState state) {
        this.state = state;
    }
}
