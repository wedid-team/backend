package com.wedid.backend.roadmap.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 할 일 상태.
 *
 * @param assignee       조회한 사람 기준 담당: ME / PARTNER / BOTH / UNASSIGNED /
 *                       FORMER_MEMBER(연결 해제로 계획을 떠난 회원 — 지금 짝꿍으로 표시하지 않는다, db.md 4.30)
 * @param assignedUserId 담당 회원 ID. BOTH · UNASSIGNED면 null
 * @param updatedBy      마지막으로 이 할 일을 고친 회원 (P-04)
 */
public record TodoResponse(
        UUID taskId,
        UUID stepId,
        String title,
        boolean completed,
        OffsetDateTime completedAt,
        String assignee,
        UUID assignedUserId,
        UUID updatedBy,
        OffsetDateTime updatedAt
) {
}
