package com.wedid.backend.auth.web;

import java.util.UUID;

/**
 * 인증된 요청의 principal. 컨트롤러에서 {@code @AuthenticationPrincipal LoginUser loginUser}로 받는다.
 * 비로그인 요청이면 null이 들어온다.
 */
public record LoginUser(UUID id) {
}
