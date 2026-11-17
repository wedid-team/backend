
package com.wedid.backend.vendor.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wedid.backend.vendor.entity.Vendor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class VendorRepositoryTest {

    @Autowired
    private VendorRepository vendorRepository;

    @Test
    void 업체_개수_조회() {
        long count = vendorRepository.count();

        System.out.println("DB에 저장된 업체 수: " + count);

        assertTrue(count > 0, "vendors 테이블에 업체 데이터가 없습니다.");
    }

    @Test
    void findOperatingWeddingHallsByRegion() {

        List<Vendor> vendors =
                vendorRepository
                        .findByCategoryAndOperatingStatusRawAndRegionId(
                                "WEDDING_HALL",
                                "운영",
                                (short) 1
                        );

        System.out.println("서울 지역 운영 중인 웨딩홀 업체 수: "
                + vendors.size());

        assertEquals(50, vendors.size());

        assertTrue(vendors.stream().allMatch(vendor ->
                "WEDDING_HALL".equals(vendor.getCategory())
                        && "운영".equals(vendor.getOperatingStatusRaw())
                        && Short.valueOf((short) 1).equals(vendor.getRegionId())
        ));
    }

}
