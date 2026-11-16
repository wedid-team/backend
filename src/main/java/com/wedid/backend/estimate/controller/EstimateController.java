
package com.wedid.backend.estimate.controller;

import com.wedid.backend.estimate.dto.EstimateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    @PostMapping
    public ResponseEntity<?> createEstimate(
            @Valid @RequestBody EstimateRequest request
    ) {
        // 임시 응답: 실제 예상 비용 계산은 다음 단계에서 구현
        return ResponseEntity.ok(
                Map.of(
                        "message", "입력값 검증 성공"
                )
        );
    }
}
