package com.wedid.backend.invitation.repository;

import com.wedid.backend.invitation.entity.PlanInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, UUID> {

    Optional<PlanInvitation> findByTokenHash(String tokenHash);

    /** 엔티티를 불러오지 않고 계획 ID만 본다(잠그기 전에 읽은 값이 영속성 컨텍스트에 남지 않도록). */
    @Query("select i.planId from PlanInvitation i where i.tokenHash = :tokenHash")
    Optional<UUID> findPlanIdByTokenHash(@Param("tokenHash") String tokenHash);

    /** 아직 수락 · 폐기되지 않은 링크(만료 여부와 상관없이). */
    @Query("select i from PlanInvitation i where i.planId = :planId and i.acceptedAt is null and i.revokedAt is null")
    List<PlanInvitation> findPendingByPlanId(@Param("planId") UUID planId);
}
