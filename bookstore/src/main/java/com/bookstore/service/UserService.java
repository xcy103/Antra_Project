package com.bookstore.service;

import com.bookstore.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse getByUsername(String username);

    List<UserResponse> getAllUsers();

    UserResponse getById(Long id);
}
