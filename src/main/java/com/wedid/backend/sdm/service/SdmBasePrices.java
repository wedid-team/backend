package com.wedid.backend.sdm.service;

import com.wedid.backend.sdm.repository.SdmBasePriceRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 업체별 스드메 기본가.
 * <ul>
 *   <li>대표 구성만 쓴다: 스튜디오는 촬영, 드레스 · 메이크업은 본식+촬영 (표준 상품 구분)</li>
 *   <li>가격 행마다 (최저 + 최고) / 2를 구하고, 업체 기본가는 그 값들의 중앙값이다(짝수 개면 가운데 두 값의 평균, 원 단위 반올림).
 *       최저가는 최저 직급 · 대행사 특가라 실제 계약가보다 낮을 수 있어 쓰지 않는다.</li>
 *   <li>화면용 단품가 범위는 대표 구성 가격 행의 최저 ~ 최고 (D-20)</li>
 * </ul>
 */
public final class SdmBasePrices {

    public static final Map<String, String> REPRESENTATIVE_PRODUCT_TYPES = Map.of(
            "STUDIO", "촬영",
            "DRESS", "본식+촬영",
            "MAKEUP", "본식+촬영");

    /** 화면 · 응답 순서 */
    public static final List<String> CATEGORIES = List.of("STUDIO", "DRESS", "MAKEUP");

    public record Source(String agencyName, String sourceUrl, String fileReference, java.time.LocalDate collectedAt) {
    }

    public record VendorPrice(
            long vendorId,
            String vendorName,
            String category,
            long basePrice,
            long rangeMin,
            long rangeMax,
            int priceRowCount,
            Integer publicAgencyCount,
            String officialUrl,
            List<Source> sources
    ) {
    }

    private SdmBasePrices() {
    }

    /** 대표 구성 가격 행을 업체별로 묶어 기본가를 만든다. 업체 ID 순. */
    public static List<VendorPrice> aggregate(List<SdmBasePriceRow> rows) {
        Map<Long, List<SdmBasePriceRow>> byVendor = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparingLong(SdmBasePriceRow::vendorId))
                .forEach(row -> byVendor.computeIfAbsent(row.vendorId(), id -> new ArrayList<>()).add(row));

        List<VendorPrice> result = new ArrayList<>();
        for (List<SdmBasePriceRow> vendorRows : byVendor.values()) {
            SdmBasePriceRow first = vendorRows.get(0);
            List<BigDecimal> midpoints = vendorRows.stream()
                    .map(row -> row.amountMin().add(maxOf(row)).divide(BigDecimal.valueOf(2)))
                    .sorted()
                    .toList();
            long rangeMin = vendorRows.stream().map(SdmBasePriceRow::amountMin).min(BigDecimal::compareTo).orElseThrow().longValue();
            long rangeMax = vendorRows.stream().map(SdmBasePrices::maxOf).max(BigDecimal::compareTo).orElseThrow().longValue();

            List<Source> sources = vendorRows.stream()
                    .map(row -> new Source(row.agencyName(), row.sourceUrl(), row.fileReference(), row.collectedAt()))
                    .distinct()
                    .sorted(Comparator.comparing(Source::agencyName, Comparator.nullsLast(String::compareTo))
                            .thenComparing(Source::sourceUrl, Comparator.nullsLast(String::compareTo)))
                    .toList();
            Integer agencyCount = first.vendorAgencyCount() != null
                    ? first.vendorAgencyCount()
                    : (int) sources.stream().map(Source::agencyName).filter(Objects::nonNull).distinct().count();

            result.add(new VendorPrice(
                    first.vendorId(), first.vendorName(), first.category(),
                    median(midpoints), rangeMin, rangeMax, vendorRows.size(),
                    agencyCount, first.officialUrl(), sources));
        }
        return result;
    }

    static long median(List<BigDecimal> sorted) {
        int n = sorted.size();
        BigDecimal median = n % 2 == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2));
        return median.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 최고가가 비어 있으면 최저가와 같은 한 가격으로 본다. */
    private static BigDecimal maxOf(SdmBasePriceRow row) {
        return row.amountMax() != null ? row.amountMax() : row.amountMin();
    }
}
