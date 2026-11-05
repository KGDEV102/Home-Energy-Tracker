CREATE TABLE refresh_tokens(
                               id BIGSERIAL PRIMARY KEY,

                               user_id BIGINT NOT NULL,
                               family_id UUID NOT NULL,
                               token_hash CHAR(64) NOT NULL,

                               issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               expires_at TIMESTAMPTZ NOT NULL,

                               used_at TIMESTAMPTZ,
                               revoked_at TIMESTAMPTZ,
                               revocation_reason VARCHAR(32),

                               replaced_by_token_id BIGINT,

                               created_ip INET,
                               user_agent VARCHAR(512),
                               CONSTRAINT fk_refresh_tokens_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_refresh_tokens_replacement
                                   FOREIGN KEY (replaced_by_token_id)
                                       REFERENCES refresh_tokens(id)
                                       ON DELETE SET NULL,

                               CONSTRAINT uk_refresh_tokens_token_hash
                                   UNIQUE (token_hash),

                               CONSTRAINT uk_refresh_tokens_replacement
                                   UNIQUE (replaced_by_token_id),

                               CONSTRAINT chk_refresh_tokens_hash
                                   CHECK (token_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT chk_refresh_tokens_expiration
        CHECK (expires_at > issued_at),
        CONSTRAINT chk_refresh_tokens_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at),

    CONSTRAINT chk_refresh_tokens_revocation
        CHECK (
            (revoked_at IS NULL AND revocation_reason IS NULL)
            OR
            (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
        ),
          CONSTRAINT chk_refresh_tokens_revocation_reason
        CHECK (
            revocation_reason IS NULL
            OR revocation_reason IN (
                'ROTATED',
                'LOGOUT',
                'REUSE_DETECTED',
                'USER_DISABLED',
                'ADMIN_REVOKED'
            )
        ),
          CONSTRAINT chk_refresh_tokens_replacement_used
        CHECK (
            replaced_by_token_id IS NULL
            OR used_at IS NOT NULL
        )
);