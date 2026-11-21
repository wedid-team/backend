package com.wedid.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 서비스 회원. 별도 가입 화면 없이 첫 소셜 로그인 때 생성된다(C-09).
 * 이메일은 식별키로 쓰지 않으며, 로그인 식별은 {@link OAuthAccount}가 담당한다.
 */
@Entity
@Table(name = "users", schema = "public")
public class User {

    @Id
    private UUID id;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
    }

    private User(UUID id, String displayName, String email, OffsetDateTime now) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.updatedAt = now;
    }

    public static User create(String displayName, String email, OffsetDateTime now) {
        return new User(UUID.randomUUID(), displayName, email, now);
    }

    /**
     * 소셜 프로필 값이 바뀌었으면 반영한다.
     * 사용자가 동의를 철회해 값이 비어 온 경우에는 기존 값을 지우지 않는다.
     */
    public void updateProfile(String displayName, String email, OffsetDateTime now) {
        boolean changed = false;

        if (displayName != null && !Objects.equals(this.displayName, displayName)) {
            this.displayName = displayName;
            changed = true;
        }
        if (email != null && !Objects.equals(this.email, email)) {
            this.email = email;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }
}
