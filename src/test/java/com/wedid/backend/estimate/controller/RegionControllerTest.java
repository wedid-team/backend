package com.wedid.backend.estimate.controller;

import com.wedid.backend.estimate.controller.RegionController.RegionResponse;
import com.wedid.backend.estimate.region.Region;
import com.wedid.backend.estimate.region.RegionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionControllerTest {

    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final RegionController controller = new RegionController(regionRepository);

    @Test
    void DB_regions에_있는_지역만_정렬_순서대로_돌려준다() {
        List<Region> stored = List.of(
                region("SEOUL_GANGNAM", "서울(강남)"),
                region("SEOUL_OTHER", "서울(강남외)"),
                region("GYEONGGI", "경기도"));
        when(regionRepository.findAllByOrderBySortOrderAsc()).thenReturn(stored);

        List<RegionResponse> regions = controller.getRegions();

        assertThat(regions).containsExactly(
                new RegionResponse("SEOUL_GANGNAM", "서울(강남)"),
                new RegionResponse("SEOUL_OTHER", "서울(강남외)"),
                new RegionResponse("GYEONGGI", "경기도"));
    }

    @Test
    void 지역이_없으면_빈_목록을_준다() {
        when(regionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        assertThat(controller.getRegions()).isEmpty();
    }

    private static Region region(String code, String name) {
        Region region = mock(Region.class);
        when(region.getCode()).thenReturn(code);
        when(region.getName()).thenReturn(name);
        return region;
    }
}
