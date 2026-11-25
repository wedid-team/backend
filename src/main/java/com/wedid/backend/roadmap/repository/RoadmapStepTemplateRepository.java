package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.RoadmapStepTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadmapStepTemplateRepository extends JpaRepository<RoadmapStepTemplate, Short> {

    List<RoadmapStepTemplate> findAllByOrderBySortOrderAsc();
}
