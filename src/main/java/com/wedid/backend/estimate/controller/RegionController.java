
package com.wedid.backend.estimate.controller;

import com.wedid.backend.estimate.region.Region;
import com.wedid.backend.estimate.region.RegionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 선택 가능한 결혼 지역. DB regions 테이블에 있는 지역만 sort_order 순으로 돌려준다.
 * (견적·계획 저장은 regions에서 지역을 찾으므로, 목록도 같은 곳에서 읽어야 고른 지역이 항상 저장된다)
 */
@RestController
@RequestMapping("/api/v1")
public class RegionController {

    public record RegionResponse(String code, String name) {}

    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping("/regions")
    public List<RegionResponse> getRegions() {
        return regionRepository.findAllByOrderBySortOrderAsc().stream()
                .map(region -> new RegionResponse(region.getCode(), region.getName()))
                .toList();
    }
}
