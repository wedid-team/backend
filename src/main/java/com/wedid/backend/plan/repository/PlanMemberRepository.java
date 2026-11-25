package com.wedid.backend.plan.repository;

import com.wedid.backend.plan.entity.PlanMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanMemberRepository extends JpaRepository<PlanMember, Long> {

    Optional<PlanMember> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<PlanMember> findByPlanId(UUID planId);
}
