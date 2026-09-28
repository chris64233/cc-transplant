package com.chris64233.cc.transplant.repo;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.transplant.domain.Allocation;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    boolean existsByDonorId(Long donorId);

    Optional<Allocation> findByAllocationNo(String allocationNo);

    /**
     * 悲观写锁加载分配行，使一次分配的所有决定操作串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Allocation a where a.allocationNo = :allocationNo")
    Optional<Allocation> lockByAllocationNo(@Param("allocationNo") String allocationNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Allocation a where a.id = :id")
    Optional<Allocation> lockById(@Param("id") Long id);
}
