
package com.wedid.backend.vendor.repository;

import com.wedid.backend.vendor.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HallRepository extends JpaRepository<Hall, Long> {

    List<Hall> findByVendorId(Long vendorId);

}
