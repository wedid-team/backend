package com.wedid.backend.roadmap.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 완료 처리 · 실제 계약 금액 입력 후 단계 상태. 금액은 원 단위.
 *
 * @param estimated  단계 예상 비용. 금액 단계가 아니면 null, 금액 단계인데 미정이면 min · max가 null
 * @param updatedBy  마지막으로 이 단계를 고친 회원 (P-04)
 */
public record RoadmapStepResponse(
        UUID stepId,
        String code,
        String costKind,
        Amount estimated,
        BigDecimal actualAmount,
        boolean completed,
        OffsetDateTime completedAt,
        UUID completedBy,
        UUID updatedBy,
        OffsetDateTime updatedAt
) {

    public record Amount(BigDecimal min, BigDecimal max) {
    }
}
