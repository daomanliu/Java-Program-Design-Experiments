package com.daomanliu.test2.controller;

import com.daomanliu.test2.common.Result;
import com.daomanliu.test2.entity.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class LoginController {
    User test = new User("test", "test");
    @PostMapping("/login")
    public Result<User> login(@RequestBody User user) {
        if (test.equals(user)) {
            return Result.success("登录成功", user);
        } else {
            return Result.fail(401, "登录错误");
        }

    }
}
