-- 공유 당시의 공개 결과 사본
CREATE TABLE public.result_shares (
    id UUID PRIMARY KEY,
    estimate_run_id UUID,
    created_by UUID,
    token_hash TEXT NOT NULL,
    public_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT uq_result_shares_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_result_shares_estimate_run
        FOREIGN KEY (estimate_run_id)
        REFERENCES public.estimate_runs (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_result_shares_created_by
        FOREIGN KEY (created_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_result_shares_estimate_run_id
    ON public.result_shares (estimate_run_id);

CREATE INDEX idx_result_shares_created_by
    ON public.result_shares (created_by);