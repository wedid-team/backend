
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByCategoryAndOperatingStatusRawAndRegionId(
            String category,
            String operatingStatusRaw,
            Short regionId
    );
}
