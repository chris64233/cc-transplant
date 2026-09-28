package com.chris64233.cc.transplant.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.Candidate;
import com.chris64233.cc.transplant.domain.OrganType;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    boolean existsByExternalRef(String externalRef);

    Optional<Candidate> findByExternalRef(String externalRef);

    /** 悲观写锁加载候选人，接受邀约时使用，避免与停用操作竞态。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Candidate c where c.id = :id")
    Optional<Candidate> lockById(@Param("id") Long id);

    /**
     * 找出某器官类型下、血型在可接受集合内且当前激活的候选人。
     * 排序固化在服务层完成（紧急等级降序、等待时间升序、编号稳定升序）。
     */
    List<Candidate> findByOrganTypeAndActiveTrueAndBloodTypeIn(OrganType organType,
                                                               List<BloodType> acceptableBloodTypes);
}
