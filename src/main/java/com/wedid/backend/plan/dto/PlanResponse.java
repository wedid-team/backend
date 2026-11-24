package com.wedid.backend.plan.dto;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import com.wedid.backend.estimate.entity.EstimateRun.CalculationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 내 계획(준비 공간). 금액은 모두 원 단위이며, 계산할 수 없거나 미정이면 null이다.
 *
 * @param role      조회한 사람의 역할 (OWNER: 계획을 만든 사람 / PARTNER: 합류한 짝꿍)
 * @param updatedBy 마지막으로 조건 · 단계를 고친 회원 (P-04 "마지막 수정: 짝꿍 · 날짜")
 */
public record PlanResponse(
        UUID planId,
        String role,
        Conditions conditions,
        SdmBand sdmBand,
        Estimate estimate,
        List<Step> steps,
        OffsetDateTime conditionsUpdatedAt,
        OffsetDateTime scheduleUpdatedAt,
        UUID updatedBy,
        OffsetDateTime updatedAt
) {

    /**
     * @param effectiveWeddingDate 로드맵 · D-day 기준 날짜. 월만 정했으면 그 달 1일, 미정이면 기준일 + 12개월
     * @param estimatedDate        true면 D-day에 "(예상)" 표시 (R-08 · E-04)
     */
    public record Conditions(
            String regionCode,
            String regionName,
            String guestRangeCode,
            WeddingDateType weddingDateType,
            LocalDate weddingDate,
            Integer weddingYear,
            Integer weddingMonth,
            LocalDate effectiveWeddingDate,
            boolean estimatedDate,
            BigDecimal budget,
            List<String> moods
    ) {
    }

    /** 선택한 스드메 예산대. 경계(Q-05)가 확정되기 전에는 금액이 null이다. */
    public record SdmBand(
            String code,
            String label,
            BigDecimal lowerAmount,
            BigDecimal upperAmount
    ) {
    }

    /**
     * 현재 계산 결과. 스드메 금액이 확정되기 전에는 sdm · total이 null이고 calculationStatus는 PARTIAL,
     * 예식장도 계산할 수 없으면 UNAVAILABLE.
     */
    public record Estimate(
            UUID estimateId,
            CalculationStatus calculationStatus,
            OffsetDateTime calculatedAt,
            Venue venue,
            Amount sdm,
            Amount total
    ) {
    }

    /** sourceType: HALL_DATA(수집 예식장 가격) / MARKET_STATISTICS(공공 통계, E-18) */
    public record Venue(
            BigDecimal min,
            BigDecimal max,
            String sourceType,
            String referenceMonth,
            String sourceRegion,
            String sourceUrl,
            Integer sampleCount
    ) {
    }

    public record Amount(BigDecimal min, BigDecimal max) {
    }

    /**
     * 로드맵 단계 요약. 금액 단계(VENUE · SDM)만 예상 비용 · 실제 계약 금액을 가진다.
     * 완료한 단계의 예상 비용은 조건을 바꿔도 유지된다(P-02).
     */
    public record Step(
            UUID stepId,
            String code,
            String title,
            int sortOrder,
            String costKind,
            LocalDate recommendedStartDate,
            LocalDate recommendedEndDate,
            Amount estimated,
            BigDecimal actualAmount,
            boolean completed,
            OffsetDateTime completedAt
    ) {
    }
}
