package com.chris64233.cc.transplant.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chris64233.cc.transplant.AbstractIntegrationTest;
import com.chris64233.cc.transplant.domain.AllocationRepository;
import com.chris64233.cc.transplant.domain.AllocationStatus;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferEventRepository;
import com.chris64233.cc.transplant.domain.OfferRepository;
import com.chris64233.cc.transplant.domain.OfferStatus;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.Recipient;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.domain.Urgency;
import com.chris64233.cc.transplant.service.AllocationService;
import com.chris64233.cc.transplant.service.BusinessException;
import com.chris64233.cc.transplant.service.ErrorCode;
import com.chris64233.cc.transplant.web.dto.AllocationDetailResponse;
import com.chris64233.cc.transplant.web.dto.StartAllocationRequest;

class AllocationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AllocationService allocationService;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private OfferEventRepository eventRepository;
    @Autowired
    private RecipientRepository recipientRepository;

    @BeforeEach
    void clean() {
        testDataCleaner.cleanAll();
    }

    private AllocationDetailResponse start(String donorExternalId, long ttlSeconds) {
        return allocationService.startAllocation(
                new StartAllocationRequest(donorExternalId, ttlSeconds));
    }

    private String eid() {
        return UUID.randomUUID().toString();
    }

    // ---------- 1. 快照：血型过滤 + 排序 ----------

    @Test
    void snapshot_filtersByBloodCompatibility_organType_andInactive_andSortsStably() {
        // 供体 A 型肾脏：相容受者为 A、AB；B、O 不相容。
        registerDonor("D-A", OrganType.KIDNEY, BloodType.A);

        // 不相容血型
        registerRecipient("R-B", OrganType.KIDNEY, BloodType.B, Urgency.URGENT,
                T0.minus(100, ChronoUnit.DAYS), true, "C");
        registerRecipient("R-O", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(100, ChronoUnit.DAYS), true, "C");
        // 器官类型不符
        registerRecipient("R-LIVER", OrganType.LIVER, BloodType.A, Urgency.URGENT,
                T0.minus(100, ChronoUnit.DAYS), true, "C");
        // 未激活
        registerRecipient("R-INACTIVE", OrganType.KIDNEY, BloodType.A, Urgency.URGENT,
                T0.minus(100, ChronoUnit.DAYS), false, "C");
        // 合格候选人
        registerRecipient("R-A-STD-EARLY", OrganType.KIDNEY, BloodType.A, Urgency.STANDARD,
                T0.minus(30, ChronoUnit.DAYS), true, "C");
        registerRecipient("R-A-HIGH", OrganType.KIDNEY, BloodType.A, Urgency.HIGH,
                T0.minus(5, ChronoUnit.DAYS), true, "C");
        registerRecipient("R-AB-HIGH-EARLIER", OrganType.KIDNEY, BloodType.AB, Urgency.HIGH,
                T0.minus(20, ChronoUnit.DAYS), true, "C");
        registerRecipient("R-A-URGENT", OrganType.KIDNEY, BloodType.A, Urgency.URGENT,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        // 与 R-A-HIGH 同紧急度同等待时间，验证外部编号兜底排序
        registerRecipient("R-A-HIGH-TIE", OrganType.KIDNEY, BloodType.A, Urgency.HIGH,
                T0.minus(5, ChronoUnit.DAYS), true, "C");

        var detail = start("D-A", 3600);

        var positions = detail.snapshot().stream()
                .map(s -> s.recipientExternalId()).toList();
        assertEquals(List.of(
                "R-A-URGENT",          // URGENT 最高
                "R-AB-HIGH-EARLIER",   // HIGH 且等待最久
                "R-A-HIGH",            // HIGH 同等待时间，编号小
                "R-A-HIGH-TIE",
                "R-A-STD-EARLY"),      // STANDARD
                positions);
        assertTrue(positions.stream().noneMatch(
                n -> n.equals("R-B") || n.equals("R-O") || n.equals("R-LIVER")
                        || n.equals("R-INACTIVE")));
    }

    @Test
    void startAllocation_withNoEligibleCandidate_failsAndCreatesNothing() {
        registerDonor("D-EMPTY", OrganType.HEART, BloodType.AB);
        // 唯一候选人血型不相容
        registerRecipient("X", OrganType.HEART, BloodType.O, Urgency.HIGH,
                T0.minus(1, ChronoUnit.DAYS), true, "C");

        BusinessException ex = assertThrows(BusinessException.class, () -> start("D-EMPTY", 60));
        assertEquals(422, ex.getHttpStatus());
        assertEquals(ErrorCode.NO_ELIGIBLE_CANDIDATE, ex.getErrorCode());
        assertEquals(0, allocationRepository.count(), "失败时不得创建空流程");
    }

    @Test
    void startAllocation_forUnknownDonor_returns404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> start("NO-SUCH-DONOR", 60));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void startAllocation_twiceForSameDonor_conflicts409() {
        registerDonor("D-TWICE", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(1, ChronoUnit.DAYS), true, "C");

        start("D-TWICE", 60);
        BusinessException ex = assertThrows(BusinessException.class, () -> start("D-TWICE", 60));
        assertEquals(409, ex.getHttpStatus());
        assertEquals(1, allocationRepository.count());
    }

    @Test
    void snapshot_isImmutable_whenRecipientLaterChanges() {
        registerDonor("D-IMM", OrganType.KIDNEY, BloodType.O);
        Recipient r = registerRecipient("R-IMM", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "原中心");
        var detail = start("D-IMM", 3600);

        // 启动后修改候选人，快照内容不应改变
        r.deactivate();
        recipientRepository.save(r);

        var reloaded = allocationService.getDetail(detail.allocationNo());
        var snap = reloaded.snapshot().get(0);
        assertEquals("原中心", snap.center());
        assertTrue(snap.activeAtSnapshot());
    }

    // ---------- 2. 邀约逐一发出 ----------

    @Test
    void offers_areIssuedOneAtATime_inSnapshotOrder_withExpiry() {
        registerDonor("D-SEQ", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-SEQ", 60);

        var offer1 = allocationService.issueOffer(detail.allocationNo());
        assertEquals(1, offer1.position());
        assertEquals("R1", offer1.recipientExternalId());
        assertEquals(T0.plusSeconds(60), offer1.expiresAt());
        assertEquals(OfferStatus.PENDING, offer1.status());

        // 当前邀约仍有效：不能再发，返回同一份（幂等）
        var again = allocationService.issueOffer(detail.allocationNo());
        assertEquals(offer1.offerNo(), again.offerNo());
    }

    @Test
    void whileOfferPending_noSecondOfferCanExist_andExpiredOfferMustBeProcessed() {
        registerDonor("D-DECL", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-DECL", 3600);

        var offer1 = allocationService.issueOffer(detail.allocationNo());

        // 当前邀约仍有效：重复发起不会产生第二份邀约，只返回同一份
        var same = allocationService.issueOffer(detail.allocationNo());
        assertEquals(offer1.offerNo(), same.offerNo());
        assertEquals(1, offerRepository.count());

        // 邀约过期但未做过期处理：不能跳过它直接发下一位
        clock.advanceSeconds(3601);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.issueOffer(detail.allocationNo()));
        assertEquals(ErrorCode.OFFER_EXPIRED, ex.getErrorCode());
        assertEquals(1, offerRepository.count(), "不得跳过过期邀约直接为下一位发邀约");
    }

    @Test
    void afterDecline_nextOfferGoesToNextCandidate() {
        registerDonor("D-NEXT", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-NEXT", 3600);
        String no = detail.allocationNo();

        var offer1 = allocationService.issueOffer(no);
        var declined = allocationService.decline(no, offer1.offerNo(), eid());
        assertFalse(declined.replayed());
        assertEquals(OfferStatus.DECLINED, declined.offer().status());

        var offer2 = allocationService.issueOffer(no);
        assertEquals(2, offer2.position());
        assertEquals("R2", offer2.recipientExternalId());
    }

    // ---------- 3. 接受 ----------

    @Test
    void accept_withinValidity_allocatesOrgan_andBlocksFurtherActions() {
        registerDonor("D-ACC", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-ACC", 3600);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);

        clock.advanceSeconds(60); // 仍在有效期
        var outcome = allocationService.accept(no, offer1.offerNo(), eid());

        assertEquals(OfferStatus.ACCEPTED, outcome.offer().status());
        assertFalse(outcome.replayed());

        var after = allocationService.getDetail(no);
        assertEquals(AllocationStatus.ALLOCATED, after.status());
        assertEquals("R1", after.acceptedRecipientExternalId());
        assertNull(after.currentOffer());

        // 流程已终态：不能再发邀约
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.issueOffer(no));
        assertEquals(ErrorCode.ALLOCATION_FINISHED, ex.getErrorCode());
        // 另一位候选人不能接受（其邀约尚不存在；即便对已接受邀约再操作也被拒绝）
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> allocationService.accept(no, offer1.offerNo(), eid()));
        assertEquals(409, ex2.getHttpStatus());
    }

    @Test
    void accept_afterExpiryTime_isRejected() {
        registerDonor("D-LATE", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-LATE", 60);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);

        clock.advanceSeconds(61); // 已过期
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.accept(no, offer1.offerNo(), eid()));
        assertEquals(ErrorCode.OFFER_EXPIRED, ex.getErrorCode());
        assertEquals(OfferStatus.PENDING,
                offerRepository.findByOfferNo(offer1.offerNo()).orElseThrow().getStatus());
    }

    @Test
    void accept_whenRecipientDeactivated_isRejected() {
        registerDonor("D-INACT", OrganType.KIDNEY, BloodType.O);
        Recipient r1 = registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-INACT", 3600);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);

        r1.deactivate();
        recipientRepository.save(r1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.accept(no, offer1.offerNo(), eid()));
        assertEquals(ErrorCode.RECIPIENT_INACTIVE, ex.getErrorCode());
        assertEquals(AllocationStatus.IN_PROGRESS,
                allocationRepository.findByAllocationNo(no).orElseThrow().getStatus());
    }

    // ---------- 4. 过期 ----------

    @Test
    void expire_beforeExpiryTime_conflicts() {
        registerDonor("D-NEARLY", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-NEARLY", 60);
        var offer1 = allocationService.issueOffer(detail.allocationNo());

        clock.advanceSeconds(30);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.expire(detail.allocationNo(), offer1.offerNo(), eid()));
        assertEquals(ErrorCode.OFFER_NOT_EXPIRED, ex.getErrorCode());
    }

    @Test
    void expire_afterExpiry_thenAdvanceToNextCandidate() {
        registerDonor("D-EXP", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-EXP", 60);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);

        clock.advanceSeconds(61);
        var expired = allocationService.expire(no, offer1.offerNo(), eid());
        assertEquals(OfferStatus.EXPIRED, expired.offer().status());

        var offer2 = allocationService.issueOffer(no);
        assertEquals(2, offer2.position());
        assertEquals("R2", offer2.recipientExternalId());

        // 末位接受 -> ALLOCATED
        clock.advanceSeconds(10);
        allocationService.accept(no, offer2.offerNo(), eid());
        assertEquals(AllocationStatus.ALLOCATED,
                allocationRepository.findByAllocationNo(no).orElseThrow().getStatus());
    }

    @Test
    void whenLastCandidateDeclines_allocationBecomesExhausted() {
        registerDonor("D-EXH", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-EXH", 60);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);

        allocationService.decline(no, offer1.offerNo(), eid());

        var allocation = allocationRepository.findByAllocationNo(no).orElseThrow();
        assertEquals(AllocationStatus.EXHAUSTED, allocation.getStatus());
        // 再发邀约 -> 终态冲突
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.issueOffer(no));
        assertEquals(ErrorCode.ALLOCATION_FINISHED, ex.getErrorCode());
    }

    // ---------- 5. 幂等 ----------

    @Test
    void sameEventId_replay_returnsOriginalResult() {
        registerDonor("D-IDEM", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-IDEM", 60);
        var offer1 = allocationService.issueOffer(detail.allocationNo());
        String eventId = eid();

        var first = allocationService.decline(detail.allocationNo(), offer1.offerNo(), eventId);
        var replay = allocationService.decline(detail.allocationNo(), offer1.offerNo(), eventId);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(OfferStatus.DECLINED, replay.offer().status());
        assertEquals(1, eventRepository.count(), "重放不得产生第二个事件");
    }

    @Test
    void sameEventId_withConflictingContent_returns409() {
        registerDonor("D-CONF", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-CONF", 60);
        String no = detail.allocationNo();
        var offer1 = allocationService.issueOffer(no);
        String eventId = eid();

        allocationService.decline(no, offer1.offerNo(), eventId);
        var offer2 = allocationService.issueOffer(no);

        // 同一 eventId 用于另一份邀约的接受 -> 409
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.accept(no, offer2.offerNo(), eventId));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());

        // 同一 eventId 对同一邀约但决定类型不同 -> 409
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> allocationService.accept(no, offer1.offerNo(), eventId));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex2.getErrorCode());
    }

    @Test
    void expireIdempotency_replayReturnsOriginalExpiredResult() {
        registerDonor("D-EXP-IDEM", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-EXP-IDEM", 60);
        var offer1 = allocationService.issueOffer(detail.allocationNo());
        String eventId = eid();
        clock.advanceSeconds(61);

        var first = allocationService.expire(detail.allocationNo(), offer1.offerNo(), eventId);
        var replay = allocationService.expire(detail.allocationNo(), offer1.offerNo(), eventId);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(OfferStatus.EXPIRED, replay.offer().status());
        assertEquals(1, eventRepository.count());
    }

    @Test
    void decisionForUnknownOfferOrWrongAllocation_returns404or409() {
        registerDonor("D-MIS", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerDonor("D-OTHER", OrganType.KIDNEY, BloodType.O);
        registerRecipient("RO", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");

        var a1 = start("D-MIS", 60);
        var a2 = start("D-OTHER", 60);
        var offer1 = allocationService.issueOffer(a1.allocationNo());
        var offerOther = allocationService.issueOffer(a2.allocationNo());

        assertEquals(404, assertThrows(BusinessException.class,
                () -> allocationService.accept(a1.allocationNo(), "OF-NONEXIST", eid()))
                .getHttpStatus());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.accept(a1.allocationNo(), offerOther.offerNo(), eid()));
        assertEquals(409, ex.getHttpStatus());
        // offer1 仍是 PENDING，未被跨流程调用影响
        assertEquals(OfferStatus.PENDING,
                offerRepository.findByOfferNo(offer1.offerNo()).orElseThrow().getStatus());
    }

    // ---------- 6. 并发：最多一个结果生效 ----------

    @Test
    void twoConcurrentAccepts_onlyOneSucceeds() throws Exception {
        registerDonor("D-CONCUR", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-CONCUR", 3600);
        var offer = allocationService.issueOffer(detail.allocationNo());
        String no = detail.allocationNo();
        String offerNo = offer.offerNo();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String eventId = "EVT-ACC-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    allocationService.accept(no, offerNo, eventId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "恰好一个接受生效");
        assertEquals(threads - 1, conflict.get(), "其余并发接受全部冲突失败");
        assertEquals(AllocationStatus.ALLOCATED,
                allocationRepository.findByAllocationNo(no).orElseThrow().getStatus());
        assertEquals(1, eventRepository.count(), "只持久化一个接受事件");
        assertNotNull(allocationRepository.findByAllocationNo(no).orElseThrow().getAcceptedEntry());
    }

    @Test
    void concurrentAcceptAndExpiry_onlyOneTakesEffect() throws Exception {
        registerDonor("D-RACE", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        var detail = start("D-RACE", 60);
        var offer = allocationService.issueOffer(detail.allocationNo());
        String no = detail.allocationNo();
        String offerNo = offer.offerNo();

        // 恰好处于过期边界之后
        clock.advanceSeconds(61);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> acceptFuture = pool.submit(() -> {
            ready.countDown();
            start.await();
            return runCatching(() -> allocationService.accept(no, offerNo, "EVT-RACE-A"));
        });
        Future<?> expireFuture = pool.submit(() -> {
            ready.countDown();
            start.await();
            return runCatching(() -> allocationService.expire(no, offerNo, "EVT-RACE-E"));
        });
        ready.await();
        start.countDown();

        Throwable acceptError = (Throwable) acceptFuture.get(20, TimeUnit.SECONDS);
        Throwable expireError = (Throwable) expireFuture.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        long succeeded = (acceptError == null ? 1 : 0) + (expireError == null ? 1 : 0);
        assertEquals(1, succeeded, "接受与过期只能有一个生效");
        assertEquals(1, eventRepository.count(), "只能落一个事件，无部分事件");

        Offer reloaded = offerRepository.findByOfferNo(offerNo).orElseThrow();
        if (acceptError == null) {
            // 该分支理论上不会出现（时间已过有效期），保留以严格校验互斥语义
            assertEquals(OfferStatus.ACCEPTED, reloaded.getStatus());
            assertEquals(AllocationStatus.ALLOCATED,
                    allocationRepository.findByAllocationNo(no).orElseThrow().getStatus());
        } else {
            // 接受失败：可能因自身过期校验，也可能因先拿到锁的过期事务已置为 EXPIRED
            assertEquals(OfferStatus.EXPIRED, reloaded.getStatus());
            assertTrue(acceptError instanceof BusinessException);
            ErrorCode code = ((BusinessException) acceptError).getErrorCode();
            assertTrue(code == ErrorCode.OFFER_EXPIRED || code == ErrorCode.CONFLICT,
                    "接受方错误码应为 OFFER_EXPIRED 或 CONFLICT，实际: " + code);
        }
    }

    private Throwable runCatching(RunnableThrowing r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @FunctionalInterface
    private interface RunnableThrowing {
        void run() throws Exception;
    }

    // ---------- 7. 详情查询 ----------

    @Test
    void detail_returnsSnapshotCurrentOfferDecisionsAndFinalRecipient() {
        registerDonor("D-VIEW", OrganType.KIDNEY, BloodType.O);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.O, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "C");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "C");
        var detail = start("D-VIEW", 60);
        String no = detail.allocationNo();

        var offer1 = allocationService.issueOffer(no);
        clock.advanceSeconds(61);
        allocationService.expire(no, offer1.offerNo(), "EVT-1");
        var offer2 = allocationService.issueOffer(no);
        allocationService.accept(no, offer2.offerNo(), "EVT-2");

        var view = allocationService.getDetail(no);
        assertEquals(2, view.snapshot().size());
        assertEquals(AllocationStatus.ALLOCATED, view.status());
        assertNull(view.currentOffer(), "终态不应存在当前邀约");
        assertEquals("R2", view.acceptedRecipientExternalId());

        var decisions = view.decisions();
        assertEquals(2, decisions.size());
        assertEquals("EVT-1", decisions.get(0).eventId());
        assertEquals(OfferStatus.EXPIRED, decisions.get(0).resultStatus());
        assertEquals("EVT-2", decisions.get(1).eventId());
        assertEquals(OfferStatus.ACCEPTED, decisions.get(1).resultStatus());
    }
}
