-- 로그인 유지용 리프레시 토큰
-- 토큰 평문은 저장하지 않고 SHA-256 해시만 저장한다.
-- 갱신할 때마다 기존 토큰을 폐기(revoked_at)하고 새 토큰을 발급한다(rotation).
CREATE TABLE public.refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON public.refresh_tokens (user_id);
