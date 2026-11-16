-- 카카오·구글 로그인 계정 연결
CREATE TABLE public.oauth_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL,
    provider TEXT NOT NULL,
    provider_subject TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_oauth_accounts_provider_subject
        UNIQUE (provider, provider_subject),

    CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_oauth_accounts_provider
        CHECK (provider IN ('KAKAO', 'GOOGLE'))
);

CREATE INDEX idx_oauth_accounts_user_id
    ON public.oauth_accounts (user_id);