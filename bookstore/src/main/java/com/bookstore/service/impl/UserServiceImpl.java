package com.bookstore.service.impl;

import com.bookstore.dto.UserResponse;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        return UserResponse.from(userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id)));
    }
}
