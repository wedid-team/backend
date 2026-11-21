
package com.wedid.backend.recommendation.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationScoreCalculatorTest {

    private final RecommendationScoreCalculator calculator =
            new RecommendationScoreCalculator();

    @Test
    void matchingMoodShouldReturn25() {
        int score = calculator.calculateMoodScore(
                Set.of("BRIGHT"),
                Set.of("BRIGHT", "CLASSIC")
        );

        assertEquals(25, score);
    }

    @Test
    void missingHallMoodShouldReturn8() {
        int score = calculator.calculateMoodScore(
                Set.of("BRIGHT"),
                Set.of()
        );

        assertEquals(8, score);
    }

    @Test
    void mismatchedMoodShouldReturn0() {
        int score = calculator.calculateMoodScore(
                Set.of("BRIGHT"),
                Set.of("GRAND")
        );

        assertEquals(0, score);
    }

    @Test
    void noPreferenceShouldExcludeMoodScoring() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateMoodScore(
                        Set.of(),
                        Set.of("BRIGHT")
                )
        );
    }

    @Test
    void fullyAffordableBudgetShouldReturn40() {
        double score = calculator.calculateBudgetScore(
                20_000_000L, 28_000_000L, 30_000_000L
        );

        assertEquals(40.0, score, 0.0001);
    }

    @Test
    void partiallyAffordableBudgetShouldReturn30() {
        double score = calculator.calculateBudgetScore(
                25_000_000L, 35_000_000L, 30_000_000L
        );

        assertEquals(30.0, score, 0.0001);
    }

    @Test
    void overBudgetShouldReturn12() {
        double score = calculator.calculateBudgetScore(
                33_000_000L, 38_000_000L, 30_000_000L
        );

        assertEquals(12.0, score, 0.0001);
    }

    @Test
    void significantlyOverBudgetShouldReturn0() {
        double score = calculator.calculateBudgetScore(
                40_000_000L, 45_000_000L, 30_000_000L
        );

        assertEquals(0.0, score, 0.0001);
    }

    @Test
    void missingBudgetShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateBudgetScore(
                        20_000_000L, 30_000_000L, null
                )
        );
    }

    @Test
    void zeroBudgetShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateBudgetScore(
                        20_000_000L, 30_000_000L, 0L
                )
        );
    }

    @Test
    void invalidCostRangeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateBudgetScore(
                        35_000_000L, 25_000_000L, 30_000_000L
                )
        );
    }

    @Test
    void equalMinMaxCostShouldBeHandled() {
        assertEquals(
                40.0,
                calculator.calculateBudgetScore(
                        30_000_000L, 30_000_000L, 30_000_000L
                ),
                0.0001
        );
    }

    @Test
    void cheapestHallShouldReturn25() {
        double score = calculator.calculatePriceScore(
                15_000_000L, 15_000_000L,
                15_000_000.0, 25_000_000.0
        );

        assertEquals(25.0, score, 0.0001);
    }

    @Test
    void middlePricedHallShouldReturn12Point5() {
        double score = calculator.calculatePriceScore(
                20_000_000L, 20_000_000L,
                15_000_000.0, 25_000_000.0
        );

        assertEquals(12.5, score, 0.0001);
    }

    @Test
    void mostExpensiveHallShouldReturn0() {
        double score = calculator.calculatePriceScore(
                25_000_000L, 25_000_000L,
                15_000_000.0, 25_000_000.0
        );

        assertEquals(0.0, score, 0.0001);
    }

    @Test
    void nearlyEqualPricesShouldReturn25() {
        double score = calculator.calculatePriceScore(
                20_000_000L, 20_000_000L,
                20_000_000.0, 20_100_000.0
        );

        assertEquals(25.0, score, 0.0001);
    }

    @Test
    void equalPricesShouldReturn25() {
        double score = calculator.calculatePriceScore(
                20_000_000L, 20_000_000L,
                20_000_000.0, 20_000_000.0
        );

        assertEquals(25.0, score, 0.0001);
    }

    @Test
    void invalidHallPriceRangeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculatePriceScore(
                        30_000_000L, 20_000_000L,
                        15_000_000.0, 25_000_000.0
                )
        );
    }

    @Test
    void hallOutsideComparisonRangeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculatePriceScore(
                        30_000_000L, 30_000_000L,
                        15_000_000.0, 25_000_000.0
                )
        );
    }

    @Test
    void suitableGuestSizeShouldReturn10() {
        double score = calculator.calculateSizeScore(
                100, 150, 100
        );

        assertEquals(10.0, score, 0.0001);
    }

    @Test
    void equalGuestMidAndGuaranteeShouldReturn10() {
        double score = calculator.calculateSizeScore(
                100, 150, 125
        );

        assertEquals(10.0, score, 0.0001);
    }

    @Test
    void higherGuaranteeShouldReturn6Point25() {
        double score = calculator.calculateSizeScore(
                100, 150, 200
        );

        assertEquals(6.25, score, 0.0001);
    }

    @Test
    void zeroGuaranteeShouldReturn10() {
        double score = calculator.calculateSizeScore(
                100, 150, 0
        );

        assertEquals(10.0, score, 0.0001);
    }

    @Test
    void unknownGuestRangeShouldUse175() {
        // 하객 수 미정 → 150~200명
        double score = calculator.calculateSizeScore(
                150, 200, 250
        );

        assertEquals(7.0, score, 0.0001);
    }

    @Test
    void over300GuestsShouldUse350() {
        // 300명 이상 → 300~400명
        double score = calculator.calculateSizeScore(
                300, 400, 400
        );

        assertEquals(8.75, score, 0.0001);
    }

    @Test
    void missingGuaranteeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateSizeScore(
                        100, 150, null
                )
        );
    }

    @Test
    void invalidGuestRangeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateSizeScore(
                        150, 100, 120
                )
        );
    }

    @Test
    void negativeGuaranteeShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateSizeScore(
                        100, 150, -1
                )
        );
    }

    @Test
    void allScoresShouldSumTo100() {
        double score = calculator.calculateTotalScore(
                40.0, 25, 25.0, 10.0
        );

        assertEquals(100.0, score, 0.0001);
    }

    @Test
    void missingBudgetShouldRedistributeWeights() {
        double score = calculator.calculateTotalScore(
                null, 25, 20.0, 10.0
        );

        assertEquals(91.6667, score, 0.0001);
    }

    @Test
    void missingMoodShouldRedistributeWeights() {
        double score = calculator.calculateTotalScore(
                30.0, null, 20.0, 10.0
        );

        assertEquals(80.0, score, 0.0001);
    }

    @Test
    void missingBudgetAndMoodShouldRedistributeWeights() {
        double score = calculator.calculateTotalScore(
                null, null, 20.0, 10.0
        );

        assertEquals(85.7143, score, 0.0001);
    }

    @Test
    void singleCandidateShouldExcludePriceScore() {
        double score = calculator.calculateTotalScore(
                40.0, 25, null, 10.0
        );

        assertEquals(100.0, score, 0.0001);
    }

    @Test
    void allScoresMissingShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateTotalScore(
                        null, null, null, null
                )
        );
    }

    @Test
    void invalidBudgetScoreShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateTotalScore(
                        45.0, 25, 20.0, 10.0
                )
        );
    }

    @Test
    void invalidMoodScoreShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateTotalScore(
                        30.0, 30, 20.0, 10.0
                )
        );
    }

    @Test
    void invalidPriceScoreShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateTotalScore(
                        30.0, 25, 30.0, 10.0
                )
        );
    }

    @Test
    void invalidSizeScoreShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateTotalScore(
                        30.0, 25, 20.0, 15.0
                )
        );
    }




}
