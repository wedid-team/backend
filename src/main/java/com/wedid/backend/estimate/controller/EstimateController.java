
package com.wedid.backend.estimate.controller;

import com.wedid.backend.estimate.dto.EstimateRequest;
import com.wedid.backend.estimate.service.EstimateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @PostMapping
    public ResponseEntity<?> createEstimate(
            @Valid @RequestBody EstimateRequest request
    ) {

        Map<String, BigDecimal[]> totals =
                estimateService.calculateTotalEstimate(request);

        if (totals.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                    Map.of("message", "예상 비용을 계산할 수 없습니다.")
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("regionCode", request.regionCode());
        response.put("guestRangeCode", request.guestRangeCode());

        response.put("budget", toPriceRange(totals.get("BUDGET")));
        response.put("balanced", toPriceRange(totals.get("BALANCED")));
        response.put("premium", toPriceRange(totals.get("PREMIUM")));

        return ResponseEntity.ok(response);
    }

    private Map<String, BigDecimal> toPriceRange(
            BigDecimal[] range
    ) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        result.put("minPrice", range[0]);
        result.put("maxPrice", range[1]);

        return result;
    }
}
