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

    @Column(name = "joined_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    protected PlanMember() {
    }

    public static PlanMember owner(UUID planId, UUID userId) {
        PlanMember member = new PlanMember();
        member.planId = planId;
        member.userId = userId;
        member.role = Role.OWNER;
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
}
