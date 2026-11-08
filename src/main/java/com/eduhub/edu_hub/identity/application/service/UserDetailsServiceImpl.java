package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.security.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    /** Transaction của login giữ khóa User đến khi cập nhật bộ đếm và tạo phiên hoàn tất. */
    @Override
    public UserDetails loadUserByUsername(@NonNull String username) {

        var user = userRepository
                .findByUsernameForUpdate(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        try {
            return new CustomUserDetail(user);
        } catch (IllegalArgumentException exception) {
            throw new InternalAuthenticationServiceException("Vai trò tài khoản không hợp lệ", exception);
        }
    }
}
