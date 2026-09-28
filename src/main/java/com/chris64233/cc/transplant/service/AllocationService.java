package com.chris64233.cc.transplant.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.transplant.domain.Allocation;
import com.chris64233.cc.transplant.domain.AllocationEntry;
import com.chris64233.cc.transplant.domain.AllocationRepository;
import com.chris64233.cc.transplant.domain.AllocationStatus;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.DecisionType;
import com.chris64233.cc.transplant.domain.Donor;
import com.chris64233.cc.transplant.domain.DonorRepository;
import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferEvent;
import com.chris64233.cc.transplant.domain.OfferEventRepository;
import com.chris64233.cc.transplant.domain.OfferRepository;
import com.chris64233.cc.transplant.domain.OfferStatus;
import com.chris64233.cc.transplant.domain.Recipient;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.web.dto.AllocationDetailResponse;
import com.chris64233.cc.transplant.web.dto.DecisionEventResponse;
import com.chris64233.cc.transplant.web.dto.OfferResponse;
import com.chris64233.cc.transplant.web.dto.SnapshotEntryResponse;
import com.chris64233.cc.transplant.web.dto.StartAllocationRequest;

/**
 * 器官分配核心服务。
 *
 * <p>并发模型：所有写操作（发邀约、接受、拒绝、过期）都先对 allocation 主行加
 * 悲观写锁（SELECT ... FOR UPDATE），因此同一流程上的并发决定被数据库串行化；
 * 配合 offer_event.event_id 的唯一约束，保证“接受 vs 过期”“两个接受”最多一个结果生效，
 * 不会出现跳号、双重分配或部分事件。</p>
 */
@Service
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final DonorRepository donorRepository;
    private final RecipientRepository recipientRepository;
    private final OfferRepository offerRepository;
    private final OfferEventRepository eventRepository;
    private final Clock clock;

    public AllocationService(AllocationRepository allocationRepository,
                             DonorRepository donorRepository,
                             RecipientRepository recipientRepository,
                             OfferRepository offerRepository,
                             OfferEventRepository eventRepository,
                             Clock clock) {
        this.allocationRepository = allocationRepository;
        this.donorRepository = donorRepository;
        this.recipientRepository = recipientRepository;
        this.offerRepository = offerRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /** 决定操作的返回结果，区分本次生效与幂等重放。 */
    public record DecisionOutcome(OfferResponse offer, boolean replayed) {
    }

    /**
     * 启动分配：过滤合格候选人 → 排序 → 固化不可变快照。
     * 没有合格候选人时明确失败（422），不创建任何流程。
     */
    @Transactional
    public AllocationDetailResponse startAllocation(StartAllocationRequest request) {
        Donor donor = donorRepository.findByExternalId(request.donorExternalId())
                .orElseThrow(() -> BusinessException.notFound(
                        "供体不存在: " + request.donorExternalId()));

        if (allocationRepository.existsByDonorId(donor.getId())) {
            throw BusinessException.conflict(ErrorCode.CONFLICT,
                    "该供体器官已存在分配流程，不能重复启动: " + donor.getExternalId());
        }

        List<BloodType> compatibleBlood =
                RegistrationService.compatibleRecipientBloodTypes(donor.getBloodType());
        List<Recipient> candidates = recipientRepository
                .findEligibleCandidates(donor.getOrganType(), compatibleBlood)
                .stream()
                .sorted(RegistrationService::compareCandidate)
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ELIGIBLE_CANDIDATE, 422,
                    "没有器官类型与血型相容且处于激活状态的候选人，无法启动分配");
        }

        Allocation allocation = new Allocation(newAllocationNo(), donor, Instant.now(clock),
                request.offerTtlSeconds());
        allocationRepository.save(allocation);

        int position = 1;
        for (Recipient candidate : candidates) {
            allocation.addEntry(new AllocationEntry(allocation, position++, candidate));
        }
        try {
            allocation = allocationRepository.saveAndFlush(allocation);
        } catch (DataIntegrityViolationException e) {
            // 并发下两个请求同时通过 existsByDonorId 检查时，由唯一约束兜底。
            throw BusinessException.conflict(ErrorCode.CONFLICT,
                    "该供体器官已存在分配流程，不能重复启动: " + donor.getExternalId());
        }
        return buildDetail(allocation);
    }

    /**
     * 向当前应发出邀约的候选人发出限时邀约。
     * 必须不存在有效（PENDING）邀约；存在仍有效 PENDING 邀约时原样返回（幂等）。
     */
    @Transactional
    public OfferResponse issueOffer(String allocationNo) {
        Allocation allocation = lockAllocation(allocationNo);
        Instant now = Instant.now(clock);

        Offer pending = findPendingOffer(allocation);
        if (pending != null) {
            if (pending.isValidAt(now)) {
                // 已有一份有效邀约，不能再发；原样返回当前邀约。
                return OfferResponse.from(pending);
            }
            throw BusinessException.conflict(ErrorCode.OFFER_EXPIRED,
                    "当前邀约已过期，请先进行过期处理后再向下一位候选人发出邀约");
        }

        if (allocation.getStatus() != AllocationStatus.IN_PROGRESS) {
            throw BusinessException.conflict(ErrorCode.ALLOCATION_FINISHED,
                    "分配流程已结束，状态: " + allocation.getStatus());
        }

        int nextPosition = nextPosition(allocation);
        if (nextPosition > allocation.getEntries().size()) {
            // 正常不会发生：末位候选人拒绝/过期时已在同一事务内置为 EXHAUSTED。
            throw BusinessException.conflict(ErrorCode.ALLOCATION_FINISHED,
                    "候选名单已遍历完毕，无人接受，流程结束");
        }

        AllocationEntry entry = allocation.getEntries().get(nextPosition - 1);
        Instant issuedAt = now;
        Instant expiresAt = now.plusSeconds(allocation.getOfferTtlSeconds());
        Offer offer = new Offer(newOfferNo(), allocation, entry, issuedAt, expiresAt);
        return OfferResponse.from(offerRepository.save(offer));
    }

    /**
     * 接受邀约。仅允许：邀约存在且属于该流程、仍在有效期内、候选人仍为激活状态、
     * 流程未结束。成功后器官进入 ALLOCATED 终态，其他候选人不能再接受。
     */
    @Transactional
    public DecisionOutcome accept(String allocationNo, String offerNo, String eventId) {
        return decide(allocationNo, offerNo, eventId, DecisionType.ACCEPT);
    }

    /** 拒绝邀约。拒绝后才允许向下一位发出邀约。 */
    @Transactional
    public DecisionOutcome decline(String allocationNo, String offerNo, String eventId) {
        return decide(allocationNo, offerNo, eventId, DecisionType.DECLINE);
    }

    /**
     * 过期处理。仅当邀约已过过期时间才允许；过期后才允许向下一位发出邀约。
     */
    @Transactional
    public DecisionOutcome expire(String allocationNo, String offerNo, String eventId) {
        Allocation allocation = lockAllocation(allocationNo);
        Instant now = Instant.now(clock);

        OfferEvent existing = eventRepository.findByEventId(eventId).orElse(null);
        if (existing != null) {
            return replayOrConflict(existing, offerNo, DecisionType.EXPIRE);
        }

        Offer offer = requireOfferInAllocation(allocation, offerNo);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw BusinessException.conflict(ErrorCode.CONFLICT,
                    "邀约已处于终态 " + offer.getStatus() + "，不能再标记过期");
        }
        if (now.isBefore(offer.getExpiresAt())) {
            throw BusinessException.conflict(ErrorCode.OFFER_NOT_EXPIRED,
                    "邀约尚在有效期内（过期时间 " + offer.getExpiresAt() + "），不能标记过期");
        }

        return applyDecision(allocation, offer, DecisionType.EXPIRE, OfferStatus.EXPIRED, eventId, now);
    }

    // ---- 内部逻辑 ----

    private DecisionOutcome decide(String allocationNo, String offerNo, String eventId,
                                   DecisionType decision) {
        Allocation allocation = lockAllocation(allocationNo);
        Instant now = Instant.now(clock);

        OfferEvent existing = eventRepository.findByEventId(eventId).orElse(null);
        if (existing != null) {
            return replayOrConflict(existing, offerNo, decision);
        }

        Offer offer = requireOfferInAllocation(allocation, offerNo);
        if (!offer.isPending()) {
            throw BusinessException.conflict(ErrorCode.CONFLICT,
                    "邀约已处于终态 " + offer.getStatus() + "，不能重复决定");
        }

        if (decision == DecisionType.ACCEPT) {
            if (allocation.getStatus() != AllocationStatus.IN_PROGRESS) {
                throw BusinessException.conflict(ErrorCode.ALLOCATION_FINISHED,
                        "分配流程已结束，状态: " + allocation.getStatus());
            }
            if (!offer.isValidAt(now)) {
                throw BusinessException.conflict(ErrorCode.OFFER_EXPIRED,
                        "邀约已超过过期时间 " + offer.getExpiresAt() + "，不能接受");
            }
            boolean stillActive = recipientRepository.findById(offer.getEntry().getRecipientId())
                    .map(Recipient::isActive)
                    .orElse(false);
            if (!stillActive) {
                throw BusinessException.conflict(ErrorCode.RECIPIENT_INACTIVE,
                        "候选人当前已不是激活状态，不能接受: "
                                + offer.getEntry().getRecipientExternalId());
            }
        }

        OfferStatus resultStatus = switch (decision) {
            case ACCEPT -> OfferStatus.ACCEPTED;
            case DECLINE -> OfferStatus.DECLINED;
            case EXPIRE -> OfferStatus.EXPIRED;
        };
        return applyDecision(allocation, offer, decision, resultStatus, eventId, now);
    }

    private DecisionOutcome applyDecision(Allocation allocation, Offer offer, DecisionType decision,
                                          OfferStatus resultStatus, String eventId, Instant now) {
        offer.decide(resultStatus, now);
        offerRepository.save(offer);

        if (decision == DecisionType.ACCEPT) {
            allocation.markAllocated(offer.getEntry());
        } else if (offer.getEntry().getPosition() >= allocation.getEntries().size()) {
            // 末位候选人拒绝或过期：在同一事务内收敛为 EXHAUSTED 终态。
            allocation.markExhausted();
        }
        allocationRepository.save(allocation);

        // 事件行带 event_id 唯一约束：并发下只有一个事务能插入，另一个回滚，杜绝双重结果。
        eventRepository.save(new OfferEvent(eventId, offer, decision, resultStatus, now));
        return new DecisionOutcome(OfferResponse.from(offer), false);
    }

    /**
     * 幂等重放：相同 eventId + 相同邀约 + 相同决定类型 → 返回原结果；
     * 任一字段不一致 → 409 冲突。
     */
    private DecisionOutcome replayOrConflict(OfferEvent existing, String offerNo,
                                             DecisionType decision) {
        boolean sameOffer = existing.getOfferNo().equals(offerNo);
        boolean sameDecision = existing.getDecision() == decision;
        if (!sameOffer || !sameDecision) {
            throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "幂等标识 " + existing.getEventId() + " 已用于 "
                            + existing.getDecision() + " 邀约 " + existing.getOfferNo()
                            + "，与本次请求（" + decision + " 邀约 " + offerNo + "）冲突");
        }
        Offer original = offerRepository.findByOfferNo(existing.getOfferNo())
                .orElseThrow(() -> BusinessException.notFound(
                        "原邀约不存在: " + existing.getOfferNo()));
        return new DecisionOutcome(OfferResponse.from(original), true);
    }

    private Allocation lockAllocation(String allocationNo) {
        return allocationRepository.findByAllocationNoForUpdate(allocationNo)
                .orElseThrow(() -> BusinessException.notFound(
                        "分配流程不存在: " + allocationNo));
    }

    private Offer requireOfferInAllocation(Allocation allocation, String offerNo) {
        Offer offer = offerRepository.findByOfferNo(offerNo)
                .orElseThrow(() -> BusinessException.notFound("邀约不存在: " + offerNo));
        if (!offer.getAllocation().getId().equals(allocation.getId())) {
            throw BusinessException.conflict(ErrorCode.CONFLICT,
                    "邀约 " + offerNo + " 不属于分配流程 " + allocation.getAllocationNo());
        }
        return offer;
    }

    private Offer findPendingOffer(Allocation allocation) {
        return offerRepository
                .findFirstByAllocationIdAndStatusOrderByIdAsc(allocation.getId(), OfferStatus.PENDING)
                .orElse(null);
    }

    /** 下一份邀约的候选位（1 起）：已存在邀约的最大 position + 1。 */
    private int nextPosition(Allocation allocation) {
        return offerRepository.findByAllocationIdOrderByEntryPositionAsc(allocation.getId())
                .stream()
                .mapToInt(o -> o.getEntry().getPosition())
                .max()
                .orElse(0) + 1;
    }

    private String newAllocationNo() {
        return "AL" + UUID.randomUUID().toString().replace("-", "");
    }

    private String newOfferNo() {
        return "OF" + UUID.randomUUID().toString().replace("-", "");
    }

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public AllocationDetailResponse getDetail(String allocationNo) {
        Allocation allocation = allocationRepository.findByAllocationNo(allocationNo)
                .orElseThrow(() -> BusinessException.notFound(
                        "分配流程不存在: " + allocationNo));
        return buildDetail(allocation);
    }

    /** 在事务/会话内完成所有关联映射，避免懒加载脱离会话。 */
    private AllocationDetailResponse buildDetail(Allocation allocation) {
        List<SnapshotEntryResponse> snapshot = allocation.getEntries().stream()
                .map(SnapshotEntryResponse::from)
                .toList();

        List<Offer> offers =
                offerRepository.findByAllocationIdOrderByEntryPositionAsc(allocation.getId());
        OfferResponse currentOffer = offers.stream()
                .filter(Offer::isPending)
                .findFirst()
                .map(OfferResponse::from)
                .orElse(null);

        List<DecisionEventResponse> decisions =
                eventRepository.findByOfferAllocationIdOrderByIdAsc(allocation.getId()).stream()
                        .map(DecisionEventResponse::from)
                        .toList();

        return AllocationDetailResponse.of(allocation, snapshot, currentOffer, decisions);
    }
}
