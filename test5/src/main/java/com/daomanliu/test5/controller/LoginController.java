package com.daomanliu.test5.controller;

import com.daomanliu.test5.common.JwtUtils;
import com.daomanliu.test5.common.Result;
import com.daomanliu.test5.entity.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class LoginController {
    User test = new User("test", "test");
    @PostMapping("/login")
    public Result<String> login(@RequestBody User user) {
        if (test.equals(user)) {
            String token = JwtUtils.generate(user.getName());
            return Result.success("登录成功", token);
        } else {
            return Result.fail(401, "登录错误");
        }

    }
}
