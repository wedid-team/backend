package com.wedid.backend.auth.service;

import com.wedid.backend.auth.dto.AuthMeResponse;
import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 로그인 상태와 회원 정보. 비로그인이거나 회원이 삭제됐으면 authenticated=false. */
    @Transactional(readOnly = true)
    public AuthMeResponse me(LoginUser loginUser) {
        if (loginUser == null) {
            return AuthMeResponse.anonymous();
        }
        return userRepository.findById(loginUser.id())
                .map(user -> AuthMeResponse.of(user.getId(), user.getDisplayName(), user.getEmail()))
                .orElseGet(AuthMeResponse::anonymous);
    }
}
