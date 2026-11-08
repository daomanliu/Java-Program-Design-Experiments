package com.daomanliu.test6.controller;

import com.daomanliu.test6.common.Result;
import com.daomanliu.test6.dao.UserDao;
import com.daomanliu.test6.entity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {
    private final UserDao userDao;
    public UserController(UserDao userDao) {
        this.userDao = userDao;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> list() {
        List<User> users = userDao.findAll();
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }
}
