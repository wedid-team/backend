
package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.ProductPriceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductPriceDetailRepository
        extends JpaRepository<ProductPriceDetail, Long> {

    // 가격 기록 ID로 상품의 상세 가격 조회
    Optional<ProductPriceDetail> findByPriceRecordId(Long priceRecordId);
}
