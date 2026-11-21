
package com.wedid.backend.recommendation.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.HashSet;

@Component
public class RecommendationScoreCalculator {

    public int calculateMoodScore(
            Set<String> preferredMoods,
            Set<String> hallMoods
    ) {
        // '상관없어요' 또는 미선택은 평가 제외
        if (preferredMoods == null || preferredMoods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mood scoring is excluded when no preference is selected"
            );
        }

        // 웨딩홀 분위기 정보 없음: 8점
        if (hallMoods == null || hallMoods.isEmpty()) {
            return 8;
        }

        // 사용자와 웨딩홀 태그 중복 제거 및 비교
        Set<String> uniquePreferred = new HashSet<>(preferredMoods);
        Set<String> uniqueHall = new HashSet<>(hallMoods);

        uniquePreferred.retainAll(uniqueHall);

        // 하나라도 일치하면 25점
        return uniquePreferred.isEmpty() ? 0 : 25;
    }

    public double calculateBudgetScore(
            long totalMin,
            long totalMax,
            Long budget
    ) {
        if (budget == null) {
            throw new IllegalArgumentException(
                    "Budget scoring is excluded when no budget is provided"
            );
        }

        if (budget <= 0) {
            throw new IllegalArgumentException("Budget must be positive");
        }

        if (totalMin < 0 || totalMax < totalMin) {
            throw new IllegalArgumentException("Invalid total cost range");
        }

        // 완전 적합: 최대 예상 비용까지 예산 이내
        if (totalMax <= budget) {
            return 40.0;
        }

        // 부분 적합: 최소 비용은 예산 이내, 최대 비용은 초과
        if (totalMin <= budget) {
            return 20.0 + 20.0
                    * (budget - totalMin)
                    / (double) (totalMax - totalMin);
        }

        // 예산 초과: 초과율에 따라 0~20점
        double alpha = 0.25;

        return 20.0 * Math.max(
                0.0,
                1.0 - (totalMin - (double) budget) / (alpha * budget)
        );
    }

    public double calculatePriceScore(
            long hallMin,
            long hallMax,
            double lowestPrice,
            double highestPrice
    ) {
        if (hallMin < 0 || hallMax < hallMin) {
            throw new IllegalArgumentException("Invalid hall price range");
        }

        if (lowestPrice < 0 || highestPrice < lowestPrice) {
            throw new IllegalArgumentException("Invalid comparison price range");
        }

        double hallMid = hallMin / 2.0 + hallMax / 2.0;

        if (hallMid < lowestPrice || hallMid > highestPrice) {
            throw new IllegalArgumentException(
                    "Hall midpoint is outside comparison price range"
            );
        }

        // 후보 가격이 동일하거나 가격 차이가 1% 이하인 경우
        if (lowestPrice == highestPrice ||
                (lowestPrice > 0 &&
                        (highestPrice - lowestPrice) / lowestPrice <= 0.01)) {
            return 25.0;
        }

        return 25.0 * (highestPrice - hallMid)
                / (highestPrice - lowestPrice);
    }

    public double calculateSizeScore(
            int minGuests,
            int maxGuests,
            Integer guaranteedGuests
    ) {
        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException("Invalid guest range");
        }

        if (guaranteedGuests == null) {
            throw new IllegalArgumentException(
                    "Missing guaranteed guests: insufficient hall information"
            );
        }

        if (guaranteedGuests < 0) {
            throw new IllegalArgumentException("Invalid guaranteed guests");
        }

        // 최소 보증 인원이 0이면 제한 없음
        if (guaranteedGuests == 0) {
            return 10.0;
        }

        // 하객 구간 중앙값
        double guestMid = minGuests / 2.0 + maxGuests / 2.0;

        // 하객 중앙값이 최소 보증 인원 이상
        if (guestMid >= guaranteedGuests) {
            return 10.0;
        }

        // 최소 보증 인원이 더 많은 경우
        return 10.0 * guestMid / guaranteedGuests;
    }

    public double calculateTotalScore(
            Double budgetScore,
            Integer moodScore,
            Double priceScore,
            Double sizeScore
    ) {
        double earned = 0.0;
        double maximum = 0.0;

        if (budgetScore != null) {
            if (!Double.isFinite(budgetScore)
                    || budgetScore < 0 || budgetScore > 40) {
                throw new IllegalArgumentException("Invalid budget score");
            }
            earned += budgetScore;
            maximum += 40;
        }

        if (moodScore != null) {
            if (moodScore < 0 || moodScore > 25) {
                throw new IllegalArgumentException("Invalid mood score");
            }
            earned += moodScore;
            maximum += 25;
        }

        if (priceScore != null) {
            if (!Double.isFinite(priceScore)
                    || priceScore < 0 || priceScore > 25) {
                throw new IllegalArgumentException("Invalid price score");
            }
            earned += priceScore;
            maximum += 25;
        }

        if (sizeScore != null) {
            if (!Double.isFinite(sizeScore)
                    || sizeScore < 0 || sizeScore > 10) {
                throw new IllegalArgumentException("Invalid size score");
            }
            earned += sizeScore;
            maximum += 10;
        }

        if (maximum == 0) {
            throw new IllegalArgumentException("No applicable scores");
        }

        return earned / maximum * 100.0;
    }




}
