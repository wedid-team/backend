package com.wedid.backend.invitation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 합류 결과.
 *
 * @param planId         합류한 계획
 * @param role           항상 PARTNER
 * @param joinedAt       합류 시각
 * @param replacedPlanId 합류하면서 정리된 내 기존 계획(P-05). 없었으면 null
 */
public record InvitationAcceptResponse(
        UUID planId,
        String role,
        OffsetDateTime joinedAt,
        UUID replacedPlanId
) {
}
