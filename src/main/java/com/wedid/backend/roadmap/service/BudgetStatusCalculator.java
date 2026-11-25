package com.wedid.backend.roadmap.service;

import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.SkipReason;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse.Verdict;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 남은 예산 판정(R-03 · R-06 · R-09 · R-10). 저장하지 않고 조회할 때마다 계산한다(db.md 5.3).
 * <ul>
 *   <li>지금까지 쓴 금액 = 완료한 금액 단계의 실제 계약 금액 합. 금액만 넣고 완료하지 않은 단계는 넣지 않는다.</li>
 *   <li>남은 예산 = 예산 − 지금까지 쓴 금액 (음수면 예산 초과)</li>
 *   <li>남은 예상 비용 = 완료하지 않은 금액 단계의 예상 비용 합 (하한끼리 · 상한끼리).
 *       하나라도 예상 비용이 미정이면 합도 미정(null)이다. 0으로 보지 않는다.</li>
 *   <li>판정은 E-12와 같은 기준: 상한 ≤ 남은 예산 → 여유 / 하한 ≤ 남은 예산 &lt; 상한 → 빠듯 / 하한 &gt; 남은 예산 → 부족</li>
 * </ul>
 */
public final class BudgetStatusCalculator {

    /** 계산에 필요한 단계 값. 금액 단계(예식장 · 스드메)만 넘긴다. */
    public record AmountStep(
            UUID stepId,
            String code,
            String costKind,
            boolean completed,
            BigDecimal actualAmount,
            BigDecimal estimatedMin,
            BigDecimal estimatedMax
    ) {

        BudgetStatusResponse.AmountStepStatus toStatus() {
            return new BudgetStatusResponse.AmountStepStatus(
                    stepId, code, costKind, completed,
                    new BudgetStatusResponse.Amount(estimatedMin, estimatedMax),
                    actualAmount,
                    completed ? "SPENT" : "REMAINING_ESTIMATE");
        }
    }

    private BudgetStatusCalculator() {
    }

    /** 계획에 금액 단계가 모두 있을 때. 목록이 비어 있으면 빠진 것으로 본다. */
    public static BudgetStatusResponse calculate(BigDecimal budget, List<AmountStep> amountSteps, boolean allStepsCompleted) {
        return calculate(budget, amountSteps, false, allStepsCompleted);
    }

    /**
     * @param budget             예산(원). 입력하지 않았으면 null
     * @param amountSteps        금액 단계 목록
     * @param amountStepsMissing 계획에 있어야 할 금액 단계(예식장 · 스드메) 중 빠진 것이 있는지.
     *                           목록이 비어 있어도 빠진 것으로 본다. 빠졌으면 완료로 보지 않고 판정도 하지 않는다
     * @param allStepsCompleted  6단계를 모두 완료했는지 (R-09 "모든 준비를 마쳤어요")
     */
    public static BudgetStatusResponse calculate(
            BigDecimal budget,
            List<AmountStep> amountSteps,
            boolean amountStepsMissing,
            boolean allStepsCompleted
    ) {
        boolean missing = amountStepsMissing || amountSteps.isEmpty();
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal remainingMin = BigDecimal.ZERO;
        BigDecimal remainingMax = BigDecimal.ZERO;
        // 빠진 단계의 예상 비용은 알 수 없으니 합도 미정이다
        boolean estimateKnown = !missing;
        boolean amountStepsCompleted = !missing;

        for (AmountStep step : amountSteps) {
            if (step.completed()) {
                // 금액 단계는 실제 금액이 있어야 완료되지만, 직접 고친 데이터에 대비해 null은 건너뛴다
                if (step.actualAmount() != null) {
                    spent = spent.add(step.actualAmount());
                }
                continue;
            }
            amountStepsCompleted = false;
            if (step.estimatedMin() == null || step.estimatedMax() == null) {
                estimateKnown = false;
            } else {
                remainingMin = remainingMin.add(step.estimatedMin());
                remainingMax = remainingMax.add(step.estimatedMax());
            }
        }

        List<BudgetStatusResponse.AmountStepStatus> stepStatuses = amountSteps.stream()
                .map(AmountStep::toStatus)
                .toList();
        BudgetStatusResponse.Amount remainingEstimate = estimateKnown
                ? new BudgetStatusResponse.Amount(remainingMin, remainingMax)
                : null;

        if (budget == null) {
            return new BudgetStatusResponse(null, spent, null, null, remainingEstimate,
                    null, SkipReason.NO_BUDGET, amountStepsCompleted, allStepsCompleted, stepStatuses);
        }

        BigDecimal remainingBudget = budget.subtract(spent);
        BigDecimal overBudget = remainingBudget.signum() < 0 ? remainingBudget.negate() : BigDecimal.ZERO;

        Verdict verdict = null;
        SkipReason skipReason;
        if (missing) {
            skipReason = SkipReason.AMOUNT_STEPS_MISSING;
        } else if (amountStepsCompleted) {
            skipReason = SkipReason.AMOUNT_STEPS_COMPLETED;
        } else if (remainingEstimate == null) {
            skipReason = SkipReason.ESTIMATE_PENDING;
        } else {
            skipReason = null;
            verdict = judge(remainingBudget, remainingEstimate);
        }

        return new BudgetStatusResponse(budget, spent, remainingBudget, overBudget, remainingEstimate,
                verdict, skipReason, amountStepsCompleted, allStepsCompleted, stepStatuses);
    }

    /** E-12와 같은 기준. */
    static Verdict judge(BigDecimal remainingBudget, BudgetStatusResponse.Amount remainingEstimate) {
        if (remainingEstimate.max().compareTo(remainingBudget) <= 0) {
            return Verdict.ENOUGH;
        }
        if (remainingEstimate.min().compareTo(remainingBudget) <= 0) {
            return Verdict.TIGHT;
        }
        return Verdict.SHORT;
    }
}
