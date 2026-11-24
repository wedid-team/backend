package com.wedid.backend.auth.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.wedid.backend.auth.config.AuthProperties;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 회원 ID(subject)만 담은 짧은 수명의 액세스 토큰(JWT, HS256)을 발급·검증한다. */
@Component
public class AccessTokenProvider {

    static final String ISSUER = "wedid";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenProvider(AuthProperties properties, Clock clock) {
        SecretKey key = new SecretKeySpec(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        nimbusDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                List.<OAuth2TokenValidator<Jwt>>of(timestampValidator, new JwtIssuerValidator(ISSUER))
        ));
        this.decoder = nimbusDecoder;

        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public String issue(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 서명·만료·발급자를 검증하고 회원 ID를 돌려준다. 유효하지 않으면 빈 값. */
    public Optional<UUID> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String subject = jwt.getSubject();
            return subject == null ? Optional.empty() : Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
