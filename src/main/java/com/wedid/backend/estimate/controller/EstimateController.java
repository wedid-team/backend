
package com.wedid.backend.estimate.controller;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.dto.HallCostEstimate;
import com.wedid.backend.estimate.service.EstimateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @PostMapping("/hall")
    public ResponseEntity<?> calculateHallEstimate(
            @Valid @RequestBody EstimateRequest request
    ) {

        Optional<HallCostEstimate> hallOptional =
                estimateService.calculateHallCostEstimate(request);

        if (hallOptional.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                    Map.of("message", "예식장 예상 비용을 계산할 수 없습니다.")
            );
        }

        HallCostEstimate hall = hallOptional.get();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("regionCode", request.regionCode());
        response.put("guestRangeCode", request.guestRangeCode());

        Map<String, Object> hallEstimate = new LinkedHashMap<>();

        hallEstimate.put("minPrice", hall.minPrice());
        hallEstimate.put("maxPrice", hall.maxPrice());
        hallEstimate.put("sourceType", hall.priceSource());
        hallEstimate.put("referenceMonth", hall.referenceMonth());
        hallEstimate.put("sourceRegion", hall.regionName());
        hallEstimate.put("sourceUrl", hall.sourceUrl());
        hallEstimate.put("sampleCount", hall.sampleCount());

        response.put("hallEstimate", hallEstimate);

        return ResponseEntity.ok(response);
    }
}
