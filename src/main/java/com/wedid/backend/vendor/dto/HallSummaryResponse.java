
package com.wedid.backend.vendor.dto;

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
        Long estimatedMinPrice,
        Long estimatedMaxPrice,
        String informationStatus,
        Double recommendationScore
) {
}
