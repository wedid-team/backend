package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.PriceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PriceRecordRepository
        extends JpaRepository<PriceRecord, Long> {

    List<PriceRecord> findByHallId(Long hallId);

    List<PriceRecord> findByProductId(Long productId);
}