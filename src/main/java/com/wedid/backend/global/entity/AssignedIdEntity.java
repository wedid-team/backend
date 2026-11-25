package com.wedid.backend.global.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * 코드에서 UUID를 정해 만드는 엔티티의 공통 부모.
 * ID가 미리 있으면 Spring Data의 save()가 새 엔티티로 보지 않고 merge해서 복사본을 돌려준다.
 * (넘긴 객체는 관리되지 않아 이후 변경이 저장되지 않고, 저장 전에 SELECT도 한 번 더 나간다)
 * 처음 만든 객체는 새 엔티티로 표시해 바로 persist되게 한다.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
