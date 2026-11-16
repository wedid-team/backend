-- 로드맵 단계별 기본 할 일
CREATE TABLE public.todo_templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    step_template_id SMALLINT NOT NULL,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    sort_order INTEGER NOT NULL,

    CONSTRAINT uq_todo_templates_code
        UNIQUE (code),

    CONSTRAINT uq_todo_templates_step_sort_order
        UNIQUE (step_template_id, sort_order),

    CONSTRAINT fk_todo_templates_step_template
        FOREIGN KEY (step_template_id)
        REFERENCES public.roadmap_step_templates (id)
        ON DELETE RESTRICT
);