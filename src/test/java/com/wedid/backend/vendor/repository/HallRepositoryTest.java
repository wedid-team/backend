
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.Hall;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HallRepositoryTest {

    @Autowired
    private HallRepository hallRepository;

    @Test
    void countHalls() {
        long count = hallRepository.count();

        System.out.println("DB에 저장된 웨딩홀 수: " + count);

        assertTrue(count > 0, "halls 테이블에 웨딩홀 데이터가 없습니다.");
    }

    @Test
    void findHallsByVendorId() {
        // 실제 DB에 존재하는 업체 ID를 자동으로 찾는다.
        Hall sampleHall = hallRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("조회할 웨딩홀이 없습니다."));

        Long vendorId = sampleHall.getVendorId();

        List<Hall> halls = hallRepository.findByVendorId(vendorId);

        System.out.println("업체 ID: " + vendorId);

        for (Hall hall : halls) {
            System.out.println("웨딩홀: " + hall.getSourceHallName());
        }

        assertFalse(halls.isEmpty());
        assertTrue(halls.stream()
                .allMatch(hall -> vendorId.equals(hall.getVendorId())));
    }
}
