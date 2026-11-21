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
        Integer capacityMin,
        Integer capacityMax,
        Long estimatedMinPrice,
        Long estimatedMaxPrice,
        String informationStatus,
        List<String> moodTags,
        String priceSourceUrl,
        String pricePublishedAt
) {
}