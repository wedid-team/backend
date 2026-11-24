package com.wedid.backend.user.repository;

import com.wedid.backend.user.entity.OAuthAccount;
import com.wedid.backend.user.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderSubject(
            OAuthProvider provider,
            String providerSubject
    );
}
