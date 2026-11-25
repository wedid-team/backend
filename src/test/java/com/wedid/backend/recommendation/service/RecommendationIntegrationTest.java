
package com.wedid.backend.recommendation.service;

import com.wedid.backend.recommendation.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecommendationIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private RecommendationRepository recommendationRepository;

    // 기존 테스트: 실제 DB 기반 추천 TOP 5
    @Test
    void recommendTopFiveFromRealDatabase() {
        var result = recommendationService.recommendTopFive(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2028, 3, 18),
                30_000_000L,
                Set.of("화사한")
        );

        System.out.println("=== 실제 DB 기반 추천 결과 ===");

        result.forEach(r -> System.out.printf(
                "홀 ID: %d | 예상 비용: %s~%s원 | 추천 점수: %.2f%n",
                r.hall().hallId(),
                r.hall().minPrice(),
                r.hall().maxPrice(),
                r.totalScore()
        ));

        assertFalse(result.isEmpty(), "추천 결과가 비어 있습니다.");
        assertTrue(result.size() <= 5);

        for (int i = 1; i < result.size(); i++) {
            assertTrue(
                    result.get(i - 1).totalScore()
                            >= result.get(i).totalScore()
            );
        }
    }

    // 신규 테스트: 정보 부족 예식장 조회
    @Test
    void findInformationInsufficientHallsFromRealDatabase() {
        var insufficientHallIds =
                recommendationRepository.findInformationInsufficientHallIds(
                        "SEOUL_GANGNAM",
                        150
                );

        var eligibleHallIds =
                recommendationRepository.findCandidateHallIds(
                        "SEOUL_GANGNAM",
                        150
                );

        System.out.println("=== 정보 부족 예식장 조회 결과 ===");
        System.out.println("정보 부족 홀 ID: " + insufficientHallIds);
        System.out.println("정보 부족 홀 수: " + insufficientHallIds.size());

        // 현재 DB 기준 정보 부족 예식장은 0개
        assertEquals(0, insufficientHallIds.size());

        // 정상 후보와 정보 부족 후보는 중복되면 안 됨
        assertTrue(
                insufficientHallIds.stream()
                        .noneMatch(eligibleHallIds::contains),
                "정상 후보와 정보 부족 후보가 중복되었습니다."
        );
    }
}
