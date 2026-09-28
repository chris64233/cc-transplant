package com.chris64233.cc.transplant.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    boolean existsByDonorId(Long donorId);

    Optional<Allocation> findByAllocationNo(String allocationNo);

    /**
     * 对分配流程主行加悲观写锁。邀约发出与所有决定都在该锁内进行，
     * 串行化“接受 vs 过期”“两个接受”等并发，杜绝双重分配与跳号。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Allocation a where a.id = :id")
    java.util.Optional<Allocation> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Allocation a where a.allocationNo = :allocationNo")
    Optional<Allocation> findByAllocationNoForUpdate(
            @org.springframework.data.repository.query.Param("allocationNo") String allocationNo);
}
