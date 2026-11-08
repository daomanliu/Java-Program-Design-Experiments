package com.daomanliu.test6.controller;

import com.daomanliu.test6.common.JwtUtils;
import com.daomanliu.test6.common.Result;
import com.daomanliu.test6.dao.UserDao;
import com.daomanliu.test6.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {
    private final UserDao userDao;
    public AuthController(UserDao userDao) {this.userDao = userDao;}

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        if (user.getName() == null || user.getPassword() == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (userDao.findByName(user.getName()) != null) {
            return Result.fail(400, "用户名已存在");
        }
        userDao.insert(user.getName(), encoder.encode(user.getPassword()));
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody User user) {
        User dbUser = userDao.findByName(user.getName());
        if (dbUser == null || !encoder.matches(user.getPassword(), dbUser.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        List<String> roles = userDao.findRolesByUserId(dbUser.getId())
                .stream().map(r -> "ROLE_" + r.getName()).toList();
        String token = JwtUtils.generate(dbUser.getName(), roles);
        return Result.success("登录成功", token);
    }
}
