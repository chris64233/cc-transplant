package com.chris64233.cc.transplant.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.transplant.domain.Candidate;
import com.chris64233.cc.transplant.domain.Donor;
import com.chris64233.cc.transplant.dto.CandidateResponse;
import com.chris64233.cc.transplant.dto.DonorResponse;
import com.chris64233.cc.transplant.dto.RegisterCandidateRequest;
import com.chris64233.cc.transplant.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.error.BusinessException;
import com.chris64233.cc.transplant.error.ErrorCode;
import com.chris64233.cc.transplant.repo.CandidateRepository;
import com.chris64233.cc.transplant.repo.DonorRepository;

/**
 * 供体与候选人登记。外部编号唯一，完整性由 Bean Validation + 数据库唯一约束双重保证。
 */
@Service
public class RegistrationService {

    private final DonorRepository donorRepository;
    private final CandidateRepository candidateRepository;
    private final Clock clock;

    public RegistrationService(DonorRepository donorRepository, CandidateRepository candidateRepository,
                               Clock clock) {
        this.donorRepository = donorRepository;
        this.candidateRepository = candidateRepository;
        this.clock = clock;
    }

    @Transactional
    public DonorResponse registerDonor(RegisterDonorRequest request) {
        if (donorRepository.existsByExternalRef(request.externalRef())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                    "供体外部编号已存在: " + request.externalRef());
        }
        Donor donor = new Donor(request.externalRef(), request.organType(), request.bloodType(),
                request.centerCode(), Instant.now(clock));
        try {
            donorRepository.saveAndFlush(donor);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                    "供体外部编号已存在: " + request.externalRef());
        }
        return toDonorResponse(donor);
    }

    @Transactional
    public CandidateResponse registerCandidate(RegisterCandidateRequest request) {
        if (candidateRepository.existsByExternalRef(request.externalRef())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                    "候选人外部编号已存在: " + request.externalRef());
        }
        Candidate candidate = new Candidate(request.externalRef(), request.organType(), request.bloodType(),
                request.urgency(), request.waitlistedAt(), request.active(), request.centerCode());
        try {
            candidateRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                    "候选人外部编号已存在: " + request.externalRef());
        }
        return toCandidateResponse(candidate);
    }

    static DonorResponse toDonorResponse(Donor donor) {
        return new DonorResponse(donor.getId(), donor.getExternalRef(), donor.getOrganType(),
                donor.getBloodType(), donor.getCenterCode(), donor.getRegisteredAt());
    }

    static CandidateResponse toCandidateResponse(Candidate candidate) {
        return new CandidateResponse(candidate.getId(), candidate.getExternalRef(), candidate.getOrganType(),
                candidate.getBloodType(), candidate.getUrgency(), candidate.getWaitlistedAt(),
                candidate.isActive(), candidate.getCenterCode());
    }
}
