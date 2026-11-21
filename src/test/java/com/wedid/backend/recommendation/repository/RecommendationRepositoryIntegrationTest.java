
package com.wedid.backend.recommendation.repository;

import com.wedid.backend.vendor.repository.PriceRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/wedid_test"
})
class RecommendationRepositoryIntegrationTest {

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private PriceRecordRepository priceRecordRepository;

    @Test
    void classifyHallsCorrectly() {

        var insufficientIds =
                recommendationRepository.findInformationInsufficientHallIds(
                        "SEOUL_GANGNAM", 150
                );

        var eligibleHallIds =
                recommendationRepository.findEligibleHallPrices(
                                "SEOUL_GANGNAM", 150
                        ).stream()
                        .map(detail -> priceRecordRepository
                                .findById(detail.getPriceRecordId())
                                .orElseThrow()
                                .getHallId())
                        .collect(Collectors.toSet());

        // A, B: 정보 부족
        assertEquals(Set.of(1L, 2L), Set.copyOf(insufficientIds));

        // D: 정상 추천 후보
        assertEquals(Set.of(4L), eligibleHallIds);

        // C: 수용 인원 부족으로 제외
        assertFalse(insufficientIds.contains(3L));
        assertFalse(eligibleHallIds.contains(3L));

        // 정상 후보와 정보 부족 후보는 중복되면 안 됨
        assertTrue(
                insufficientIds.stream()
                        .noneMatch(eligibleHallIds::contains)
        );
    }
}
