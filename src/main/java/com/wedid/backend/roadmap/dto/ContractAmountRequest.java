package com.wedid.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 실제 계약 금액 입력 · 수정(원 단위). 0도 값으로 저장한다(db.md 4.29).
 * 완료한 단계도 고칠 수 있다(R-04).
 */
public record ContractAmountRequest(
        @NotNull(message = "실제 계약 금액을 입력해주세요.")
        @PositiveOrZero(message = "실제 계약 금액은 0원 이상이어야 해요.")
        @Max(value = ContractAmountRequest.AMOUNT_MAX, message = "실제 계약 금액은 99,999만 원까지 입력할 수 있어요.")
        Long actualAmount
) {

    /** 예산 입력 최대값(E-06 · Q-10)과 같다. */
    public static final long AMOUNT_MAX = 999_990_000L;
}
