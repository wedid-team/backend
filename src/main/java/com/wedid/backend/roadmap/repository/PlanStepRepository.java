package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.PlanStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanStepRepository extends JpaRepository<PlanStep, UUID> {

    List<PlanStep> findByPlanId(UUID planId);

    /** 다른 계획의 단계는 찾지 않는다. */
    Optional<PlanStep> findByIdAndPlanId(UUID id, UUID planId);
}
