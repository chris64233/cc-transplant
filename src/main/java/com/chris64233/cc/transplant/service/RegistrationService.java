package com.chris64233.cc.transplant.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.Donor;
import com.chris64233.cc.transplant.domain.DonorRepository;
import com.chris64233.cc.transplant.domain.Recipient;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.web.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.web.dto.RegisterRecipientRequest;

/**
 * 供体器官与候选受者登记。外部编号唯一由数据库唯一约束兜底。
 */
@Service
public class RegistrationService {

    private final DonorRepository donorRepository;
    private final RecipientRepository recipientRepository;
    private final Clock clock;

    public RegistrationService(DonorRepository donorRepository,
                               RecipientRepository recipientRepository, Clock clock) {
        this.donorRepository = donorRepository;
        this.recipientRepository = recipientRepository;
        this.clock = clock;
    }

    @Transactional
    public Donor registerDonor(RegisterDonorRequest request) {
        if (donorRepository.existsByExternalId(request.externalId())) {
            throw BusinessException.conflict(ErrorCode.UNIQUE_CONSTRAINT,
                    "供体外部编号已存在: " + request.externalId());
        }
        Instant registeredAt = request.registeredAt() != null
                ? request.registeredAt() : Instant.now(clock);
        Donor donor = new Donor(request.externalId().trim(), request.donorName().trim(),
                request.organType(), request.bloodType(), registeredAt);
        try {
            return donorRepository.save(donor);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict(ErrorCode.UNIQUE_CONSTRAINT,
                    "供体外部编号已存在: " + request.externalId());
        }
    }

    @Transactional
    public Recipient registerRecipient(RegisterRecipientRequest request) {
        if (recipientRepository.existsByExternalId(request.externalId())) {
            throw BusinessException.conflict(ErrorCode.UNIQUE_CONSTRAINT,
                    "受者外部编号已存在: " + request.externalId());
        }
        Recipient recipient = new Recipient(request.externalId().trim(),
                request.recipientName().trim(), request.organType(), request.bloodType(),
                request.urgency(), request.waitlistedAt(), request.active(), request.center().trim());
        try {
            return recipientRepository.save(recipient);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict(ErrorCode.UNIQUE_CONSTRAINT,
                    "受者外部编号已存在: " + request.externalId());
        }
    }

    /** 血型相容性：返回可接受某供体血型的受者血型集合。 */
    static java.util.List<BloodType> compatibleRecipientBloodTypes(BloodType donor) {
        return java.util.Arrays.stream(BloodType.values())
                .filter(donor::compatibleWith)
                .toList();
    }

    /**
     * 候选排序：紧急等级权重降序 → 进入等待名单时间升序（越早越优先）→ 外部编号升序（稳定兜底）。
     */
    static int compareCandidate(Recipient a, Recipient b) {
        int byUrgency = Integer.compare(b.getUrgency().getWeight(), a.getUrgency().getWeight());
        if (byUrgency != 0) {
            return byUrgency;
        }
        int byWait = a.getWaitlistedAt().compareTo(b.getWaitlistedAt());
        if (byWait != 0) {
            return byWait;
        }
        return a.getExternalId().compareTo(b.getExternalId());
    }
}
