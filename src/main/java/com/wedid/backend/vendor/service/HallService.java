
package com.wedid.backend.vendor.service;

import com.wedid.backend.recommendation.repository.RecommendationRepository;
import com.wedid.backend.recommendation.service.RecommendationService;
import com.wedid.backend.vendor.dto.HallListResponse;
import com.wedid.backend.vendor.dto.HallSummaryResponse;
import com.wedid.backend.vendor.entity.Hall;
import com.wedid.backend.vendor.entity.Vendor;
import com.wedid.backend.vendor.repository.HallRepository;
import com.wedid.backend.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import com.wedid.backend.vendor.repository.HallPriceDetailRepository;
import com.wedid.backend.vendor.repository.PriceRecordRepository;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class HallService {

    private final HallRepository hallRepository;
    private final VendorRepository vendorRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationService recommendationService;
    private final PriceRecordRepository priceRecordRepository;
    private final HallPriceDetailRepository hallPriceDetailRepository;

    public HallService(
            HallRepository hallRepository,
            VendorRepository vendorRepository,
            RecommendationRepository recommendationRepository,
            RecommendationService recommendationService,
            PriceRecordRepository priceRecordRepository,
            HallPriceDetailRepository hallPriceDetailRepository
    ) {
        this.hallRepository = hallRepository;
        this.vendorRepository = vendorRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationService = recommendationService;
        this.priceRecordRepository = priceRecordRepository;
        this.hallPriceDetailRepository = hallPriceDetailRepository;
    }



    public HallListResponse getHalls(
            String regionCode,
            int minGuests,
            int maxGuests,
            LocalDate weddingDate,
            int page,
            int size,
            String sort,
            Long userBudget,
            Set<String> preferredMoods


    ) {


        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }



        if (userBudget != null && userBudget <= 0) {
            throw new IllegalArgumentException(
                    "User budget must be positive"
            );
        }



        if (minGuests <= 0 || maxGuests < minGuests) {
            throw new IllegalArgumentException("Invalid guest range");
        }

        // 1. 정상 예식장과 예상 비용 조회
        var prices = recommendationService.findHallPrices(
                regionCode, minGuests, maxGuests, weddingDate
        );

        // 2. 정보 부족 예식장 ID 조회
        List<Long> insufficientIds =
                recommendationRepository.findInformationInsufficientHallIds(
                        regionCode, maxGuests
                );

        // 3. 조회할 웨딩홀 ID 통합
        Set<Long> allIds = java.util.stream.Stream.concat(
                prices.stream().map(RecommendationService.HallPriceResult::hallId),
                insufficientIds.stream()
        ).collect(Collectors.toSet());

        Map<Long, Hall> halls = hallRepository.findAllById(allIds)
                .stream()
                .collect(Collectors.toMap(Hall::getId, Function.identity()));

        Set<Long> vendorIds = halls.values().stream()
                .map(Hall::getVendorId)
                .collect(Collectors.toSet());

        Map<Long, Vendor> vendors = vendorRepository.findAllById(vendorIds)
                .stream()
                .collect(Collectors.toMap(Vendor::getId, Function.identity()));

        // 웨딩홀별 가격 기록을 조회
        Map<Long, List<PriceRecord>> recordsByHall =
                allIds.stream().collect(Collectors.toMap(
                        Function.identity(),
                        priceRecordRepository::findByHallId
                ));

        // 가격 기록 ID를 모아 상세 정보 일괄 조회
        List<Long> priceRecordIds = recordsByHall.values().stream()
                .flatMap(List::stream)
                .map(PriceRecord::getId)
                .toList();

        Map<Long, HallPriceDetail> detailsByRecord =
                priceRecordIds.isEmpty()
                        ? Map.of()
                        : hallPriceDetailRepository
                        .findByPriceRecordIdIn(priceRecordIds)
                        .stream()
                        .collect(Collectors.toMap(
                                HallPriceDetail::getPriceRecordId,
                                Function.identity()
                        ));

        // 홀별 수용 인원 집계
        Map<Long, Integer> capacityMinByHall =
                new java.util.HashMap<>();

        Map<Long, Integer> capacityMaxByHall =
                new java.util.HashMap<>();

        for (Long hallId : allIds) {
            List<HallPriceDetail> details = recordsByHall
                    .getOrDefault(hallId, List.of())
                    .stream()
                    .map(record -> detailsByRecord.get(record.getId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            details.stream()
                    .map(HallPriceDetail::getCapacityMin)
                    .filter(java.util.Objects::nonNull)
                    .min(Integer::compareTo)
                    .ifPresent(value -> capacityMinByHall.put(hallId, value));

            details.stream()
                    .map(HallPriceDetail::getCapacityMax)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .ifPresent(value -> capacityMaxByHall.put(hallId, value));
        }

        Map<Long, Set<String>> hallMoodsById =
                halls.values().stream()
                        .collect(Collectors.toMap(
                                Hall::getId,
                                hall -> {
                                    Vendor vendor = vendors.get(hall.getVendorId());

                                    if (vendor == null ||
                                            vendor.getMoodTagsRaw() == null ||
                                            vendor.getMoodTagsRaw().isBlank()) {
                                        return Set.<String>of();
                                    }

                                    return java.util.Arrays.stream(
                                                    vendor.getMoodTagsRaw().split(",")
                                            )
                                            .map(String::trim)
                                            .filter(tag -> !tag.isEmpty())
                                            .collect(Collectors.toSet());
                                }
                        ));


        Map<Long, Double> recommendationScores =
                prices.stream()
                        .collect(Collectors.toMap(
                                RecommendationService.HallPriceResult::hallId,
                                hall -> recommendationService.calculateHallTotalScore(
                                        hall,
                                        prices,
                                        userBudget,
                                        preferredMoods,
                                        hallMoodsById.getOrDefault(hall.hallId(), Set.of()),
                                        minGuests,
                                        maxGuests


                                )
                        ));



        // 4. 정상 예식장 DTO 생성
        List<HallSummaryResponse> available = prices.stream()
                .map(price -> {
                    Hall hall = halls.get(price.hallId());
                    if (hall == null) {
                        throw new IllegalStateException("Hall not found: " + price.hallId());
                    }

                    Vendor vendor = vendors.get(hall.getVendorId());
                    if (vendor == null) {
                        throw new IllegalStateException("Vendor not found: " + hall.getVendorId());
                    }


                    return new HallSummaryResponse(
                            hall.getId(),
                            vendor.getId(),
                            hall.getSourceHallName(),
                            vendor.getName(),
                            vendor.getSigunguRaw(),
                            vendor.getAddress(),
                            vendor.getOfficialUrl(),
                            capacityMinByHall.get(hall.getId()),
                            capacityMaxByHall.get(hall.getId()),
                            price.minPrice().longValueExact(),
                            price.maxPrice().longValueExact(),
                            "AVAILABLE",
                            recommendationScores.get(hall.getId())


                    );

                })
                .sorted(Comparator.comparing(
                        HallSummaryResponse::hallName,
                        Comparator.nullsLast(String::compareTo)
                ))
                .toList();

        // 5. 정상 예식장과 겹치지 않도록 분리
        Set<Long> availableIds = available.stream()
                .map(HallSummaryResponse::hallId)
                .collect(Collectors.toSet());

        List<HallSummaryResponse> insufficient = insufficientIds.stream()
                .filter(id -> !availableIds.contains(id))
                .map(id -> {
                    Hall hall = halls.get(id);
                    if (hall == null) {
                        throw new IllegalStateException("Hall not found: " + id);
                    }

                    Vendor vendor = vendors.get(hall.getVendorId());
                    if (vendor == null) {
                        throw new IllegalStateException("Vendor not found: " + hall.getVendorId());
                    }

                    return new HallSummaryResponse(
                            hall.getId(),
                            vendor.getId(),
                            hall.getSourceHallName(),
                            vendor.getName(),
                            vendor.getSigunguRaw(),
                            vendor.getAddress(),
                            vendor.getOfficialUrl(),
                            capacityMinByHall.get(hall.getId()),
                            capacityMaxByHall.get(hall.getId()),
                            null,
                            null,
                            "INSUFFICIENT",
                            null

                    );
                })
                .sorted(Comparator.comparing(
                        HallSummaryResponse::hallName,
                        Comparator.nullsLast(String::compareTo)
                ))
                .toList();

        Comparator<HallSummaryResponse> comparator;

        switch (sort) {
            case "name":
                comparator = Comparator
                        .comparing(
                                HallSummaryResponse::hallName,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(HallSummaryResponse::hallId);
                break;

            case "price_asc":
                comparator = Comparator
                        .comparing(
                                HallSummaryResponse::estimatedMinPrice,
                                Comparator.nullsLast(Long::compareTo)
                        )
                        .thenComparing(HallSummaryResponse::hallId);
                break;

            case "price_desc":
                comparator = Comparator
                        .comparing(
                                HallSummaryResponse::estimatedMinPrice,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(HallSummaryResponse::hallId);
                break;


            case "recommended":
                comparator = Comparator
                        .comparing(
                                HallSummaryResponse::recommendationScore,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(HallSummaryResponse::hallId);
                break;


            default:
                throw new IllegalArgumentException(
                        "Unsupported sort: " + sort
                );
        }

        available = available.stream()
                .sorted(comparator)
                .toList();



        int totalCount = available.size();
        int insufficientCount = insufficient.size();

// 전체 페이지 수 계산
        int totalPages = (int) Math.ceil((double) totalCount / size);

// 현재 페이지의 시작 및 끝 위치
        int start = (int) Math.min((long) page * size, totalCount);
        int end = (int) Math.min((long) start + size, totalCount);

// 현재 페이지에 해당하는 예식장만 추출
        List<HallSummaryResponse> pagedHalls =
                available.subList(start, end);

// 다음 페이지 존재 여부
        boolean hasNext = page + 1 < totalPages;

        return new HallListResponse(
                pagedHalls,
                insufficient,
                totalCount,
                insufficientCount,
                page,
                size,
                totalPages,
                hasNext
        );

    }
}
