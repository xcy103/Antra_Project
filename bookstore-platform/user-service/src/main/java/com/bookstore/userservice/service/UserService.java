package com.bookstore.userservice.service;

import com.bookstore.userservice.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse getByUsername(String username);

    List<UserResponse> getAllUsers();

    UserResponse getById(Long id);
}
