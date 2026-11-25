package com.wedid.backend.plan.entity;

import com.wedid.backend.estimate.dto.EstimateRequest.WeddingDateType;

/** plans.date_precision 저장값. API의 예식 시기 유형(UNDECIDED)은 DB에서 UNKNOWN으로 저장한다. */
public enum DatePrecision {
    EXACT, MONTH, UNKNOWN;

    public static DatePrecision from(WeddingDateType type) {
        return switch (type) {
            case EXACT -> EXACT;
            case MONTH -> MONTH;
            case UNDECIDED -> UNKNOWN;
        };
    }

    public WeddingDateType toWeddingDateType() {
        return switch (this) {
            case EXACT -> WeddingDateType.EXACT;
            case MONTH -> WeddingDateType.MONTH;
            case UNKNOWN -> WeddingDateType.UNDECIDED;
        };
    }
}
