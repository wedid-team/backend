package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.PlanTodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlanTodoRepository extends JpaRepository<PlanTodo, UUID> {

    List<PlanTodo> findByPlanStepIdIn(Collection<UUID> planStepIds);
}
