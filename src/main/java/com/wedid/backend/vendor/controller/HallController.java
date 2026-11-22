
package com.wedid.backend.vendor.controller;

import com.wedid.backend.vendor.dto.HallListResponse;
import com.wedid.backend.vendor.service.HallService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.util.Set;
import com.wedid.backend.vendor.dto.HallDetailResponse;

import com.wedid.backend.vendor.dto.HallCompareResponse;
import java.util.List;
import com.wedid.backend.vendor.dto.MarketPriceResponse;

@RestController
@RequestMapping("/api/v1/halls")
public class HallController {

    private final HallService hallService;

    public HallController(HallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping
    public HallListResponse getHalls(
            @RequestParam String regionCode,
            @RequestParam int minGuests,
            @RequestParam int maxGuests,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weddingDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(required = false) Long userBudget,
            @RequestParam(required = false) Set<String> preferredMoods,
            @RequestParam(required = false) Set<String> moodFilters,
            @RequestParam(defaultValue = "ALL") String priceVisibility



    ) {



        return hallService.getHalls(
                regionCode,
                minGuests,
                maxGuests,
                weddingDate,
                page,
                size,
                sort,
                userBudget,
                preferredMoods,
                moodFilters,
                priceVisibility
        );



    }
    @GetMapping("/{id}")
    public HallDetailResponse getHallDetail(
            @PathVariable Long id,
            @RequestParam int minGuests,
            @RequestParam int maxGuests,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weddingDate
    ) {
        return hallService.getHallDetail(
                id, minGuests, maxGuests, weddingDate
        );
    }

    @GetMapping("/compare")
    public HallCompareResponse compareHalls(
            @RequestParam List<Long> hallIds,
            @RequestParam int minGuests,
            @RequestParam int maxGuests,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weddingDate
    ) {
        return hallService.compareHalls(
                hallIds,
                minGuests,
                maxGuests,
                weddingDate
        );
    }

    @GetMapping("/market-price")
    public MarketPriceResponse getMarketPrice(
            @RequestParam String regionCode,
            @RequestParam int minGuests,
            @RequestParam int maxGuests,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weddingDate
    ) {
        return hallService.getMarketPrice(
                regionCode,
                minGuests,
                maxGuests,
                weddingDate
        );
    }


}
