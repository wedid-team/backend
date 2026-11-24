
package com.wedid.backend.vendor.dto;

import java.util.List;

public record HallDetailResponse(
        Long hallId,
        Long vendorId,
        String hallName,
        String vendorName,
        String regionName,
        String address,
        String officialUrl,
        String phone,
        String operatingStatus,
        String mealType,
        String parkingStatus,
        String notes,
        Integer capacityMin,
        Integer capacityMax,
        Long estimatedMinPrice,
        Long estimatedMaxPrice,
        String informationStatus,
        List<String> moodTags,
        List<PriceSourceResponse> priceSources
) {
    public record PriceSourceResponse(
            Long priceRecordId,
            String sourceName,
            String sourceUrl,
            String publishedAt
    ) {}
}
