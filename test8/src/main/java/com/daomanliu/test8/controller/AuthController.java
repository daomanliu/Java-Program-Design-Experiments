package com.daomanliu.test8.controller;

import com.daomanliu.test8.common.JwtUtils;
import com.daomanliu.test8.common.Result;
import com.daomanliu.test8.entity.Role;
import com.daomanliu.test8.entity.User;
import com.daomanliu.test8.repository.RoleRepository;
import com.daomanliu.test8.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/register")
    @Transactional
    public Result<String> register(@RequestBody User user) {
        if (user.getName() == null || user.getPassword() == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (userRepository.findByName(user.getName()).isPresent()) {
            return Result.fail(400, "用户名已存在");
        }
        user.setPassword(encoder.encode(user.getPassword()));
        userRepository.save(user);
        Role userRole = roleRepository.findByName("USER");
        if (userRole == null) {
            userRole = roleRepository.save(new Role(null, "USER"));
        }
        roleRepository.insertUserRole(user.getId(), userRole.getId());
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody User user) {
        User dbUser = userRepository.findByName(user.getName()).orElse(null);
        if (dbUser == null || !encoder.matches(user.getPassword(), dbUser.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        List<String> roles = roleRepository.findRolesByUserId(dbUser.getId())
                .stream().map(r -> "ROLE_" + r.getName()).toList();
        String token = JwtUtils.generate(dbUser.getName(), roles);
        return Result.success("登录成功", token);
    }
}
