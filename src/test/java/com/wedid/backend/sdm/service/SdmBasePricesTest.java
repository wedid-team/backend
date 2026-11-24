package com.wedid.backend.sdm.service;

import com.wedid.backend.sdm.repository.SdmBasePriceRow;
import com.wedid.backend.sdm.service.SdmBasePrices.VendorPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 금액은 테스트용 예시 값이다(원 단위). */
class SdmBasePricesTest {

    @Test
    void 업체_기본가는_가격_행별_최저_최고_가운데값의_중앙값이다() {
        List<VendorPrice> prices = SdmBasePrices.aggregate(List.of(
                row(1, "A", 1_000_000, 1_200_000),   // 110만
                row(1, "B", 1_300_000, 1_300_000),   // 130만
                row(1, "C", 900_000, 1_500_000)));   // 120만

        assertThat(prices).singleElement().satisfies(vendor -> {
            assertThat(vendor.basePrice()).isEqualTo(1_200_000);
            assertThat(vendor.rangeMin()).isEqualTo(900_000);
            assertThat(vendor.rangeMax()).isEqualTo(1_500_000);
            assertThat(vendor.priceRowCount()).isEqualTo(3);
        });
    }

    @Test
    void 가격_행이_짝수면_가운데_두_값의_평균을_원_단위로_반올림한다() {
        List<VendorPrice> prices = SdmBasePrices.aggregate(List.of(
                row(1, "A", 1_000_001, 1_000_001),
                row(1, "B", 1_000_002, 1_000_002)));

        assertThat(prices.get(0).basePrice()).isEqualTo(1_000_002);   // 1,000,001.5 → 반올림
    }

    @Test
    void 최고가가_비어_있으면_최저가_하나로_본다() {
        List<VendorPrice> prices = SdmBasePrices.aggregate(List.of(row(1, "A", 800_000, null)));

        assertThat(prices.get(0).basePrice()).isEqualTo(800_000);
        assertThat(prices.get(0).rangeMax()).isEqualTo(800_000);
    }

    @Test
    void 업체별로_묶고_출처는_대행사별로_중복_없이_준다() {
        List<VendorPrice> prices = SdmBasePrices.aggregate(List.of(
                row(2, "B", 700_000, 700_000),
                row(1, "A", 1_000_000, 1_000_000),
                row(1, "A", 1_100_000, 1_100_000)));

        assertThat(prices).extracting(VendorPrice::vendorId).containsExactly(1L, 2L);
        assertThat(prices.get(0).sources()).singleElement()
                .satisfies(source -> assertThat(source.agencyName()).isEqualTo("A"));
        // 업체 데이터의 공개 대행사 수를 그대로 쓴다
        assertThat(prices.get(0).publicAgencyCount()).isEqualTo(4);
    }

    private static SdmBasePriceRow row(long vendorId, String agency, long min, Long max) {
        return new SdmBasePriceRow(vendorId, "업체" + vendorId, "STUDIO", 4, null, agency,
                "https://www.price.go.kr/", "2026-01", null,
                BigDecimal.valueOf(min), max == null ? null : BigDecimal.valueOf(max));
    }

    private static SdmBasePriceRow row(long vendorId, String agency, long min, long max) {
        return row(vendorId, agency, min, Long.valueOf(max));
    }
}
