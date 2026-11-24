package com.wedid.backend.roadmap.dto;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 준비 로드맵 전체(SCR-06 전체 로드맵 · SCR-06-D 단계 상세). 금액은 원 단위, 알 수 없는 값은 null.
 * 지역 · 하객 수 · 분위기 · 예산 · 스드메 예산대는 GET /plans/me, 쓴 금액 · 남은 예산 판정은 GET /plans/me/budget-status에서 받는다.
 *
 * @param wedding    D-day 기준 예식일 (R-08)
 * @param nextStepId "다음 할 일" = 가장 이른 미완료 단계 (R-02). 모두 완료했으면 null
 * @param steps      6단계, 로드맵 순서. 단계마다 할 일 목록을 담는다
 * @param me         조회한 사람
 * @param partner    짝꿍. 연결되지 않았으면 null
 */
public record RoadmapResponse(
        UUID planId,
        Wedding wedding,
        UUID nextStepId,
        int completedStepCount,
        List<Step> steps,
        Member me,
        Member partner,
        OffsetDateTime scheduleUpdatedAt
) {

    /**
     * @param effectiveWeddingDate D-day 기준 날짜. 월만 정했으면 그 달 1일, 미정이면 처음 저장한 날 + 12개월
     * @param estimatedDate        true면 D-day에 "(예상)" (R-08 · E-04)
     * @param dDay                 오늘(Asia/Seoul)부터 예식일까지 남은 일수. 지났으면 음수
     */
    public record Wedding(
            WeddingDateType weddingDateType,
            LocalDate effectiveWeddingDate,
            boolean estimatedDate,
            long dDay
    ) {
    }

    /**
     * @param shortTitle      "전체 일정" 막대용 짧은 이름. 템플릿 데이터가 없어 지금은 null
     * @param daysBeforeStart 권장 시기 시작 (D-n)
     * @param daysBeforeEnd   권장 시기 끝 (D-n)
     * @param estimated       단계 예상 비용. 금액 단계(VENUE · SDM)가 아니면 null, 미정이면 min · max가 null
     * @param overdue         권장 기간이 지났는데 완료하지 않음 ("시기가 지났어요" 태그, R-07)
     * @param overdueReviewed 지금 예식 일정 기준으로 지난 단계 확인 모달을 이미 처리함. 처리 API가 아직 없어 지금은 항상 false
     * @param decisionItems   "정해야 할 것". 템플릿 콘텐츠 미정이라 지금은 []
     * @param guidanceItems   "알아두면 좋은 점". 템플릿 콘텐츠 미정이라 지금은 []
     * @param costNote        지난 단계 팝업의 비용 안내. 템플릿 데이터가 없어 지금은 null
     */
    public record Step(
            UUID stepId,
            String code,
            String title,
            String shortTitle,
            int sortOrder,
            String costKind,
            int daysBeforeStart,
            int daysBeforeEnd,
            LocalDate recommendedStartDate,
            LocalDate recommendedEndDate,
            Amount estimated,
            BigDecimal actualAmount,
            boolean completed,
            OffsetDateTime completedAt,
            UUID completedBy,
            boolean overdue,
            boolean overdueReviewed,
            List<String> decisionItems,
            List<String> guidanceItems,
            String costNote,
            List<TodoResponse> todos,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
    }

    public record Amount(BigDecimal min, BigDecimal max) {
    }

    /**
     * @param displayName   소셜 로그인 프로필 이름 (C-06). 동의 항목에 따라 null
     * @param role          OWNER(계획을 만든 사람) / PARTNER(합류한 짝꿍)
     * @param loginProvider KAKAO / GOOGLE ("구글로 합류" 표시용). 연결 계정이 없으면 null
     */
    public record Member(
            UUID userId,
            String displayName,
            String role,
            String loginProvider,
            OffsetDateTime joinedAt
    ) {
    }
}
