package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.CalculationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CalculationPolicyRepository extends JpaRepository<CalculationPolicy, Long> {

    Optional<CalculationPolicy> findFirstByStatusOrderByIdDesc(String status);
}
