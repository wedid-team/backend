
package com.wedid.backend.estimate.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record EstimateRequest(

        @NotBlank
        String regionCode,

        @NotBlank
        String guestRangeCode,

        @NotNull
        WeddingDateType weddingDateType,

        LocalDate weddingDate,
        Integer weddingYear,
        Integer weddingMonth,

        @PositiveOrZero
        Long budget,

        List<String> moods
) {

    public enum WeddingDateType {
        EXACT, MONTH, UNDECIDED
    }

    private static final Set<String> REGIONS = Set.of(
            "SEOUL_GANGNAM", "SEOUL_OTHER",
            "GYEONGGI", "INCHEON", "BUSAN",
            "DAEGU", "JEONNAM_GWANGJU",
            "DAEJEON", "ULSAN", "GANGWON",
            "CHUNGCHEONG", "JEONBUK",
            "GYEONGSANG", "JEJU"
    );

    private static final Set<String> GUEST_RANGES = Set.of(
            "UNDER_100", "FROM_100_TO_150",
            "FROM_150_TO_200", "FROM_200_TO_250",
            "FROM_250_TO_300", "OVER_300",
            "UNDECIDED"
    );

    private static final Set<String> MOODS = Set.of(
            "BRIGHT", "GRAND", "CLASSIC",
            "GREENERY", "LUXURY", "ANY"
    );

    @AssertTrue(message = "올바른 지역을 선택해주세요.")
    public boolean isValidRegion() {
        return regionCode == null || REGIONS.contains(regionCode);
    }

    @AssertTrue(message = "올바른 하객 수를 선택해주세요.")
    public boolean isValidGuestRange() {
        return guestRangeCode == null
                || GUEST_RANGES.contains(guestRangeCode);
    }

    @AssertTrue(message = "예식 시기 입력을 확인해주세요.")
    public boolean isValidWeddingDate() {
        if (weddingDateType == null) return true;

        return switch (weddingDateType) {
            case EXACT ->
                    weddingDate != null
                            && weddingYear == null
                            && weddingMonth == null;

            case MONTH ->
                    weddingDate == null
                            && weddingYear != null
                            && weddingMonth != null
                            && weddingMonth >= 1
                            && weddingMonth <= 12;

            case UNDECIDED ->
                    weddingDate == null
                            && weddingYear == null
                            && weddingMonth == null;
        };
    }

    @AssertTrue(message = "분위기 선택을 확인해주세요.")
    public boolean isValidMoods() {
        if (moods == null || moods.isEmpty()) return true;

        return moods.stream().allMatch(MOODS::contains)
                && moods.size() == moods.stream().distinct().count()
                && !(moods.contains("ANY") && moods.size() > 1);
    }
}
