package com.wedid.backend.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 계획 참여자. 한 사용자는 계획 하나에만 속하고(UNIQUE user_id),
 * 계획마다 OWNER 1명 · PARTNER 최대 1명이다. 내 계획은 이 멤버십으로 찾는다.
 */
@Entity
@Table(name = "plan_members", schema = "public")
public class PlanMember {

    public enum Role {
        OWNER, PARTNER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 합류 직후 응답에도 같은 시각을 쓰도록 서버 시계 값을 직접 넣는다
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    protected PlanMember() {
    }

    public static PlanMember owner(UUID planId, UUID userId, OffsetDateTime now) {
        return of(planId, userId, Role.OWNER, now);
    }

    /** 초대 링크로 합류한 짝꿍. */
    public static PlanMember partner(UUID planId, UUID userId, OffsetDateTime now) {
        return of(planId, userId, Role.PARTNER, now);
    }

    private static PlanMember of(UUID planId, UUID userId, Role role, OffsetDateTime now) {
        PlanMember member = new PlanMember();
        member.planId = planId;
        member.userId = userId;
        member.role = role;
        member.joinedAt = now;
        return member;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
