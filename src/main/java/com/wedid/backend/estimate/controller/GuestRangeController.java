
package com.wedid.backend.estimate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GuestRangeController {

    public record GuestRangeResponse(
            String code,
            String name,
            Integer minGuests,
            Integer maxGuests
    ) {}

    @GetMapping("/guest-ranges")
    public List<GuestRangeResponse> getGuestRanges() {
        return List.of(
                new GuestRangeResponse("UNDER_100", "100명 미만", 0, 99),
                new GuestRangeResponse("FROM_100_TO_150", "100~150명", 100, 150),
                new GuestRangeResponse("FROM_150_TO_200", "150~200명", 150, 200),
                new GuestRangeResponse("FROM_200_TO_250", "200~250명", 200, 250),
                new GuestRangeResponse("FROM_250_TO_300", "250~300명", 250, 300),
                new GuestRangeResponse("OVER_300", "300명 이상", 300, 400),
                new GuestRangeResponse("UNDECIDED", "아직 모르겠어요", 150, 200)
        );
    }
}
