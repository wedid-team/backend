
package com.wedid.backend.estimate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RegionController {

    public record RegionResponse(String code, String name) {}

    @GetMapping("/regions")
    public List<RegionResponse> getRegions() {
        return List.of(
                new RegionResponse("SEOUL_GANGNAM", "서울(강남)"),
                new RegionResponse("SEOUL_OTHER", "서울(강남 외)"),
                new RegionResponse("GYEONGGI", "경기"),
                new RegionResponse("INCHEON", "인천"),
                new RegionResponse("BUSAN", "부산"),
                new RegionResponse("DAEGU", "대구"),
                new RegionResponse("JEONNAM_GWANGJU", "전남·광주"),
                new RegionResponse("DAEJEON", "대전"),
                new RegionResponse("ULSAN", "울산"),
                new RegionResponse("GANGWON", "강원"),
                new RegionResponse("CHUNGCHEONG", "충청"),
                new RegionResponse("JEONBUK", "전북"),
                new RegionResponse("GYEONGSANG", "경상"),
                new RegionResponse("JEJU", "제주")
        );
    }
}
