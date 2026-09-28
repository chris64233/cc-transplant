package com.chris64233.cc.transplant.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferEventRepository extends JpaRepository<OfferEvent, Long> {

    Optional<OfferEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<OfferEvent> findByOfferAllocationIdOrderByIdAsc(Long allocationId);
}
