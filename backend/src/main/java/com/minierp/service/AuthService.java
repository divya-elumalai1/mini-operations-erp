package com.minierp.service;

import com.minierp.domain.User;
import com.minierp.dto.LoginRequest;
import com.minierp.dto.LoginResponse;
import com.minierp.exception.ResourceNotFoundException;
import com.minierp.repository.UserRepository;
import com.minierp.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.username()));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        log.info("User logged in: username={} role={}", user.getUsername(), user.getRole());
        return new LoginResponse(token, LoginResponse.BEARER, user.getUsername(),
                user.getFullName(), user.getRole().name());
    }
}
