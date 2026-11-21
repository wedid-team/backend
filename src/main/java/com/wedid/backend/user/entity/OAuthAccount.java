package com.wedid.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 카카오·구글 계정과 서비스 회원의 연결. (provider, provider_subject)로 회원을 식별한다. */
@Entity
@Table(name = "oauth_accounts", schema = "public")
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OAuthAccount() {
    }

    public OAuthAccount(UUID userId, OAuthProvider provider, String providerSubject) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    public UUID getUserId() {
        return userId;
    }
}
