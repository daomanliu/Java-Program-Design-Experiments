# 实验九　引入 Redis（test9）——token 存储与黑名单

> 对应工程：`test9`　｜　来源：由 **test8** 演进而来　｜　端口：8080　｜　前置：MySQL + **Redis**

## 一、实验目标

1. 装好 Redis，会用 `redis-cli` 做基本的增删查和过期时间操作；
2. 在 Spring Boot 里接入 Redis（`spring-boot-starter-data-redis` + `RedisTemplate`）；
3. 登录时把 token 写进 Redis（`login:用户名` → token，带 1 小时 TTL）；
4. **退出登录时把 token 加入黑名单**（`blacklist:token` → "1"，TTL = token 剩余寿命）；
5. 过滤器每次请求先查黑名单，**已注销的 token 立刻失效**；
6. 能用 `redis-cli` 亲眼看到 key、TTL，并验证"注销后旧 token 变 401"。

---

## 二、原理速览

### 2.1 为什么实验五的"退出登录"是假的

JWT 是**无状态**的：服务器不保存任何东西，只靠**验签**认人。所以：

- 前端把 token 从 `localStorage` 删掉 → 只是"浏览器忘了"；
- **把这个 token 抄下来，在任何地方仍然能用到过期为止**；
- 服务器**没有办法**"让它失效"——因为它根本没存过它。

要解决这个问题，只有两条路：

| 方案 | 做法 | 优缺点 |
|------|------|--------|
| 有状态会话 | 回到 Session | 简单，但失去 JWT 的横向扩展优势 |
| **黑名单**（本实验） | 把"要作废的 token"记到一个**共享存储**里，每次请求先查 | 保留无状态的大部分优势，只需一个很小的存储 |

### 2.2 黑名单的工作方式

```text
【登录】  POST /login
   签发 token（exp = 现在 + 120s）
   同时写入 Redis：login:daomanliu = <token>   TTL = 1 小时

【正常请求】 GET /profile   Authorization: Bearer <token>
   JwtAuthFilter：
      ① 查 Redis 有没有 blacklist:<token> ？
          有 → 不认证 → 401
          没有 → ② 验签 → 认证通过 → 200

【退出】  POST /logout   Authorization: Bearer <token>
   算出 token 还剩多少毫秒（remaining）
   写 Redis：blacklist:<token> = "1"   TTL = remaining
   删 Redis：login:daomanliu

【再用旧 token】 GET /profile
   ① 命中黑名单 → 401 ✅ 立刻失效
```

**为什么要设 TTL？**
token 最多还能活 120 秒，120 秒后它自己就过期了（验签会失败）。黑名单只需要"活到 token 过期"就够了，否则 Redis 会被无限撑大。**这正是 Redis 的 `EXPIRE` 最擅长的场景。**

### 2.3 为什么用 Redis 而不是 Map

| | 内存 `Map` | Redis |
|---|---|---|
| 多台服务器 | ❌ 各自一份，A 注销了 B 还认 | ✅ 共享 |
| 重启 | ❌ 全丢 | ✅ 可持久化 |
| 自动过期 | ❌ 要自己写定时任务 | ✅ `EXPIRE` 天生支持 |
| 内存占用 | 全在 JVM 堆里 | 独立进程，可控 |

### 2.4 Redis 是什么

- 一个**内存**键值数据库，读写极快（10 万 QPS 量级）；
- 常用来做：缓存、分布式锁、计数器、会话/令牌存储；
- 数据模型：`key → value`，value 支持 5 种类型：

| 类型 | 命令 | 典型用途 |
|------|------|----------|
| String | `SET/GET/INCR` | 缓存对象、计数器、**本实验存 token** |
| Hash | `HSET/HGETALL` | 存对象的多个字段 |
| List | `LPUSH/RPOP` | 队列、最新消息 |
| Set | `SADD/SISMEMBER` | 去重、黑名单（另一种实现） |
| ZSet | `ZADD/ZRANGE` | 排行榜 |

---

## 三、实验准备：安装并启动 Redis

### 3.1 三选一

**方案 A：Docker（最省事）**

```powershell
docker run -d --name redis -p 6379:6379 redis:7
docker ps            # 看到 redis 容器在跑
```

**方案 B：WSL2**

```bash
wsl --install -d Ubuntu          # 已装过可跳过
sudo apt update
sudo apt install -y redis-server
sudo service redis-server start
redis-cli ping
```

**方案 C：Windows 免安装版**

搜索 "tporadowski redis windows"，下载 Release 的 zip，解压后：

- 双击 `redis-server.exe` → 出现一个大方块图案（不要关）；
- 同一目录再开一个 cmd，运行 `redis-cli.exe`。

### 3.2 验证

```powershell
redis-cli ping
```

返回 `PONG` 就成功了。如果提示"不是内部或外部命令"，就到 Redis 安装目录下执行，或者把它加进 PATH。

### 3.3 redis-cli 必会命令（本实验会反复用）

| 命令 | 作用 | 示例 |
|------|------|------|
| `PING` | 连通性测试 | `PING` → PONG |
| `SET key value` | 存字符串 | `SET name tom` |
| `SETEX key 秒 value` | 存并设置过期时间 | `SETEX code 60 1234` |
| `GET key` | 取值 | `GET name` → "tom" |
| `TTL key` | 剩余秒数 | `TTL code` → 58；**-1 表示永不过期，-2 表示 key 不存在** |
| `DEL key` | 删除 | `DEL name` |
| `EXISTS key` | 是否存在 | `EXISTS name` → 1/0 |
| `KEYS 模式` | 按模式查 key（**生产慎用**，会阻塞） | `KEYS *` |
| `SCAN 0` | 游标遍历（生产用这个） | `SCAN 0 COUNT 100` |
| `TYPE key` | 看类型 | `TYPE name` → string |
| `DBSIZE` | key 总数 | `DBSIZE` |
| `FLUSHDB` | 清空当前库（**危险**） | `FLUSHDB` |
| `MONITOR` | 实时打印服务器收到的命令（调试神器） | 另开一个窗口运行 |

> 建议现在就动手试一遍：`SET` → `GET` → `TTL` → `DEL`，把"过期"这个概念坐实。

---

## 四、实验步骤

### 步骤 1：复制 test8 得到 test9

改 `artifactId`、包名 `com.daomanliu.test9`、启动类 `Test9Application`、`spring.application.name`。

### 步骤 2：加 Redis 依赖

**文件路径**：`test9/pom.xml`

在 `<dependencies>` 末尾追加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**它带来了什么？**

| 组件 | 说明 |
|------|------|
| Lettuce | 默认的 Redis 客户端（Netty 实现，线程安全） |
| `RedisTemplate<Object, Object>` | 通用操作模板（**本工程用的就是它**） |
| `StringRedisTemplate` | key/value 都用 String 序列化的模板（进阶时用） |
| 自动配置 | 读 `spring.data.redis.*` 自动建连接工厂 |

> 不需要写任何 Redis 配置类，Spring Boot 会自动配好。

### 步骤 3：配置 Redis 连接

**文件路径**：`test9/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: test9
  datasource:
    url: jdbc:mysql://localhost:3306/test6?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
```

**注意位置**：Redis 的配置在 `spring.data.redis` 下面（**不是** `spring.redis`——3.0 以后改过，写错就静默连默认值）。

如果 Redis 设了密码，再加一行：

```yaml
      password: 你的密码
```

> **小技巧**：启动后如果日志里有 `Unable to connect to Redis`，说明配置对不上或服务没起。**本实验必须在 Redis 启动的前提下运行**，否则登录时写 key 会抛异常（登录接口直接 500）。

### 步骤 4：改造 JwtUtils，增加"算剩余时间"的能力

**文件路径**：`test9/src/main/java/com/daomanliu/test9/common/JwtUtils.java`

```java
package com.daomanliu.test9.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.List;

public class JwtUtils {
    private static final Key KEY = Keys.hmacShaKeyFor("daomanliu-test6-jwt-secret-key-2026".getBytes());
    private static final long EXPIRE = 120 * 1000;

    public static String generate(String username, List<String> roles) {
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(KEY)
                .compact();
    }

    private static Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
    }

    public static boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public static String getUsername(String token) {
        return parse(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(String token) {
        return parse(token).get("roles", List.class);
    }

    public static Date getExpiration(String token) {
        return Jwts.parserBuilder().setSigningKey(KEY).build()
                .parseClaimsJws(token).getBody().getExpiration();
    }

    public static long getRemainingMillis(String token) {
        long remaining = getExpiration(token).getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }
}
```

**新增的两个方法：**

| 方法 | 作用 |
|------|------|
| `getExpiration(token)` | 从 payload 里取出 `exp`（一个 `Date`） |
| `getRemainingMillis(token)` | `exp - 现在`，得到**还剩多少毫秒**；用 `Math.max(..., 0)` 保证不会返回负数 |

> 黑名单的 TTL 就靠这个值：**token 还能活多久，黑名单条目就活多久**，不多不少。

### 步骤 5：改造认证控制器（写 token / 加黑名单）

**文件路径**：`test9/src/main/java/com/daomanliu/test9/controller/AuthController.java`

```java
package com.daomanliu.test9.controller;

import com.daomanliu.test9.common.JwtUtils;
import com.daomanliu.test9.common.Result;
import com.daomanliu.test9.entity.Role;
import com.daomanliu.test9.entity.User;
import com.daomanliu.test9.repository.RoleRepository;
import com.daomanliu.test9.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

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
```

**三处 Redis 操作，逐个讲：**

#### ① 注入 `RedisTemplate`

```java
private final RedisTemplate<Object, Object> redisTemplate;
```

- 这是 Spring Boot 自动配置好的 Bean，**不用自己 new**；
- 泛型 `<Object, Object>` 是因为默认序列化器是 **JDK 序列化**，能存任意对象（后面会讲它带来的副作用）；
- `opsForValue()` 拿到"字符串/值"操作器，还可以 `opsForHash()`、`opsForList()`、`opsForSet()`、`opsForZSet()`。

#### ② 登录：把 token 存进 Redis

```java
redisTemplate.opsForValue().set("login:" + dbUser.getName(), token, Expiration.from(Duration.ofHours(1)));
```

| 部分 | 说明 |
|------|------|
| key = `"login:daomanliu"` | **约定：用 `业务前缀:标识` 的命名法**，方便在 redis-cli 里按前缀筛选 |
| value = token | 一个用户只保留**最近一次**登录的 token（后面的登录会覆盖） |
| 第三个参数 | 过期时间 1 小时 |

> 有了这个 key，就可以做"**同一账号只能在一处登录**"（新登录把旧 token 记进黑名单），或者"查看在线用户"（`KEYS login:*`）。本实验只做了写入，读取留作思考题。

#### ③ 退出：写黑名单 + 删登录记录

```java
String token = header.substring(7);                       // 去掉 "Bearer "
long remaining = JwtUtils.getRemainingMillis(token);      // token 还能活多久

if (remaining > 0) {
    redisTemplate.opsForValue().set(
        "blacklist:" + token,                             // key：整个 token 作为标识
        "1",                                              // value：随便，只要 key 存在就代表"已作废"
        Expiration.from(Duration.ofMillis(remaining)));   // TTL = 剩余寿命
}

String username = JwtUtils.getUsername(token);
redisTemplate.delete("login:" + username);                // 清掉在线记录
```

**要点：**

- **key 是整个 token 字符串**（很长，但 JWT 本来就适合直接当 key 用——它唯一且不可预测）；
- **value 用 "1" 就够**，我们只关心"这个 key 在不在"；
- **TTL 必须设**，否则黑名单会无限增长；
- 已经过期的 token 不需要进黑名单（它自己就无效了），所以有 `if (remaining > 0)`。

### 步骤 6：改造过滤器，先查黑名单

**文件路径**：`test9/src/main/java/com/daomanliu/test9/config/JwtAuthFilter.java`

```java
package com.daomanliu.test9.config;

import com.daomanliu.test9.common.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final RedisTemplate<Object, Object> redisTemplate;

    public JwtAuthFilter(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token))) {
                chain.doFilter(request, response);
                return;
            }
            if (JwtUtils.validate(token)) {
                String username = JwtUtils.getUsername(token);
                List<String> roles = JwtUtils.getRoles(token);
                var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
                var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

**和实验八的差异只有 4 行：**

```diff
  String token = header.substring(7);
+ if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token))) {
+     chain.doFilter(request, response);   // 不设置认证信息，后面自然被 401
+     return;
+ }
  if (JwtUtils.validate(token)) {
```

**为什么要用 `Boolean.TRUE.equals(...)` 而不是直接 `if (hasKey(...))`？**

因为 `hasKey` 返回的是包装类型 `Boolean`，理论上可能为 `null`。直接 `if (Boolean)` 会**自动拆箱**，遇到 null 就抛 NPE。写 `Boolean.TRUE.equals(...)` 是最稳的写法。

**注意过滤顺序**：先查黑名单，再验签。因为校验签名要算 HMAC，比查 Redis 略慢；先查黑名单相当于"最快路径拦截"。

### 步骤 7：改 SecurityConfig，放行 /logout

**文件路径**：`test9/src/main/java/com/daomanliu/test9/config/SecurityConfig.java`

```java
package com.daomanliu.test9.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/*.html", "/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"msg\":\"未登录或权限不够\"}");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**两个新变化，都很关键：**

| 变化 | 原因 |
|------|------|
| `.logout(AbstractHttpConfigurer::disable)` | Spring Security **自带一个 `/logout` 接口**（默认会跳登录页）。不关掉它，我们的 `POST /logout` 会被它抢走 |
| 白名单里加 `"/logout"` | 退出接口自己必须能被调用（哪怕 token 已过期，也要允许调进来） |

> 第二个改动有争议：放行 `/logout` 后，**谁都能调**这个接口。但真正执行"作废"依赖的是请求头里的 token，所以这是个可接受的设计。更严谨的做法是**不放行**，让过滤器先认证再进 Controller。

### 步骤 8：新增 /profile 接口（个人中心）

**文件路径**：`test9/src/main/java/com/daomanliu/test9/controller/UserController.java`

```java
package com.daomanliu.test9.controller;

import com.daomanliu.test9.common.Result;
import com.daomanliu.test9.entity.User;
import com.daomanliu.test9.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/profile")
    public Result<?> profile(Authentication authentication) {
        String name = authentication.getName();
        userRepository.findByName(name).ifPresent(user -> user.setPassword(null));
        return Result.success(userRepository.findByName(name));
    }

}
```

**新增的 `/profile` 讲三点：**

1. **`Authentication authentication` 是 Spring 自动注入的**——里面就是 `JwtAuthFilter` 塞进 `SecurityContext` 的那个对象，`getName()` 就是用户名（我们签发 token 时的 `sub`）。
2. 这个接口**没有 `@PreAuthorize`**，所以任何**已登录**用户都能访问（这就够测黑名单了）。
3. 返回前把密码置空。

> ⚠️ **建议你检查一下这个方法的实现**：它调用了两次 `findByName`，并且第一次就 `setPassword(null)`。在默认开启的 **OSIV（Open Session In View）** 下，这个实体会一直处于"托管"状态，`setPassword(null)` 有被 Hibernate 脏检查**写回数据库**的风险（也就是把真密码改成 null）。更安全的写法是查一次、置空后直接返回同一个对象，或者返回一个不含 password 的 DTO：
>
> ```java
> User u = userRepository.findByName(name).orElse(null);
> if (u != null) u.setPassword(null);
> return Result.success(u);
> ```
>
> **这只是提醒，我没有改你的代码。**

### 步骤 9：前端页面

以下 5 个文件直接从 test8 复制（其中 `profile.html` 是本节新增的）：

| 文件路径 | 作用 | 调用的接口 |
|----------|------|-----------|
| `test9/src/main/resources/static/index.html` | 导航首页 | 无 |
| `test9/src/main/resources/static/register.html` | 注册 | `POST /register` |
| `test9/src/main/resources/static/login.html` | 登录并存 token | `POST /login` |
| `test9/src/main/resources/static/users.html` | 用户列表 | `GET /users` |
| `test9/src/main/resources/static/profile.html` | **个人中心 + 退出登录** | `GET /profile`、`POST /logout` |

**访问地址**：`http://localhost:8080/index.html`

`profile.html` 的关键交互（不用你写，但要知道它在干什么）：

| 行为 | 对应代码 |
|------|----------|
| 打开页面自动查个人信息 | 立即执行 `fetch('/profile', {headers:{Authorization:'Bearer '+token}})` |
| 显示 ID 和用户名 | 读取 `data.data.id`、`data.data.name` |
| 点"退出登录" | `fetch('/logout', {method:'POST', headers:{...}})`，然后 `localStorage.removeItem('token')`，跳回登录页 |

---

## 五、完整验证流程（本实验的高潮部分）

> 下面 8 步建议**按顺序做一遍**，每一步都截图，实验报告就完整了。

### 第 1 步：确认 Redis 与 MySQL 都启动

```powershell
redis-cli ping        # PONG
```

### 第 2 步：启动工程

运行 `Test9Application`，控制台出现 `Started Test9Application`。

### 第 3 步：注册并登录

浏览器 `http://localhost:8080/index.html` → 注册 `redis_user` / `123456` → 登录。

### 第 4 步：在 Redis 里看到 login key

另开一个 PowerShell 窗口：

```powershell
redis-cli
127.0.0.1:6379> KEYS *
```

你会看到类似（**注意 key 前面那串乱码，第 6 节会解释**）：

```text
1) "\xac\xed\x00\x05t\x00\x13login:redis_user"
```

再看它的过期时间：

```text
127.0.0.1:6379> KEYS *login*
1) "\xac\xed\x00\x05t\x00\x13login:redis_user"
127.0.0.1:6379> TTL "\xac\xed\x00\x05t\x00\x13login:redis_user"
(integer) 3598
```

> 3598 秒 ≈ 1 小时，说明 **TTL 设置生效了**。
> 如果 `TTL` 返回 **-1**，说明没设过期时间；返回 **-2**，说明 key 不存在。

### 第 5 步：访问个人中心

浏览器新开标签页 `http://localhost:8080/profile.html` → 显示 ID 和用户名。

### 第 6 步：退出登录，观察黑名单

1. 在 `profile.html` 点"退出登录" → 弹窗"退出成功"，跳回登录页；
2. 切到 redis-cli：

```text
127.0.0.1:6379> KEYS *blacklist*
1) "\xac\xed\x00\x05t\x00\xb5blacklist:eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyZWRpcy11c2VyIi..."
127.0.0.1:6379> TTL "\xac\xed\x00\x05t\x00\xb5blacklist:eyJ...（完整 key）"
(integer) 87
```

- 黑名单 key **出现了** ✅
- TTL 是 **剩余秒数**（87 秒，因为 token 总共才 120 秒）✅
- 同时 `login:redis_user` 已经**被删除** ✅

### 第 7 步：用旧 token 再访问 → 401

**这是最关键的验证。** 有三种做法，任选：

- **做法一（最简单）**：在 `profile.html` 里，退出登录后按浏览器的"后退"回到刚才的页面并刷新 → 页面提示"登录已失效，请重新登录"；
- **做法二（Apifox）**：见第八节 8.4；
- **做法三（手工）**：把退出前的 token 复制下来，用 curl 请求：

```powershell
curl -i -H "Authorization: Bearer 你的旧token" http://localhost:8080/profile
```

返回：

```text
HTTP/1.1 401
{"code":401,"msg":"未登录或权限不够"}
```

> 注意 msg 变成了 **"未登录或权限不够"**（test8 是"未登录或token无效"），因为我们在 test9 的 `authenticationEntryPoint` 里改了文案。看到这个文案就说明是新代码在跑。

### 第 8 步：等黑名单过期，观察 key 消失

token 只有 120 秒寿命，黑名单 TTL 与之相同。等 2 分钟后再执行：

```text
127.0.0.1:6379> KEYS *blacklist*
(empty array)
```

**key 自己消失了** —— 这就是 Redis 的 TTL 在替你"打扫卫生"，不需要写任何定时任务。

---

## 六、深入理解 Redis（本节重点知识）

### 6.1 为什么 redis-cli 里的 key 是乱码

因为 `RedisTemplate<Object, Object>` 默认使用 **JDK 序列化**（`JdkSerializationRedisSerializer`）来序列化 key 和 value，不是普通的字符串。

存进去的 "login:redis_user" 实际是：

```text
AC ED 00 05 74 00 0F 6C 6F 67 69 6E 3A 72 65 64 69 73 5F 75 73 65 72
└─────┬────┘ └┬┘ └┬┘ └──────────────┬──────────────┘
  Java序列化头  't' 长度   "login:redis_user"
```

所以 `KEYS login:*` **匹配不到**（开头是二进制），必须用 `KEYS *login*`。

**三个实用技巧：**

| 需求 | 做法 |
|------|------|
| 按前缀找 key | `KEYS *login*`、`KEYS *blacklist*`（注意两边都要 `*`） |
| 安全地遍历 | `redis-cli --scan`（生产用），可配合管道过滤 |
| 实时看程序发了什么命令 | 另开窗口 `redis-cli MONITOR` |

> **进阶（本实验不需要改代码）**：如果你希望 key 在 redis-cli 里显示为正常字符串，可以改用 `StringRedisTemplate`，或给 `RedisTemplate` 配置 `StringRedisSerializer`。代价是 value 只能是字符串。另一种做法是自定义 `RedisTemplate<String, Object>`，key 用 String 序列化、value 用 JSON 序列化——这是生产项目的常见配置。

### 6.2 TTL 的三种返回值

| 返回值 | 含义 |
|--------|------|
| 正数 | 剩余秒数 |
| **-1** | key 存在，但**没有设置过期时间**（永不过期）—— 本实验如果出现，说明第三个参数没生效 |
| **-2** | key **不存在**（已过期或从没写入） |

### 6.3 本实验的 key 设计

| key | value | TTL | 作用 |
|-----|-------|-----|------|
| `login:用户名` | token | 1 小时 | 记录"谁登录了、当前 token 是什么" |
| `blacklist:token` | "1" | token 剩余寿命（≤120 秒） | 已注销的 token |

**设计上的一点观察（供你思考）**：`login:` 的 TTL 是 1 小时，但 token 只有 120 秒。也就是说登录 2 分钟后 token 就失效了，而 `login:` 这个 key 还会多挂 58 分钟。
更合理的做法是让二者**保持一致的过期时间**（比如都用 1 小时，同时把 JwtUtils 的 `EXPIRE` 调大）。**这部分我没有改你的代码，留作思考题。**

### 6.4 黑名单 vs 白名单

| | 黑名单（本实验） | 白名单 |
|---|---|---|
| 存什么 | **已作废**的 token | **有效**的 token |
| 校验 | 在名单里 → 拒绝 | 不在名单里 → 拒绝 |
| 优点 | 存储小（作废的少） | 可以强制下线、控制并发登录 |
| 缺点 | 无法"强制下线" | 每个 token 都要存，存储大 |

> 注意：现在 `login:用户名` 那个 key 其实已经具备"白名单"的雏形——只要在过滤器里加一句"token ≠ login:用户名 里的值就拒绝"，就能实现"**同一账号只能在一处登录**"。

### 6.5 Redis 的持久化（了解即可）

- Redis 默认把数据放内存。重启后，**黑名单会丢失**（未持久化时）；
- 想持久化：开启 **RDB**（快照）或 **AOF**（追加日志），配置文件里改；
- 对本实验无影响（token 本来就只有 2 分钟寿命）。

---

## 七、前端文件清单（直接复制，不要手写）

| 文件路径 | 作用 |
|----------|------|
| `test9/src/main/resources/static/index.html` | 导航首页 |
| `test9/src/main/resources/static/register.html` | 注册 |
| `test9/src/main/resources/static/login.html` | 登录（存 token） |
| `test9/src/main/resources/static/users.html` | 用户列表 |
| `test9/src/main/resources/static/profile.html` | 个人中心 + 退出登录 |

---

## 八、接口测试（Apifox）

对应 `接口测试文档.md` 中的 **「POST test9登录测试」「GET test9用户中心测试」「POST test9退出登录测试」**。
这份文档里这三个接口都标了 **Bearer** 认证，正是本实验要串起来的链路。

### 8.1 登录（提取 token）

| 项 | 填写 |
|----|------|
| 方法 | `POST` |
| URL | `{{baseUrl}}/login` |
| Body（json） | `{"name": "daomanliu", "password": "123456"}` |

**后置操作 → 提取变量**：`token` = `$.data`。

### 8.2 用户中心

| 项 | 填写 |
|----|------|
| 方法 | `GET` |
| URL | `{{baseUrl}}/profile` |
| 认证 | Bearer Token → `{{token}}` |

**期望**：

```json
{
  "code": 200,
  "msg": "success",
  "data": { "id": 1, "name": "daomanliu", "password": null }
}
```

断言：`$.code` = `200`，`$.data.name` = `daomanliu`。

> 如果这里 `password` **不是 null**，而是密文，说明实体被 JPA 重新查了一次——这正好印证了步骤 8 里提到的"两次 findByName"的问题。

### 8.3 退出登录

| 项 | 填写 |
|----|------|
| 方法 | `POST` |
| URL | `{{baseUrl}}/logout` |
| 认证 | Bearer Token → `{{token}}` |
| Body | 无 |

**期望**：

```json
{ "code": 200, "msg": "退出成功", "data": null }
```

### 8.4 黑名单验证（本节的核心测试）

**紧接着 8.3**，再发送一次 8.2 的 `GET /profile`（token 不变）：

| 期望 | 说明 |
|------|------|
| HTTP **401** | 黑名单生效 |
| 响应体 `{"code":401,"msg":"未登录或权限不够"}` | |

> ⚠️ 必须**在 120 秒内**做完这套动作，否则 token 自己就过期了，你分不清是"黑名单生效"还是"token 过期"。
> 如果分不清，就看 Redis：有 `blacklist:` key → 是黑名单；没有 → 是过期。

### 8.5 建议的自动化测试场景

Apifox → **自动化测试 → 测试场景**，新建场景：

| 步骤 | 接口 | 断言 |
|------|------|------|
| 1 | `POST /login` | HTTP 200，提取 `token` |
| 2 | `GET /profile`（Bearer `{{token}}`） | HTTP 200，`$.code=200` |
| 3 | `POST /logout`（Bearer `{{token}}`） | HTTP 200，`$.code=200` |
| 4 | `GET /profile`（Bearer `{{token}}`） | **HTTP 401** |

> 第 3 步和第 4 步用的是**同一个 token**——这就是"退出后旧 token 失效"的自动化验证。

### 8.6 其它值得测的异常

| 用例 | 操作 | 期望 |
|------|------|------|
| 不带 token 访问 `/profile` | 认证选"无" | HTTP 401 |
| 不带 token 访问 `/logout` | 认证选"无" | HTTP **400**（因为 `@RequestHeader("Authorization")` 取不到值） |
| 用**已过期**的 token 访问 `/logout` | 等 3 分钟后再调 | **500**（见 FAQ 的已知问题） |
| 重复退出 | 同一 token 调两次 `/logout` | 第二次仍是 200（黑名单重复写，TTL 会被重置） |

---

## 九、实验报告要点

1. Redis 启动 + `redis-cli ping` 返回 PONG 的截图；
2. `AuthController` 里 login/logout 两处 Redis 操作的截图；
3. `JwtAuthFilter` 里黑名单判断的截图；
4. 登录后 `KEYS *login*` + `TTL` 的截图；
5. 退出后 `KEYS *blacklist*` + `TTL` 的截图；
6. 用旧 token 访问 `/profile` 返回 401 的截图（**最重要**）；
7. 等 2 分钟后黑名单 key 自动消失的截图；
8. Apifox 4 步场景全部通过的截图。

---

## 十、常见问题（FAQ）

### Q1：启动报 `Unable to connect to Redis` / `Connection refused`

- Redis 没启动：`redis-cli ping` 验证；
- 端口不对：默认 6379，改过就要在 yaml 里同步；
- 装了 Docker 版但映射端口写成别的了：`docker ps` 看端口映射；
- 用 WSL 装的，Windows 里连不上：确认 WSL 的网络映射（一般 `localhost:6379` 可以直接用）。

### Q2：登录接口 500，异常是 `RedisConnectionFailureException`

同上，Redis 没通。**本实验不能在没有 Redis 的情况下跑**。

### Q3：`KEYS login:*` 什么都查不到

JDK 序列化让 key 前面带了二进制头。用 `KEYS *login*`（两边都加 `*`）。
详见第 6.1 节。

### Q4：黑名单写了，但旧 token 还能访问

按顺序检查：

1. `JwtAuthFilter` 里是不是真的先查了 `hasKey("blacklist:" + token)`？
2. key 拼的是**完整 token** 吗？（少了前缀 / 大小写不同都会查不到）
3. 前后两次请求用的是**同一个 token** 吗？（重新登录会生成新 token）
4. Redis 里确实有那条 key 吗？（`KEYS *blacklist*`）

### Q5：`logout` 报 500

**已知问题**：`logout` 里先调 `JwtUtils.getRemainingMillis(token)`，再调 `getUsername(token)`，两者都会**解析 token**。如果 token **已过期**，解析会抛 `ExpiredJwtException`，接口就 500 了。同理，如果请求头没带 `Bearer ` 前缀，`substring(7)` 也会出问题。

**建议的健壮写法**（仅供参考，我没有改你的代码）：

```java
if (header == null || !header.startsWith("Bearer ")) {
    return Result.fail(400, "缺少 token");
}
String token = header.substring(7);
if (!JwtUtils.validate(token)) {
    return Result.success("退出成功", null);   // 已经无效了，直接当成功
}
```

### Q6：`TTL` 返回 -1

说明没设过期时间。检查是不是用了只有两个参数的 `set(key, value)`。

### Q7：`Expiration.from(Duration)` 编译不过

这是较新版本 Spring Data Redis 的 API。如果你的版本较老，改成：

```java
redisTemplate.opsForValue().set(key, value, Duration.ofHours(1));
```

（`set(K, V, Duration)` 是很早就有的重载。）

### Q8：多台服务器部署，黑名单还生效吗

生效。这正是用 Redis 而不是 `Map` 的意义——所有实例共享同一份黑名单。

### Q9：Redis 重启后黑名单丢了

默认没开持久化。对本实验影响不大（token 只有 2 分钟寿命）。生产环境要开 AOF/RDB。

### Q10：能不能直接把 token 存在 Session 里

可以，但那就退回"有状态"了，多实例部署要额外做 Session 共享（通常也是用 Redis）。绕了一圈还是 Redis。

---

## 十一、思考题

1. 如果想让"同一账号只能在一处登录"，应该怎么改？（提示：登录时先取 `login:用户名` 的旧 token，把它写进黑名单）
2. 为什么黑名单的 TTL 要用"token 剩余寿命"而不是固定 1 小时？
3. 如果把 `blacklist:` 的 value 改成存"注销时间"，能多做什么功能？（提示：审计）
4. 现在每次请求都要访问一次 Redis，如果 QPS 很高怎么办？（提示：本地缓存 + 布隆过滤器；或改用短 TTL + 白名单）
5. 为什么 `logout` 里要先算出 `remaining` 再判断 `> 0`？

---

## 附录：test9 依赖清单

在 test8 的基础上**只多了一个**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

完整清单见 `test9/pom.xml`（data-jpa + security + webmvc + mysql + lombok + jjwt + data-redis）。

---

**上一节：[实验08-数据库事务.md](./实验08-数据库事务.md)　｜　下一节：[实验10-AI大模型调用.md](./实验10-AI大模型调用.md)**
