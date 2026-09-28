package com.chris64233.cc.transplant.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    Optional<Donor> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
