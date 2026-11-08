package com.daomanliu.test7.controller;

import com.daomanliu.test7.common.Result;
import com.daomanliu.test7.entity.User;
import com.daomanliu.test7.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
