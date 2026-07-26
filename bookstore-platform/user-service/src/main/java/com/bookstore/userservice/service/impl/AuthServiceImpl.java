package com.bookstore.userservice.service.impl;

import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.userservice.dto.AuthResponse;
import com.bookstore.userservice.dto.LoginRequest;
import com.bookstore.userservice.dto.RegisterRequest;
import com.bookstore.userservice.dto.UserResponse;
import com.bookstore.userservice.entity.User;
import com.bookstore.userservice.repository.UserRepository;
import com.bookstore.userservice.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.USER);
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException (-> 401) on a wrong username/password.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.username()));

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return AuthResponse.bearer(token, jwtUtil.getExpirationSeconds());
    }
}
