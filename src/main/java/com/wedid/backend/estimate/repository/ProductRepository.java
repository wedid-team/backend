
package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 카테고리별 상품 조회
    List<Product> findByCategory(String category);

    // 지역과 카테고리별 상품 조회
    List<Product> findByRegionIdAndCategory(
            Short regionId,
            String category
    );
}
