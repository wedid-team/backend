package com.wedid.backend.roadmap.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 단계 완료 · 완료 취소. 금액 단계(예식장 · 스드메)는 실제 계약 금액이 있어야 완료할 수 있다(R-09).
 * 완료를 취소해도 실제 계약 금액은 지우지 않는다.
 */
public record StepCompletionRequest(
        @NotNull(message = "완료 여부를 선택해주세요.")
        Boolean completed
) {
}
