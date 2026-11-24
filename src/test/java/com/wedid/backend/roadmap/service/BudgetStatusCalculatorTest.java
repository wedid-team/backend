package com.wedid.backend.roadmap.service;

import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.SkipReason;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.Verdict;
import com.wedid.backend.roadmap.service.BudgetStatusCalculator.AmountStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 금액은 테스트용 예시 값이다(원 단위). */
class BudgetStatusCalculatorTest {

    private static final BigDecimal BUDGET = won(30_000_000);

    @Test
    void 쓴_금액은_완료한_금액_단계의_실제_금액만_더한다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                completed(25_000_000),
                // 금액만 넣고 완료하지 않은 단계: 쓴 금액에 넣지 않고 예상 비용으로 비교한다
                step(false, won(9_999_999), won(3_000_000), won(4_000_000))
        ), false);

        assertThat(status.spentAmount()).isEqualByComparingTo("25000000");
        assertThat(status.remainingBudget()).isEqualByComparingTo("5000000");
        assertThat(status.overBudgetAmount()).isEqualByComparingTo("0");
        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("3000000");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("4000000");
        assertThat(status.verdict()).isEqualTo(Verdict.ENOUGH);
        assertThat(status.verdictSkipReason()).isNull();
        assertThat(status.amountStepsCompleted()).isFalse();
    }

    /** E-12와 같은 경계: 상한 ≤ 남은 예산 여유 / 하한 ≤ 남은 예산 &lt; 상한 빠듯 / 하한 &gt; 남은 예산 부족 */
    @ParameterizedTest
    @CsvSource({
            "8000000, ENOUGH",   // 상한과 같으면 여유
            "9000000, ENOUGH",
            "7999999, TIGHT",
            "5000000, TIGHT",    // 하한과 같으면 빠듯
            "4999999, SHORT"
    })
    void 남은_예산과_남은_예상_비용을_E12_기준으로_판정한다(long remainingBudget, Verdict expected) {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(won(remainingBudget), List.of(
                incomplete(2_000_000, 3_000_000),
                incomplete(3_000_000, 5_000_000)
        ), false);

        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("5000000");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("8000000");
        assertThat(status.verdict()).isEqualTo(expected);
    }

    @Test
    void 예산을_넘게_쓰면_넘은_금액을_알려주고_부족으로_판정한다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                completed(32_000_000),
                incomplete(2_000_000, 3_000_000)
        ), false);

        assertThat(status.remainingBudget()).isEqualByComparingTo("-2000000");
        assertThat(status.overBudgetAmount()).isEqualByComparingTo("2000000");
        assertThat(status.verdict()).isEqualTo(Verdict.SHORT);
    }

    @Test
    void 미완료_단계의_예상_비용이_미정이면_합도_미정이고_판정하지_않는다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                incomplete(20_000_000, 30_000_000),
                step(false, null, null, null)   // 스드메 예산대 경계 미확정
        ), false);

        assertThat(status.spentAmount()).isEqualByComparingTo("0");
        assertThat(status.remainingBudget()).isEqualByComparingTo("30000000");
        assertThat(status.remainingEstimate()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.ESTIMATE_PENDING);
        // 합계는 미정이어도 단계별 예상 비용은 그대로 보여 준다
        assertThat(status.amountSteps()).hasSize(2);
        assertThat(status.amountSteps().get(0).estimated().min()).isEqualByComparingTo("20000000");
        assertThat(status.amountSteps().get(0).estimated().max()).isEqualByComparingTo("30000000");
        assertThat(status.amountSteps().get(0).countedAs()).isEqualTo("REMAINING_ESTIMATE");
        assertThat(status.amountSteps().get(1).estimated().min()).isNull();
    }

    @Test
    void 완료한_단계의_예상_비용이_미정인_것은_판정에_영향이_없다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                step(true, won(3_000_000), null, null),
                incomplete(20_000_000, 28_000_000)
        ), false);

        // 남은 예산 2,700만, 남은 예상 2,000만~2,800만

        assertThat(status.verdict()).isEqualTo(Verdict.TIGHT);
    }

    @Test
    void 예산이_없으면_쓴_금액만_알려주고_판정하지_않는다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(null, List.of(
                completed(25_000_000),
                incomplete(3_000_000, 4_000_000)
        ), false);

        assertThat(status.budget()).isNull();
        assertThat(status.spentAmount()).isEqualByComparingTo("25000000");
        assertThat(status.remainingBudget()).isNull();
        assertThat(status.overBudgetAmount()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.NO_BUDGET);
    }

    @Test
    void 금액_단계를_모두_완료하면_판정_대신_완료_안내와_남은_예산을_준다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                completed(22_000_000),
                completed(0)   // 입력한 0원도 값이다
        ), true);

        assertThat(status.amountStepsCompleted()).isTrue();
        assertThat(status.allStepsCompleted()).isTrue();
        assertThat(status.spentAmount()).isEqualByComparingTo("22000000");
        assertThat(status.remainingBudget()).isEqualByComparingTo("8000000");
        assertThat(status.remainingEstimate().min()).isEqualByComparingTo("0");
        assertThat(status.remainingEstimate().max()).isEqualByComparingTo("0");
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.AMOUNT_STEPS_COMPLETED);
    }

    @Test
    void 금액_단계가_하나도_없으면_완료로_보지_않고_판정하지_않는다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(), false);

        assertThat(status.amountStepsCompleted()).isFalse();
        assertThat(status.spentAmount()).isEqualByComparingTo("0");
        assertThat(status.remainingBudget()).isEqualByComparingTo("30000000");
        assertThat(status.remainingEstimate()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.AMOUNT_STEPS_MISSING);
        assertThat(status.amountSteps()).isEmpty();
    }

    @Test
    void 금액_단계가_일부_빠졌으면_있는_단계를_모두_완료해도_완료로_보지_않는다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(BUDGET, List.of(
                completed(22_000_000)
        ), true, false);

        assertThat(status.amountStepsCompleted()).isFalse();
        assertThat(status.spentAmount()).isEqualByComparingTo("22000000");
        assertThat(status.remainingBudget()).isEqualByComparingTo("8000000");
        assertThat(status.remainingEstimate()).isNull();
        assertThat(status.verdict()).isNull();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.AMOUNT_STEPS_MISSING);
    }

    @Test
    void 금액_단계가_빠졌어도_예산이_없으면_예산_미입력이_먼저다() {
        BudgetStatusResponse status = BudgetStatusCalculator.calculate(null, List.of(), false);

        assertThat(status.amountStepsCompleted()).isFalse();
        assertThat(status.verdictSkipReason()).isEqualTo(SkipReason.NO_BUDGET);
    }

    private static AmountStep completed(long actual) {
        return step(true, won(actual), won(1), won(2));
    }

    private static AmountStep incomplete(long min, long max) {
        return step(false, null, won(min), won(max));
    }

    private static AmountStep step(boolean completed, BigDecimal actual, BigDecimal min, BigDecimal max) {
        return new AmountStep(UUID.randomUUID(), "VENUE_CONTRACT", "VENUE", completed, actual, min, max);
    }

    private static BigDecimal won(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
