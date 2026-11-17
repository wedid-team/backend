
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import com.wedid.backend.vendor.entity.PriceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HallPriceDetailRepositoryTest {

    @Autowired
    private HallPriceDetailRepository hallPriceDetailRepository;

    @Autowired
    private PriceRecordRepository priceRecordRepository;

    @Test
    void findHallPriceDetails() {

        // 실제 금액 데이터가 존재하는지 확인
        long count = hallPriceDetailRepository.count();

        System.out.println("웨딩홀 가격 상세 데이터 수: " + count);

        assertTrue(count > 0, "웨딩홀 가격 상세 데이터가 없습니다.");

        // 가격 상세 데이터 1건 조회
        HallPriceDetail detail = hallPriceDetailRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        // 연결된 가격 기록 조회
        PriceRecord record = priceRecordRepository
                .findById(detail.getPriceRecordId())
                .orElseThrow();

        System.out.println("웨딩홀 ID: " + record.getHallId());
        System.out.println("대관료 최소: " + detail.getRentalMin());
        System.out.println("대관료 최대: " + detail.getRentalMax());
        System.out.println("식대 최소: " + detail.getMealMin());
        System.out.println("식대 최대: " + detail.getMealMax());
        System.out.println("장식비 최소: " + detail.getDecorationMin());
        System.out.println("장식비 최대: " + detail.getDecorationMax());
        System.out.println("최소 보증 인원: " + detail.getGuaranteedGuests());
        System.out.println("VAT 상태: " + record.getVatStatus());

        assertNotNull(record.getHallId());
    }

    @Test
    void findHallsByCapacity() {

        int guestCount = 150;

        List<HallPriceDetail> details =
                hallPriceDetailRepository
                        .findByCapacityMaxGreaterThanEqual(guestCount);

        System.out.println("150명 이상 수용 가능한 가격 기록 수: "
                + details.size());

        assertFalse(details.isEmpty(),
                "150명 이상 수용 가능한 가격 기록이 없습니다.");

        assertTrue(details.stream().allMatch(detail ->
                detail.getCapacityMax() != null
                        && detail.getCapacityMax() >= guestCount
        ));
    }

}
