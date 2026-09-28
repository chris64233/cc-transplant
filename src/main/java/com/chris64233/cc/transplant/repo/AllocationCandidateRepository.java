package com.chris64233.cc.transplant.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.transplant.domain.AllocationCandidate;

public interface AllocationCandidateRepository extends JpaRepository<AllocationCandidate, Long> {

    List<AllocationCandidate> findByAllocationIdOrderByPositionAsc(Long allocationId);

    Optional<AllocationCandidate> findByAllocationIdAndPosition(Long allocationId, int position);
}
