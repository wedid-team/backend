package com.wedid.backend.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * GET /api/v1/auth/login/{provider} 요청을 카카오·구글 인증 화면으로 보내는 요청으로 바꾼다.
 * 이때 ?redirect= 값을 세션에 보관해 로그인 후 원래 화면으로 돌아갈 수 있게 한다.
 * 등록되지 않은 provider면 null을 돌려 404로 처리되게 한다(기본 동작은 500).
 */
public class LoginRedirectAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final String authorizationBaseUri;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public LoginRedirectAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            String authorizationBaseUri
    ) {
        this.authorizationBaseUri = authorizationBaseUri;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, authorizationBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = extractRegistrationId(request);
        if (registrationId == null || clientRegistrationRepository.findByRegistrationId(registrationId) == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        if (authorizationRequest != null) {
            LoginRedirectPath.save(request);
        }
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return delegate.resolve(request, clientRegistrationId);
    }

    private String extractRegistrationId(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String prefix = authorizationBaseUri + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String registrationId = path.substring(prefix.length());
        return registrationId.isEmpty() || registrationId.contains("/") ? null : registrationId;
    }
}
