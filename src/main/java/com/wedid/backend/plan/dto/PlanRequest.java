package com.wedid.backend.plan.dto;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 계획 저장 · 덮어쓰기 요청. 조건 필드는 예상 비용 계산 요청(EstimateRequest)과 같은 코드를 쓴다.
 *
 * @param budget      예식장 + 스드메 예산, 원 단위. 미입력은 null (E-05 · E-06)
 * @param moods       선호 분위기. [] 또는 ["ANY"]는 "상관없어요"
 * @param sdmBandCode 스드메 예산대. 생략하면 새 계획은 정책 기본값, 기존 계획은 지금 예산대를 유지
 */
public record PlanRequest(
        @NotBlank
        String regionCode,

        @NotBlank
        String guestRangeCode,

        @NotNull
        WeddingDateType weddingDateType,

        LocalDate weddingDate,
        Integer weddingYear,
        Integer weddingMonth,

        Long budget,

        List<String> moods,

        String sdmBandCode
) {

    public static final long BUDGET_MIN = 1_000_000L;
    public static final long BUDGET_MAX = 999_990_000L;
    public static final long BUDGET_UNIT = 10_000L;

    private static final Set<String> SDM_BAND_CODES = Set.of("BASIC", "STANDARD", "PREMIUM", "LUXURY");

    /** 지역 · 하객 수 · 예식 시기 · 분위기 검증은 예상 비용 계산과 같은 규칙을 쓴다. */
    public EstimateRequest toEstimateConditions() {
        return new EstimateRequest(
                regionCode, guestRangeCode, weddingDateType,
                weddingDate, weddingYear, weddingMonth,
                budget, moods
        );
    }

    /** "상관없어요"(ANY)는 저장하지 않고 빈 배열로 둔다. */
    public List<String> normalizedMoods() {
        if (moods == null) {
            return List.of();
        }
        return moods.stream().filter(mood -> !"ANY".equals(mood)).toList();
    }

    @AssertTrue(message = "올바른 지역을 선택해주세요.")
    public boolean isValidRegion() {
        return toEstimateConditions().isValidRegion();
    }

    @AssertTrue(message = "올바른 하객 수를 선택해주세요.")
    public boolean isValidGuestRange() {
        return toEstimateConditions().isValidGuestRange();
    }

    @AssertTrue(message = "예식 시기 입력을 확인해주세요.")
    public boolean isValidWeddingDate() {
        return toEstimateConditions().isValidWeddingDate();
    }

    @AssertTrue(message = "분위기 선택을 확인해주세요.")
    public boolean isValidMoods() {
        return toEstimateConditions().isValidMoods();
    }

    @AssertTrue(message = "예산은 100만 원 이상 99,999만 원 이하, 만 원 단위로 입력해주세요.")
    public boolean isValidBudget() {
        return budget == null
                || (budget >= BUDGET_MIN && budget <= BUDGET_MAX && budget % BUDGET_UNIT == 0);
    }

    @AssertTrue(message = "올바른 스드메 예산대를 선택해주세요.")
    public boolean isValidSdmBandCode() {
        return sdmBandCode == null || SDM_BAND_CODES.contains(sdmBandCode);
    }
}
