package com.chris64233.cc.transplant.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.transplant.domain.OfferEvent;

public interface OfferEventRepository extends JpaRepository<OfferEvent, Long> {

    Optional<OfferEvent> findByEventId(String eventId);

    List<OfferEvent> findByAllocationIdOrderByIdAsc(Long allocationId);
}
