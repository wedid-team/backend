package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.TodoTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoTemplateRepository extends JpaRepository<TodoTemplate, Long> {

    List<TodoTemplate> findAllByOrderByStepTemplateIdAscSortOrderAsc();
}
