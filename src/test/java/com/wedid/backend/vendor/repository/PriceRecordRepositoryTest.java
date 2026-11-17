
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.PriceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PriceRecordRepositoryTest {

    @Autowired
    private PriceRecordRepository priceRecordRepository;

    @Test
    void countPriceRecords() {
        long count = priceRecordRepository.count();

        System.out.println("전체 가격 기록 수: " + count);

        assertTrue(count > 0, "가격 기록이 없습니다.");
    }

    @Test
    void findPriceRecordsByHallId() {
        PriceRecord sample = priceRecordRepository.findAll()
                .stream()
                .filter(record -> record.getHallId() != null)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("웨딩홀 가격 기록이 없습니다."));

        Long hallId = sample.getHallId();

        List<PriceRecord> records =
                priceRecordRepository.findByHallId(hallId);

        System.out.println("웨딩홀 ID: " + hallId);
        System.out.println("가격 기록 수: " + records.size());

        for (PriceRecord record : records) {
            System.out.println(
                    "가격 유형: " + record.getPriceType()
                            + ", VAT: " + record.getVatStatus()
            );
        }

        assertFalse(records.isEmpty());
        assertTrue(records.stream()
                .allMatch(record ->
                        hallId.equals(record.getHallId())));
    }
}
