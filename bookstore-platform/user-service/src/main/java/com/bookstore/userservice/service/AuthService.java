package com.bookstore.userservice.service;

import com.bookstore.userservice.dto.AuthResponse;
import com.bookstore.userservice.dto.LoginRequest;
import com.bookstore.userservice.dto.RegisterRequest;
import com.bookstore.userservice.dto.UserResponse;

/**
 * Registration and login. Registration always creates a USER; ADMINs are
 * provisioned out of band (never via self-registration).
 */
public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
