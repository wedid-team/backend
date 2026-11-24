package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.SdmBudgetBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SdmBudgetBandRepository extends JpaRepository<SdmBudgetBand, Long> {

    Optional<SdmBudgetBand> findByPolicyIdAndCode(Long policyId, String code);
}
