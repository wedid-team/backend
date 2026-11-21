
package com.wedid.backend.recommendation.repository;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

import com.wedid.backend.recommendation.service.RecommendationService;

@SpringBootTest
class RecommendationRepositoryTest {

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Test
    void findEligibleHallsInGangnam() {

        // 서울(강남), 하객 150명
        List<HallPriceDetail> results =
                recommendationRepository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 150
                );

        System.out.println("Eligible price records: " + results.size());

        assertFalse(results.isEmpty());

        assertTrue(results.stream().allMatch(detail ->
                detail.getCapacityMax() != null
                        && detail.getCapacityMax() >= 150
                        && detail.getGuaranteedGuests() != null
                        && detail.getRentalMin() != null
                        && detail.getRentalMax() != null
                        && detail.getMealMin() != null
                        && detail.getMealMax() != null
        ));
    }

    @Test
    void excludeHallsWithInsufficientCapacity() {

        List<HallPriceDetail> results =
                recommendationRepository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 1001
                );

        assertTrue(results.isEmpty());
    }

    @Test
    void excludeOtherRegions() {

        // 현재 적재된 웨딩홀 데이터는 서울(강남)에만 존재
        List<HallPriceDetail> results =
                recommendationRepository.findEligibleHallPrices(
                        "BUSAN", 150
                );

        assertTrue(results.isEmpty());
    }

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private com.wedid.backend.vendor.repository.PriceRecordRepository
            priceRecordRepository;


    @Test
    void calculateRealHallPrices() {
        var results = recommendationService.findHallPrices(
                "SEOUL_GANGNAM",
                100,
                150,
                LocalDate.of(2027, 10, 1)
        );

        assertFalse(results.isEmpty());

        System.out.println("Total price records: " + results.size());

        results.stream()
                .limit(10)
                .forEach(result -> System.out.println(
                        "Hall ID: " + result.hallId()
                                + ", Min: " + result.minPrice()
                                + ", Max: " + result.maxPrice()
                                + ", Min Billed Guests: " + result.minBilledGuests()
                                + ", Max Billed Guests: " + result.maxBilledGuests()
                ));

        assertTrue(results.stream().allMatch(result ->
                result.minPrice().compareTo(result.maxPrice()) <= 0
        ));

        assertEquals(
                results.size(),
                results.stream()
                        .map(result -> result.hallId())
                        .distinct()
                        .count()
        );
    }

    @Test
    void findCandidateHallIdsInGangnam() {

        List<Long> candidateIds =
                recommendationRepository.findCandidateHallIds(
                        "SEOUL_GANGNAM", 150
                );

        List<HallPriceDetail> eligiblePrices =
                recommendationRepository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 150
                );

        System.out.println("Candidate halls: " + candidateIds.size());

        assertFalse(candidateIds.isEmpty());

        // 중복된 웨딩홀 ID가 없어야 함
        assertEquals(
                candidateIds.size(),
                candidateIds.stream().distinct().count()
        );

        // 가격 계산이 가능한 후보는 전체 후보 목록에도 포함되어야 함
        // 단, 기존 가격 기록의 홀 ID 조회는 다음 단계에서 검증
        assertFalse(eligiblePrices.isEmpty());
    }

    @Test
    void compareCandidateAndEligibleHallCounts() {

        List<Long> candidateIds =
                recommendationRepository.findCandidateHallIds(
                        "SEOUL_GANGNAM", 150
                );

        List<HallPriceDetail> eligiblePrices =
                recommendationRepository.findEligibleHallPrices(
                        "SEOUL_GANGNAM", 150
                );

        // 가격 기록을 실제 웨딩홀 ID로 변환
        java.util.Set<Long> eligibleHallIds =
                eligiblePrices.stream()
                        .map(detail -> priceRecordRepository
                                .findById(detail.getPriceRecordId())
                                .orElseThrow()
                                .getHallId())
                        .collect(java.util.stream.Collectors.toSet());

        java.util.Set<Long> candidateHallIds =
                new java.util.HashSet<>(candidateIds);

        java.util.Set<Long> insufficientHallIds =
                new java.util.HashSet<>(candidateHallIds);

        insufficientHallIds.removeAll(eligibleHallIds);

        System.out.println("전체 후보: " + candidateHallIds.size());
        System.out.println("가격 정보 충족 후보: " + eligibleHallIds.size());
        System.out.println("가격 정보 부족 후보: " + insufficientHallIds.size());
        System.out.println("정보 부족 Hall IDs: " + insufficientHallIds);

        assertTrue(candidateHallIds.containsAll(eligibleHallIds));

        assertEquals(
                candidateHallIds.size(),
                eligibleHallIds.size() + insufficientHallIds.size()
        );
    }



}
