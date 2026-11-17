
package com.wedid.backend.estimate.region;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Short> {

    Optional<Region> findByCode(String code);
}
