package io.hwan.atlaskb.auth.service;

import io.hwan.atlaskb.auth.dto.LoginRequest;
import io.hwan.atlaskb.auth.dto.LoginResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(4001, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(4001, "用户名或密码错误");
        }

        String token = jwtService.generateToken(user);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, userInfo);
    }
}
