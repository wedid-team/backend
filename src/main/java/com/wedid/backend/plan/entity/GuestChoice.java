package com.wedid.backend.plan.entity;

import java.util.Arrays;

/**
 * plans.guest_choice 저장값. API는 예상 비용 계산과 같은 하객 수 코드(guestRangeCode)를 쓰고,
 * DB에는 db.md의 코드로 저장한다.
 */
public enum GuestChoice {
    LT100("UNDER_100"),
    G100_150("FROM_100_TO_150"),
    G150_200("FROM_150_TO_200"),
    G200_250("FROM_200_TO_250"),
    G250_300("FROM_250_TO_300"),
    GE300("OVER_300"),
    UNKNOWN("UNDECIDED");

    private final String rangeCode;

    GuestChoice(String rangeCode) {
        this.rangeCode = rangeCode;
    }

    public String rangeCode() {
        return rangeCode;
    }

    public static GuestChoice fromRangeCode(String rangeCode) {
        return Arrays.stream(values())
                .filter(choice -> choice.rangeCode.equals(rangeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("올바른 하객 수를 선택해주세요."));
    }
}
