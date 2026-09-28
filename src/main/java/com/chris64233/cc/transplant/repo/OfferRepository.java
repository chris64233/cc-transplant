package com.chris64233.cc.transplant.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.transplant.domain.Offer;
import com.chris64233.cc.transplant.domain.OfferStatus;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByOfferNo(String offerNo);

    /** 悲观写锁加载邀约，决定处理时使用，确保状态判断为最新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.offerNo = :offerNo")
    Optional<Offer> lockByOfferNo(@Param("offerNo") String offerNo);

    long countByAllocationId(Long allocationId);

    Optional<Offer> findByAllocationIdAndPosition(Long allocationId, int position);

    List<Offer> findByAllocationIdOrderBySequenceNoAsc(Long allocationId);

    /** 过期扫描：所有已到过期时间但仍处于 PENDING 的邀约。 */
    List<Offer> findByStatusAndExpiresAtLessThanEqual(OfferStatus status, Instant now);
}
