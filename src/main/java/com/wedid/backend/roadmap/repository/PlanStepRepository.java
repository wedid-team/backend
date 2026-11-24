package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.PlanStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanStepRepository extends JpaRepository<PlanStep, UUID> {

    List<PlanStep> findByPlanId(UUID planId);
}
