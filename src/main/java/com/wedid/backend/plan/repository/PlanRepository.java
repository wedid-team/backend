package com.wedid.backend.plan.repository;

import com.wedid.backend.plan.entity.Plan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByIdAndStatus(UUID id, Plan.Status status);

    /** 짝꿍이 동시에 고쳐도 저장한 순서대로 마지막 수정이 남도록 계획 행을 잠근다(P-04). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plan p where p.id = :id and p.status = :status")
    Optional<Plan> findByIdAndStatusForUpdate(@Param("id") UUID id, @Param("status") Plan.Status status);
}
