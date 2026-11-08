package com.daomanliu.test9.controller;

import com.daomanliu.test9.common.JwtUtils;
import com.daomanliu.test9.common.Result;
import com.daomanliu.test9.entity.Role;
import com.daomanliu.test9.entity.User;
import com.daomanliu.test9.repository.RoleRepository;
import com.daomanliu.test9.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class AuthController {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RedisTemplate<Object, Object> redisTemplate;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository, RedisTemplate<Object, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.redisTemplate = redisTemplate;
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
        redisTemplate.opsForValue().set("login:" + dbUser.getName(), token, Expiration.from(Duration.ofHours(1)));
        return Result.success("登录成功", token);
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String header) {
        String token = header.substring(7);
        long remaining = JwtUtils.getRemainingMillis(token);
        if (remaining > 0) {
            redisTemplate.opsForValue().set("blacklist:" + token, "1", Expiration.from(Duration.ofMillis(remaining)));
        }

        String username = JwtUtils.getUsername(token);
        redisTemplate.delete("login:" + username);
        return Result.success("退出成功", null);
    }
}





















