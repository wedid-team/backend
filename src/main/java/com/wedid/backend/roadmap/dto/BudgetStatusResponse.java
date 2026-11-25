package com.wedid.backend.roadmap.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 남은 예산 판정(R-03). 금액은 모두 원 단위이며, 알 수 없는 값은 0이 아니라 null이다.
 *
 * @param budget               계획의 예산. 입력하지 않았으면 null (R-06)
 * @param spentAmount          지금까지 쓴 금액 = 완료한 금액 단계의 실제 계약 금액 합. 완료한 단계가 없으면 0
 * @param remainingBudget      예산 − 지금까지 쓴 금액. 음수면 예산 초과. 예산이 없으면 null
 * @param overBudgetAmount     예산을 넘은 금액 (넘지 않았으면 0). "예산을 ○○만 원 넘었어요" (R-09). 예산이 없으면 null
 * @param remainingEstimate    완료하지 않은 금액 단계의 예상 비용 합. 금액 단계를 모두 완료했으면 0~0,
 *                             미완료 단계 중 예상 비용이 미정인 단계가 있거나 금액 단계가 빠졌으면 null
 * @param verdict              여유 / 빠듯 / 부족. 판정하지 않으면 null이고 이유는 verdictSkipReason
 * @param verdictSkipReason    판정하지 않은 이유. 판정했으면 null
 * @param amountStepsCompleted 예식장 · 스드메 단계가 모두 있고 모두 완료했는지. true면 판정 대신
 *                             "예식장 · 스드메 계약을 모두 마쳤어요 · 남은 예산 ○○만 원" (R-10)
 * @param allStepsCompleted    6단계를 모두 완료했는지. true면 "모든 준비를 마쳤어요" (R-09)
 * @param amountSteps          금액 단계(예식장 · 스드메)별 예상 비용 · 실제 금액. 합계가 미정이어도 단계별 값은 볼 수 있다
 */
public record BudgetStatusResponse(
        BigDecimal budget,
        BigDecimal spentAmount,
        BigDecimal remainingBudget,
        BigDecimal overBudgetAmount,
        Amount remainingEstimate,
        Verdict verdict,
        SkipReason verdictSkipReason,
        boolean amountStepsCompleted,
        boolean allStepsCompleted,
        List<AmountStepStatus> amountSteps
) {

    /** 판정 기준은 E-12와 같다. 화면 배지 문구는 Q-24(제안)를 따른다. */
    public enum Verdict {
        /** 여유: 남은 예상 비용 상한 ≤ 남은 예산 */
        ENOUGH,
        /** 빠듯: 하한 ≤ 남은 예산 &lt; 상한 */
        TIGHT,
        /** 부족: 하한 &gt; 남은 예산 */
        SHORT
    }

    public enum SkipReason {
        /** 예산 미입력: "남은 예산" 대신 "지금까지 쓴 금액"만 표시 (R-06) */
        NO_BUDGET,
        /** 예식장 · 스드메를 모두 완료: 판정 대신 완료 안내 + 남은 예산 (R-10) */
        AMOUNT_STEPS_COMPLETED,
        /** 계획에 예식장 · 스드메 단계가 없거나 일부 빠짐 (단계 데이터 누락). 완료로 보지 않고 판정하지 않는다 */
        AMOUNT_STEPS_MISSING,
        /** 완료하지 않은 금액 단계의 예상 비용이 미정 (예: 스드메 예산대 경계 미확정, Q-05) */
        ESTIMATE_PENDING
    }

    public record Amount(BigDecimal min, BigDecimal max) {
    }

    /**
     * 금액 단계 하나의 금액.
     *
     * @param estimated    단계 예상 비용. 미정이면 min · max가 null (스드메는 Q-05 확정 전까지 미정)
     * @param actualAmount 실제 계약 금액. 입력하지 않았으면 null
     * @param countedAs    합계에 들어간 곳: SPENT(완료 → 쓴 금액) / REMAINING_ESTIMATE(미완료 → 남은 예상 비용)
     */
    public record AmountStepStatus(
            UUID stepId,
            String code,
            String costKind,
            boolean completed,
            Amount estimated,
            BigDecimal actualAmount,
            String countedAs
    ) {
    }
}
