
package com.wedid.backend.vendor.dto;

public record MarketPriceResponse(
        String regionCode,
        int minGuests,
        int maxGuests,
        int sampleCount,
        Long marketMinPrice,
        Long marketMedianPrice,
        Long marketMaxPrice
) {
}
