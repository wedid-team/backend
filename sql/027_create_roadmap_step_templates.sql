-- 기본 6단계 로드맵 정의
CREATE TABLE public.roadmap_step_templates (
    id SMALLINT PRIMARY KEY,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    sort_order SMALLINT NOT NULL,
    days_before_start INTEGER NOT NULL,
    days_before_end INTEGER NOT NULL,
    cost_kind TEXT NOT NULL,
    decision_items JSONB NOT NULL,
    guidance_items JSONB NOT NULL,

    CONSTRAINT uq_roadmap_step_templates_code
        UNIQUE (code),

    CONSTRAINT uq_roadmap_step_templates_sort_order
        UNIQUE (sort_order),

    CONSTRAINT ck_roadmap_step_templates_id
        CHECK (id BETWEEN 1 AND 6),

    CONSTRAINT ck_roadmap_step_templates_sort_order
        CHECK (sort_order BETWEEN 1 AND 6),

    CONSTRAINT ck_roadmap_step_templates_days
        CHECK (
            days_before_start >= days_before_end
            AND days_before_end >= 0
        ),

    CONSTRAINT ck_roadmap_step_templates_cost_kind
        CHECK (cost_kind IN ('NONE', 'VENUE', 'SDM'))
);