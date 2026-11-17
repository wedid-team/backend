
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.Optional;

public interface HallPriceDetailRepository
        extends JpaRepository<HallPriceDetail, Long> {

    List<HallPriceDetail> findByCapacityMaxGreaterThanEqual(
            Integer guestCount
    );


    Optional<HallPriceDetail> findByPriceRecordId(
            Long priceRecordId
    );

}
