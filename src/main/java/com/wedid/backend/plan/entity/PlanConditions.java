package com.wedid.backend.plan.entity;

import java.math.BigDecimal;
import java.util.List;

/** 계획에 저장하는 사용자 조건. 같은 값으로 계산 기록(estimate_runs)도 만든다. */
public record PlanConditions(
        Short regionId,
        GuestChoice guestChoice,
        PlanSchedule schedule,
        BigDecimal budgetAmount,
        List<String> preferredMoods,
        Long selectedBandId
) {
    public PlanConditions {
        preferredMoods = List.copyOf(preferredMoods);
    }
}
