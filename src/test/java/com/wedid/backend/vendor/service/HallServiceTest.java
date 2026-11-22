
package com.wedid.backend.vendor.service;

import com.wedid.backend.estimate.region.RegionRepository;
import com.wedid.backend.recommendation.repository.RecommendationRepository;
import com.wedid.backend.recommendation.service.RecommendationService;
import com.wedid.backend.vendor.entity.Hall;
import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import com.wedid.backend.vendor.entity.Vendor;
import com.wedid.backend.vendor.repository.HallPriceDetailRepository;
import com.wedid.backend.vendor.repository.HallRepository;
import com.wedid.backend.vendor.repository.PriceRecordRepository;
import com.wedid.backend.vendor.repository.VendorRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HallServiceTest {

    @Test
    void incompletePriceRecordsShouldHaveReason() {
        HallRepository hallRepository = mock(HallRepository.class);
        VendorRepository vendorRepository = mock(VendorRepository.class);
        RecommendationRepository recommendationRepository =
                mock(RecommendationRepository.class);
        RecommendationService recommendationService =
                mock(RecommendationService.class);
        PriceRecordRepository priceRecordRepository =
                mock(PriceRecordRepository.class);
        HallPriceDetailRepository detailRepository =
                mock(HallPriceDetailRepository.class);
        RegionRepository regionRepository = mock(RegionRepository.class);

        HallService service = new HallService(
                hallRepository,
                vendorRepository,
                recommendationRepository,
                recommendationService,
                priceRecordRepository,
                detailRepository,
                regionRepository
        );

        Hall hall = mock(Hall.class);
        Vendor vendor = mock(Vendor.class);

        when(hall.getId()).thenReturn(1L);
        when(hall.getVendorId()).thenReturn(10L);
        when(vendor.getId()).thenReturn(10L);
        when(vendor.getName()).thenReturn("테스트 예식장");

        when(recommendationService.findHallPrices(
                eq("SEOUL_GANGNAM"), eq(100), eq(150), isNull()
        )).thenReturn(List.of());

        when(recommendationRepository
                .findInformationInsufficientHallIds("SEOUL_GANGNAM", 150))
                .thenReturn(List.of(1L));

        when(recommendationRepository
                .findPricePublishedHallIds("SEOUL_GANGNAM"))
                .thenReturn(List.of());

        when(hallRepository.findAllById(any()))
                .thenReturn(List.of(hall));

        when(vendorRepository.findAllById(any()))
                .thenReturn(List.of(vendor));

        PriceRecord recordA = mock(PriceRecord.class);
        PriceRecord recordB = mock(PriceRecord.class);

        when(recordA.getId()).thenReturn(101L);
        when(recordB.getId()).thenReturn(102L);

        when(priceRecordRepository.findByHallId(1L))
                .thenReturn(List.of(recordA, recordB));

        HallPriceDetail detailA = mock(HallPriceDetail.class);
        HallPriceDetail detailB = mock(HallPriceDetail.class);

        when(detailA.getPriceRecordId()).thenReturn(101L);
        when(detailB.getPriceRecordId()).thenReturn(102L);

        when(detailA.getCapacityMax()).thenReturn(200);
        when(detailB.getCapacityMax()).thenReturn(200);

        when(detailA.getGuaranteedGuests()).thenReturn(100);
        when(detailB.getGuaranteedGuests()).thenReturn(100);

        // 기록 A: 대관료만 존재
        when(detailA.getRentalMin())
                .thenReturn(BigDecimal.valueOf(3_000_000));
        when(detailA.getRentalMax())
                .thenReturn(BigDecimal.valueOf(3_000_000));

        // 기록 B: 식대만 존재
        when(detailB.getMealMin())
                .thenReturn(BigDecimal.valueOf(80_000));
        when(detailB.getMealMax())
                .thenReturn(BigDecimal.valueOf(80_000));

        when(recordA.getVatStatus()).thenReturn("INCLUDED");
        when(recordB.getVatStatus()).thenReturn("INCLUDED");

        when(detailRepository.findByPriceRecordIdIn(anyList()))
                .thenReturn(List.of(detailA, detailB));

        var result = service.getHalls(
                "SEOUL_GANGNAM",
                100,
                150,
                null,
                0,
                10,
                "recommended",
                null,
                Set.of(),
                Set.of(),
                "ALL"
        );

        assertEquals(0, result.totalCount());
        assertEquals(1, result.insufficientCount());

        var insufficientHall = result.insufficientHalls().get(0);

        assertEquals("INSUFFICIENT",
                insufficientHall.informationStatus());

        assertTrue(insufficientHall.insufficientReasons()
                .contains("PRICE_INFORMATION_INCOMPLETE"));
    }
}
