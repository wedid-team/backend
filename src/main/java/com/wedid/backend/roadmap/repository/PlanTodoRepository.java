package com.wedid.backend.roadmap.repository;

import com.wedid.backend.roadmap.entity.PlanTodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanTodoRepository extends JpaRepository<PlanTodo, UUID> {

    List<PlanTodo> findByPlanStepIdIn(Collection<UUID> planStepIds);

    /** 다른 계획의 할 일은 찾지 않는다. */
    @Query("""
            select t from PlanTodo t
            where t.id = :id
              and t.planStepId in (select s.id from PlanStep s where s.planId = :planId)
            """)
    Optional<PlanTodo> findByIdAndPlanId(@Param("id") UUID id, @Param("planId") UUID planId);
}
