package com.chris64233.cc.transplant.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByOfferNo(String offerNo);

    /** 当前流程中尚未决定的邀约；正常流程下至多一条。 */
    Optional<Offer> findFirstByAllocationIdAndStatusOrderByIdAsc(Long allocationId, OfferStatus status);

    List<Offer> findByAllocationIdOrderByEntryPositionAsc(Long allocationId);

    boolean existsByAllocationIdAndEntryId(Long allocationId, Long entryId);
}
