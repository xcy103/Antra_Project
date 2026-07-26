package com.bookstore.service;

import com.bookstore.dto.AuthResponse;
import com.bookstore.dto.LoginRequest;
import com.bookstore.dto.RegisterRequest;
import com.bookstore.dto.UserResponse;

/**
 * Registration and login. Registration always creates a USER; ADMINs are
 * provisioned out of band (never via self-registration).
 */
public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
