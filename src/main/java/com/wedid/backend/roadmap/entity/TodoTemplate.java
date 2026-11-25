package com.wedid.backend.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 로드맵 단계별 기본 할 일. 사용자가 직접 추가하는 할 일은 없다(R-09). */
@Entity
@Table(name = "todo_templates", schema = "public")
public class TodoTemplate {

    @Id
    private Long id;

    @Column(name = "step_template_id", nullable = false)
    private Short stepTemplateId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected TodoTemplate() {
    }

    public Long getId() {
        return id;
    }

    public Short getStepTemplateId() {
        return stepTemplateId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
