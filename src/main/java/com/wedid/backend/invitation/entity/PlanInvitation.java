package com.wedid.backend.invitation.entity;

import com.wedid.backend.global.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 짝꿍 초대 링크(db.md 4.24). 토큰 평문은 저장하지 않고 해시만 둔다.
 * 만료 · 수락 · 폐기된 링크는 쓸 수 없다(P-09: 7일, 짝꿍이 합류하면 즉시 사용 불가).
 */
@Entity
@Table(name = "plan_invitations", schema = "public")
public class PlanInvitation extends AssignedIdEntity {

    /** 초대 링크 유효 기간(P-09). */
    public static final Duration TTL = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    // 만료 검사(expires_at > created_at)가 서버 시계 기준으로 맞도록 생성 시각도 직접 넣는다
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected PlanInvitation() {
    }

    public static PlanInvitation create(UUID planId, UUID invitedBy, String tokenHash, OffsetDateTime now) {
        PlanInvitation invitation = new PlanInvitation();
        invitation.id = UUID.randomUUID();
        invitation.planId = planId;
        invitation.invitedBy = invitedBy;
        invitation.tokenHash = tokenHash;
        invitation.createdAt = now;
        invitation.expiresAt = now.plus(TTL);
        return invitation;
    }

    /** 아직 수락 · 폐기되지 않았고 만료 전이면 쓸 수 있다. */
    public boolean isUsable(OffsetDateTime now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isPending() {
        return acceptedAt == null && revokedAt == null;
    }

    public void accept(UUID userId, OffsetDateTime now) {
        this.acceptedBy = userId;
        this.acceptedAt = now;
    }

    public void revoke(OffsetDateTime now) {
        this.revokedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public UUID getAcceptedBy() {
        return acceptedBy;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
