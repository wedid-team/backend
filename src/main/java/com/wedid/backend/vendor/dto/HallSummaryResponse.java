
package com.wedid.backend.vendor.dto;

import java.util.List;

public record HallSummaryResponse(
        Long hallId,
        Long vendorId,
        String hallName,
        String vendorName,
        String regionName,
        String address,
        String officialUrl,
        Integer capacityMin,
        Integer capacityMax,

        // 대관료 범위
        Long rentalMinPrice,
        Long rentalMaxPrice,

        // 1인당 식대 범위
        Long mealMinPrice,
        Long mealMaxPrice,

        // 예식장 상세 정보
        List<String> moodTags,
        String parkingStatus,
        String mealType,

        // 예상 총비용
        Long estimatedMinPrice,
        Long estimatedMaxPrice,

        // 정보 상태 및 추천 점수
        String informationStatus,
        List<String> insufficientReasons,
        Double recommendationScore
) {
}
