package com.wedid.backend.estimate.repository;

import com.wedid.backend.estimate.entity.EstimateRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstimateRunRepository extends JpaRepository<EstimateRun, UUID> {
}
