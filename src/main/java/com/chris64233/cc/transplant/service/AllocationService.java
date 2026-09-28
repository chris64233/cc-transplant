package com.chris64233.cc.transplant.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.transplant.domain.Allocation;
import com.chris64233.cc.transplant.domain.AllocationCandidate;
import com.chris64233.cc.transplant.domain.AllocationStatus;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.Candidate;
import com.chris64233.cc.transplant.domain.CandidateState;
import com.chris64233.cc.transplant.domain.Donor;
import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferDecision;
import com.chris64233.cc.transplant.domain.OfferEvent;
import com.chris64233.cc.transplant.domain.OfferStatus;
import com.chris64233.cc.transplant.dto.AllocationDetailResponse;
import com.chris64233.cc.transplant.dto.AllocationStartedResponse;
import com.chris64233.cc.transplant.dto.DecisionResponse;
import com.chris64233.cc.transplant.dto.OfferEventResponse;
import com.chris64233.cc.transplant.dto.OfferResponse;
import com.chris64233.cc.transplant.dto.SnapshotCandidateResponse;
import com.chris64233.cc.transplant.error.BusinessException;
import com.chris64233.cc.transplant.error.ErrorCode;
import com.chris64233.cc.transplant.repo.AllocationCandidateRepository;
import com.chris64233.cc.transplant.repo.AllocationRepository;
import com.chris64233.cc.transplant.repo.CandidateRepository;
import com.chris64233.cc.transplant.repo.DonorRepository;
import com.chris64233.cc.transplant.repo.OfferEventRepository;
import com.chris64233.cc.transplant.repo.OfferRepository;

/**
 * 器官分配核心服务：候选快照、限时邀约流转、幂等决定与并发控制。
 *
 * <p>并发模型：所有决定操作首先以悲观写锁锁定 allocation 行，同一器官的
 * 接受/拒绝/过期因此完全串行，保证最多一个结果生效、邀约不跳号、无双重分配。</p>
 */
@Service
public class AllocationService {

    private static final DateTimeFormatter NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AllocationRepository allocationRepository;
    private final AllocationCandidateRepository snapshotRepository;
    private final OfferRepository offerRepository;
    private final OfferEventRepository eventRepository;
    private final CandidateRepository candidateRepository;
    private final DonorRepository donorRepository;
    private final Clock clock;

    public AllocationService(AllocationRepository allocationRepository,
                             AllocationCandidateRepository snapshotRepository,
                             OfferRepository offerRepository,
                             OfferEventRepository eventRepository,
                             CandidateRepository candidateRepository,
                             DonorRepository donorRepository,
                             Clock clock) {
        this.allocationRepository = allocationRepository;
        this.snapshotRepository = snapshotRepository;
        this.offerRepository = offerRepository;
        this.eventRepository = eventRepository;
        this.candidateRepository = candidateRepository;
        this.donorRepository = donorRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 启动分配
    // ------------------------------------------------------------------

    /**
     * 启动分配：过滤相容候选人、按规则排序生成不可变快照，并向首位候选人发出限时邀约。
     * 没有合格候选人时失败，不创建任何流程。
     */
    @Transactional
    public AllocationStartedResponse start(String donorExternalRef, long offerTtlSeconds) {
        Donor donor = donorRepository.findByExternalRef(donorExternalRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "供体不存在: " + donorExternalRef));

        if (allocationRepository.existsByDonorId(donor.getId())) {
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "该供体器官已存在分配流程: " + donorExternalRef);
        }

        List<BloodType> acceptable = compatibleRecipientBloodTypes(donor.getBloodType());
        List<Candidate> eligible = candidateRepository
                .findByOrganTypeAndActiveTrueAndBloodTypeIn(donor.getOrganType(), acceptable);
        eligible.sort(Comparator
                .comparingInt(Candidate::getUrgency).reversed()       // 紧急等级从高到低
                .thenComparing(Candidate::getWaitlistedAt)            // 等待时间从早到晚
                .thenComparing(Candidate::getExternalRef));           // 候选编号稳定排序

        if (eligible.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION,
                    "没有与供体器官类型和血型相容的激活候选人，无法启动分配");
        }

        Instant now = Instant.now(clock);
        Allocation allocation = new Allocation(newAllocationNo(now), donor.getId(), donor.getExternalRef(),
                donor.getOrganType(), donor.getBloodType(), AllocationStatus.IN_PROGRESS,
                offerTtlSeconds, now);
        try {
            allocationRepository.saveAndFlush(allocation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "该供体器官已存在分配流程: " + donorExternalRef);
        }

        for (int i = 0; i < eligible.size(); i++) {
            snapshotRepository.save(new AllocationCandidate(allocation.getId(), i, eligible.get(i)));
        }
        snapshotRepository.flush();

        Offer firstOffer = issueOffer(allocation, 0, now);
        allocation.setCurrentPosition(0);
        snapshotRepository.findByAllocationIdAndPosition(allocation.getId(), 0)
                .ifPresent(s -> s.setState(CandidateState.OFFERED));

        return new AllocationStartedResponse(allocation.getAllocationNo(), allocation.getDonorRef(),
                allocation.getOrganType(), allocation.getDonorBloodType(), allocation.getStatus(),
                allocation.getCreatedAt(), eligible.size(), toOfferResponse(firstOffer));
    }

    // ------------------------------------------------------------------
    // 决定：接受 / 拒绝 / 过期
    // ------------------------------------------------------------------

    @Transactional
    public DecisionResponse accept(String offerNo, String eventId) {
        return handleDecision(offerNo, eventId, OfferDecision.ACCEPT);
    }

    @Transactional
    public DecisionResponse decline(String offerNo, String eventId) {
        return handleDecision(offerNo, eventId, OfferDecision.DECLINE);
    }

    @Transactional
    public DecisionResponse expire(String offerNo, String eventId) {
        return handleDecision(offerNo, eventId, OfferDecision.EXPIRE);
    }

    /**
     * 系统自动过期使用的确定性幂等事件号，同一邀约多次扫描结果一致。
     */
    public static String systemExpiryEventId(String offerNo) {
        return "system-expire-" + offerNo;
    }

    private DecisionResponse handleDecision(String offerNo, String eventId, OfferDecision decision) {
        Offer offer = offerRepository.findByOfferNo(offerNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "邀约不存在: " + offerNo));

        // 1) 锁定分配行：同一分配的所有决定串行执行。
        Allocation allocation = allocationRepository.lockById(offer.getAllocationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分配流程不存在"));

        // 2) 幂等处理：相同事件重放返回原结果；内容冲突 409。
        OfferEvent existing = eventRepository.findByEventId(eventId).orElse(null);
        if (existing != null) {
            if (existing.getOfferNo().equals(offerNo) && existing.getDecision() == decision) {
                return new DecisionResponse(eventId, allocation.getAllocationNo(), offerNo, decision,
                        existing.getResultStatus(), true, existing.getProcessedAt(),
                        currentPendingOfferNo(allocation));
            }
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "幂等事件标识 " + eventId + " 已用于不同的决定内容");
        }

        // 3) 在锁内重新读取邀约最新状态并校验。
        Offer lockedOffer = offerRepository.lockByOfferNo(offerNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "邀约不存在: " + offerNo));
        Instant now = Instant.now(clock);

        if (lockedOffer.getStatus() != OfferStatus.PENDING) {
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "邀约已结束（" + lockedOffer.getStatus() + "），不能再次作出决定");
        }
        if (allocation.getStatus() != AllocationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "分配流程已结束（" + allocation.getStatus() + "），不能再作出决定");
        }

        OfferStatus resultStatus = switch (decision) {
            case ACCEPT -> validateAndAccept(allocation, lockedOffer, now);
            case DECLINE -> validateAndDecline(lockedOffer, now);
            case EXPIRE -> validateAndExpire(lockedOffer, now);
        };

        // 4) 状态推进与后续邀约。
        int position = lockedOffer.getPosition();
        AllocationCandidate snap = snapshotRepository
                .findByAllocationIdAndPosition(allocation.getId(), position)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选快照不存在"));
        Instant decidedAt = now;
        lockedOffer.setStatus(resultStatus);
        lockedOffer.setDecidedAt(decidedAt);

        String nextOfferNo = null;
        if (resultStatus == OfferStatus.ACCEPTED) {
            snap.setState(CandidateState.ACCEPTED);
            allocation.setCurrentPosition(null);
            allocation.markRecipient(position, lockedOffer.getCandidateRef(), decidedAt);
        } else {
            snap.setState(resultStatus == OfferStatus.DECLINED
                    ? CandidateState.DECLINED : CandidateState.EXPIRED);
            nextOfferNo = advanceToNextCandidate(allocation, position, now);
        }

        // 5) 写入幂等事件；唯一约束最终兜底。分配行锁已串行化同分配请求，
        //    能走到这里的约束冲突只可能是跨分配复用 eventId，直接按冲突处理
        //    （不在已标记失败的持久化上下文中继续读取）。
        OfferEvent event = new OfferEvent(eventId, allocation.getId(), offerNo, decision, resultStatus,
                decidedAt);
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EVENT_CONFLICT,
                    "幂等事件标识 " + eventId + " 已存在且内容冲突");
        }

        return new DecisionResponse(eventId, allocation.getAllocationNo(), offerNo, decision, resultStatus,
                false, decidedAt, nextOfferNo);
    }

    private OfferStatus validateAndAccept(Allocation allocation, Offer offer, Instant now) {
        // 过期边界：expiresAt 时刻仍可接受，由分配行锁决定并发先后。
        if (now.isAfter(offer.getExpiresAt())) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "邀约已过期，不能接受");
        }
        if (allocation.getStatus() != AllocationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "分配流程已结束，不能接受");
        }
        AllocationCandidate snap = snapshotRepository
                .findByAllocationIdAndPosition(allocation.getId(), offer.getPosition())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选快照不存在"));
        Candidate candidate = candidateRepository.lockById(snap.getCandidateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_VIOLATION, "候选人不存在"));
        if (!candidate.isActive()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "候选人当前不是激活状态，不能接受");
        }
        return OfferStatus.ACCEPTED;
    }

    private OfferStatus validateAndDecline(Offer offer, Instant now) {
        if (!now.isBefore(offer.getExpiresAt())) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "邀约已过期，应按过期处理而非拒绝");
        }
        return OfferStatus.DECLINED;
    }

    private OfferStatus validateAndExpire(Offer offer, Instant now) {
        if (now.isBefore(offer.getExpiresAt())) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "邀约尚未到达过期时间");
        }
        return OfferStatus.EXPIRED;
    }

    /**
     * 拒绝/过期后顺序推进：跳过已停用候选人，向仍激活的下一位发约；无剩余则流程耗尽。
     *
     * @return 新邀约编号；流程耗尽时返回 null
     */
    private String advanceToNextCandidate(Allocation allocation, int fromPosition, Instant now) {
        List<AllocationCandidate> snapshot =
                snapshotRepository.findByAllocationIdOrderByPositionAsc(allocation.getId());
        for (int p = fromPosition + 1; p < snapshot.size(); p++) {
            AllocationCandidate next = snapshot.get(p);
            Candidate candidate = candidateRepository.findById(next.getCandidateId()).orElse(null);
            if (candidate == null || !candidate.isActive()) {
                // 等待期间被停用：记录但不发约，继续顺序推进（不跳号——该位置不产生邀约序号）。
                next.setState(CandidateState.DEACTIVATED);
                continue;
            }
            Offer nextOffer = issueOffer(allocation, p, now);
            next.setState(CandidateState.OFFERED);
            allocation.setCurrentPosition(p);
            return nextOffer.getOfferNo();
        }
        allocation.setCurrentPosition(null);
        allocation.setStatus(AllocationStatus.EXHAUSTED);
        allocation.markFinished(now);
        return null;
    }

    private Offer issueOffer(Allocation allocation, int position, Instant now) {
        int sequenceNo = (int) offerRepository.countByAllocationId(allocation.getId()) + 1;
        Instant expiresAt = now.plus(Duration.ofSeconds(allocation.getOfferTtlSeconds()));
        AllocationCandidate snap = snapshotRepository
                .findByAllocationIdAndPosition(allocation.getId(), position)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "候选快照不存在"));
        Offer offer = new Offer(newOfferNo(now), allocation.getId(), sequenceNo, position,
                snap.getCandidateRef(), OfferStatus.PENDING, now, expiresAt);
        offerRepository.saveAndFlush(offer);
        return offer;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AllocationDetailResponse getDetail(String allocationNo) {
        Allocation allocation = allocationRepository.findByAllocationNo(allocationNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "分配流程不存在: " + allocationNo));
        List<AllocationCandidate> snapshot =
                snapshotRepository.findByAllocationIdOrderByPositionAsc(allocation.getId());
        List<Offer> offers = offerRepository.findByAllocationIdOrderBySequenceNoAsc(allocation.getId());
        List<OfferEvent> events = eventRepository.findByAllocationIdOrderByIdAsc(allocation.getId());

        OfferResponse currentOffer = null;
        if (allocation.getCurrentPosition() != null) {
            final int pos = allocation.getCurrentPosition();
            currentOffer = offers.stream()
                    .filter(o -> o.getPosition() == pos && o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .map(AllocationService::toOfferResponse)
                    .orElse(null);
        }

        return new AllocationDetailResponse(
                allocation.getAllocationNo(),
                allocation.getDonorRef(),
                allocation.getOrganType(),
                allocation.getDonorBloodType(),
                allocation.getStatus(),
                allocation.getCreatedAt(),
                allocation.getFinishedAt(),
                snapshot.stream().map(AllocationService::toSnapshotResponse).toList(),
                currentOffer,
                offers.stream().map(AllocationService::toOfferResponse).toList(),
                events.stream().map(AllocationService::toEventResponse).toList(),
                allocation.getRecipientPosition(),
                allocation.getRecipientRef());
    }

    private String currentPendingOfferNo(Allocation allocation) {
        if (allocation.getCurrentPosition() == null) {
            return null;
        }
        return offerRepository
                .findByAllocationIdAndPosition(allocation.getId(), allocation.getCurrentPosition())
                .filter(o -> o.getStatus() == OfferStatus.PENDING)
                .map(Offer::getOfferNo)
                .orElse(null);
    }

    private static List<BloodType> compatibleRecipientBloodTypes(BloodType donorBloodType) {
        return Arrays.stream(BloodType.values())
                .filter(donorBloodType::canDonateTo)
                .toList();
    }

    private static SnapshotCandidateResponse toSnapshotResponse(AllocationCandidate s) {
        return new SnapshotCandidateResponse(s.getPosition(), s.getCandidateRef(), s.getBloodType(),
                s.getUrgency(), s.getWaitlistedAt(), s.getCenterCode(), s.getState());
    }

    private static OfferResponse toOfferResponse(Offer o) {
        return new OfferResponse(o.getOfferNo(), o.getSequenceNo(), o.getPosition(), o.getCandidateRef(),
                o.getStatus(), o.getIssuedAt(), o.getExpiresAt(), o.getDecidedAt());
    }

    private static OfferEventResponse toEventResponse(OfferEvent e) {
        return new OfferEventResponse(e.getEventId(), e.getOfferNo(), e.getDecision(),
                e.getResultStatus(), e.getProcessedAt());
    }

    private static String newAllocationNo(Instant now) {
        return "AL" + NO_TIME_FORMAT.format(now) + randomSuffix();
    }

    private static String newOfferNo(Instant now) {
        return "OF" + NO_TIME_FORMAT.format(now) + randomSuffix();
    }

    private static String randomSuffix() {
        long value = RANDOM.nextLong() & 0xFFFFFFFFFFFFL;
        return String.format("%012x", value);
    }
}
