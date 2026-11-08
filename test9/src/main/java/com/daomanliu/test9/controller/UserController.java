package com.daomanliu.test9.controller;

import com.daomanliu.test9.common.Result;
import com.daomanliu.test9.entity.User;
import com.daomanliu.test9.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> list() {
        List<User> users = userRepository.findAll();
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    @GetMapping("/profile")
    public Result<?> profile(Authentication authentication) {
        String name = authentication.getName();
        userRepository.findByName(name).ifPresent(user -> user.setPassword(null));
        return Result.success(userRepository.findByName(name));
    }

}














