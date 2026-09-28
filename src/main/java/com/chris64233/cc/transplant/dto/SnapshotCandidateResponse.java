package com.chris64233.cc.transplant.dto;

import java.time.Instant;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.CandidateState;

/**
 * 候选快照中的一个候选人。
 */
public record SnapshotCandidateResponse(
        int position,
        String candidateRef,
        BloodType bloodType,
        int urgency,
        Instant waitlistedAt,
        String centerCode,
        CandidateState state) {
}
