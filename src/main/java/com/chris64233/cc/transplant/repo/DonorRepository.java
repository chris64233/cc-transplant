package com.chris64233.cc.transplant.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.transplant.domain.Donor;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    boolean existsByExternalRef(String externalRef);

    Optional<Donor> findByExternalRef(String externalRef);
}
