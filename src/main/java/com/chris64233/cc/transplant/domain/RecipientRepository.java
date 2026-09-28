package com.chris64233.cc.transplant.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    Optional<Recipient> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    /**
     * 查找某供体器官的合格候选人：器官类型一致、血型相容、处于激活状态。
     * 排序在服务层完成（紧急度权重 → 进入名单时间 → 外部编号），以保证规则显式可测。
     */
    @Query("select r from Recipient r where r.organType = :organType and r.active = true "
            + "and r.bloodType in :compatibleBloodTypes")
    List<Recipient> findEligibleCandidates(@Param("organType") OrganType organType,
                                           @Param("compatibleBloodTypes")
                                           java.util.Collection<BloodType> compatibleBloodTypes);
}
