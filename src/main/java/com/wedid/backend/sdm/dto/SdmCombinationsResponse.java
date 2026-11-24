package com.wedid.backend.sdm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 고른 예산대 안의 스드메 조합 최대 3개 (E-20 · D-20). 금액은 모두 원 단위.
 *
 * @param tier              조합을 고른 예산대
 * @param combinations      실속형 · 균형형 · 프리미엄형 순. 조건에 맞는 조합이 없으면 빈 목록
 * @param unavailableReason 조합을 하나도 못 고른 이유. 하나 이상 골랐으면 null
 * @param basis             산출 근거
 */
public record SdmCombinationsResponse(
        Tier tier,
        List<Combination> combinations,
        UnavailableReason unavailableReason,
        Basis basis
) {

    public enum UnavailableReason {
        /** 예산대 경계(Q-05)가 정해지지 않아 범위를 만들 수 없음 */
        TIER_BOUNDARY_PENDING,
        /** 스튜디오 · 드레스 · 메이크업 중 기본가가 있는 업체가 없는 카테고리가 있음 (데이터 미적재 포함) */
        NO_PRICE_DATA,
        /** 예산대 안에 들어오는 조합이 없음 ("조건에 맞는 조합이 없어요") */
        NO_COMBINATION_IN_TIER
    }

    public record Tier(String code, String label, BigDecimal lowerAmount, boolean lowerInclusive,
                       BigDecimal upperAmount, boolean upperInclusive) {
    }

    /**
     * @param type           VALUE(실속형) / BALANCED(균형형) / PREMIUM(프리미엄형)
     * @param totalBasePrice 세 업체 기본가 합. 추가 옵션은 포함하지 않음
     * @param targetAmount   이 유형의 목표 금액 (예산대 안 조합 합계의 하위 25 · 50 · 75% 지점)
     * @param items          스튜디오 · 드레스 · 메이크업 순
     */
    public record Combination(
            String type,
            String typeLabel,
            BigDecimal totalBasePrice,
            BigDecimal targetAmount,
            List<Item> items
    ) {
    }

    /**
     * @param basePrice         업체 기본가 (대표 구성 가격 행의 (최저+최고)/2 중앙값)
     * @param priceRange        화면용 단품가 범위 (대표 구성 가격 행의 최저 ~ 최고)
     * @param priceRowCount     기본가 계산에 쓴 공개 가격 행 수
     * @param publicAgencyCount 이 업체 가격을 공개한 대행사 수
     * @param officialUrl       업체 공식 채널. 수집 데이터에 없어 지금은 null
     * @param sources           가격 출처 (대행사 · 참가격 게시판 URL · 자료 기준)
     */
    public record Item(
            String category,
            Long vendorId,
            String vendorName,
            String productType,
            BigDecimal basePrice,
            PriceRange priceRange,
            int priceRowCount,
            Integer publicAgencyCount,
            String officialUrl,
            List<Source> sources
    ) {
    }

    public record PriceRange(BigDecimal min, BigDecimal max) {
    }

    public record Source(String agencyName, String sourceUrl, String fileReference, LocalDate collectedAt) {
    }

    /**
     * @param priceRule              업체 기본가 · 조합 합계 계산 규칙
     * @param selectionRule          유형별 조합 선정 규칙
     * @param priceBasis             가격 기준 (서울 공개 가격)
     * @param representativeProducts 카테고리별 대표 구성
     * @param candidateVendorCounts  카테고리별 후보 업체 수 (스튜디오 · 드레스 · 메이크업 순)
     * @param combinationCount       만들 수 있는 전체 조합 수
     * @param combinationCountInTier 예산대 안 조합 수
     * @param targets                유형별 목표 금액. 예산대 안 조합이 없으면 빈 목록
     */
    public record Basis(
            String priceRule,
            String selectionRule,
            String priceBasis,
            String extraCostRule,
            List<CategoryValue> representativeProducts,
            List<CategoryCount> candidateVendorCounts,
            long combinationCount,
            long combinationCountInTier,
            List<Target> targets,
            String policyVersion
    ) {
    }

    public record CategoryValue(String category, String value) {
    }

    public record CategoryCount(String category, int count) {
    }

    public record Target(String type, double quantile, BigDecimal amount) {
    }
}
