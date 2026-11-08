# 实验六　JDBC 访问数据库（test6）

> 对应工程：`test6`　｜　来源：由 **test5** 演进而来　｜　端口：8080　｜　前置：MySQL 8

## 一、实验目标

1. 把"内存里写死的用户"换成 **MySQL 数据库里的真实用户**；
2. 会用 `JdbcTemplate` 完成 **增（insert）** 和 **查（query）**；
3. 理解 `?` 占位符与 **SQL 注入** 的关系；
4. 会用 `BCryptPasswordEncoder` 加密存储密码；
5. 会把 **角色（role）** 写进 JWT，并用 `@PreAuthorize("hasRole('ADMIN')")` 做接口级授权；
6. 会用 Apifox 测出 **200 / 401 / 403** 三种结果的差异。

---

## 二、原理速览

### 2.1 从 test5 到 test6 改了什么

| 模块 | test5 | test6 |
|------|-------|-------|
| 用户来源 | 代码里 `new User("test","test")` | MySQL 的 `users` 表 |
| 密码 | 明文比较 | **BCrypt 加密**后存库，用 `matches` 校验 |
| 权限 | 写死 `"USER"` | 从 `user_role` 表查出角色，写进 token |
| 注册 | 没有 | 新增 `POST /register` |
| 数据访问 | 无 | 新增 `UserDao`（JdbcTemplate） |

### 2.2 JdbcTemplate 是什么

Java 原生 JDBC 要写一堆样板代码：

```java
Connection conn = DriverManager.getConnection(url, user, pwd);
PreparedStatement ps = conn.prepareStatement("select ... where name = ?");
ps.setString(1, name);
ResultSet rs = ps.executeQuery();
while (rs.next()) { ... }
rs.close(); ps.close(); conn.close();
```

Spring 用 `JdbcTemplate` 把它压成一行：

```java
List<User> list = jdbc.query("select id,name,password from users where name = ?",
        (rs, i) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("password")), name);
```

**连接获取、参数绑定、结果集遍历、资源关闭，全部由模板完成。**

### 2.3 为什么必须用 `?` 而不是字符串拼接

```java
// ❌ 危险：name 传 "x' or '1'='1" 就能绕过密码
jdbc.query("select * from users where name = '" + name + "'", ...);

// ✅ 安全：? 会被当成"值"，不是"SQL 片段"
jdbc.query("select * from users where name = ?", ..., name);
```

这叫 **预编译 / 参数绑定**，是防 SQL 注入的第一道防线。**本实验所有 SQL 都用 `?`。**

### 2.4 BCrypt 是什么

- 单向哈希，**不可逆**；
- 每次加密同一个密码，结果都不一样（因为带随机盐）；
- 所以校验不能用 `equals`，必须用 `encoder.matches(明文, 密文)`。

---

## 三、实验准备：建库建表

### 3.1 说明（重要）

工程里有个 `test6/src/main/resources/data.sql`，但 **Spring Boot 不会自动执行它**：

- Spring Boot 默认 `spring.sql.init.mode=embedded`，只对 H2 这类**内存数据库**执行初始化脚本；
- 我们用的是 **MySQL（外部数据库）**，所以不会执行；
- 而且脚本里有 `CREATE DATABASE`，真要自动执行也会报错。

**正确做法：把下面的 SQL 复制到 Navicat / DataGrip / 命令行里手动执行一次。**

### 3.2 建库建表 SQL

```sql
CREATE DATABASE test6 DEFAULT CHARSET utf8mb4;
USE test6;

CREATE TABLE users (
    id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    name     VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE roles (
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
```

**三张表的含义：**

| 表 | 作用 | 关键点 |
|----|------|--------|
| `users` | 用户 | `name` 唯一；`password` 存的是 **BCrypt 密文**（长 60 字符，所以 varchar(100)） |
| `roles` | 角色字典 | 例如 `ADMIN`、`USER` |
| `user_role` | 用户-角色关联（多对多） | 联合主键 `(user_id, role_id)`，防止重复授权 |

> ⚠️ 实验七、八、九 **共用这个 `test6` 库和这三张表**，不要重复建，也不要在实验七里让 Hibernate 自动建表（后面会讲）。

### 3.3 验证

```sql
SHOW TABLES;      -- 应看到 roles, user_role, users
SELECT * FROM users;   -- 一开始是空表
```

---

## 四、实验步骤

### 步骤 1：复制 test5 得到 test6

按实验五"步骤 1"的方法复制，注意改：

- `pom.xml` 的 `artifactId`
- 包名 `com.daomanliu.test6`
- 启动类 `Test6Application`
- `spring.application.name`
- `HelloController`和`LoginController`可以不用了

### 步骤 2：加依赖

**文件路径**：`test6/pom.xml`

在 test5 的依赖基础上追加这三个：

```xml
<!-- JDBC 支持：自动配置 DataSource + JdbcTemplate -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<!-- MySQL 驱动（8.x 的坐标是 com.mysql:mysql-connector-j） -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- JDBC 的测试支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc-test</artifactId>
    <scope>test</scope>
</dependency>
```

> 老教程里驱动坐标是 `mysql:mysql-connector-java`，**8.x 已经改成 `com.mysql:mysql-connector-j`**，用老坐标会下载不到或被废弃。

### 步骤 3：配置数据库连接

**文件路径**：`test6/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: test6
  datasource:
    url: jdbc:mysql://localhost:3306/test6?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**参数逐项解释：**

| 参数 | 作用 | 不改会怎样 |
|------|------|-----------|
| `jdbc:mysql://localhost:3306/test6` | 连本机 3306 端口的 `test6` 库 | 连不上 |
| `useUnicode=true&characterEncoding=utf8` | 中文不乱码 | 中文姓名变 `???` |
| `serverTimezone=Asia/Shanghai` | 指定时区 | 8.x 驱动不加会报 `The server time zone value ... is unrecognized` |
| `username` / `password` | **改成你自己的 MySQL 账号密码** | 启动报 `Access denied for user` |
| `driver-class-name` | 驱动类名 | 一般可省略，写上更明确 |

> 🔴 **一定要把 `password` 改成你本机的密码**，否则启动就失败。

### 步骤 4：实体类

**文件路径**：`test6/src/main/java/com/daomanliu/test6/entity/User.java`

```java
package com.daomanliu.test6.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;
    private String name;
    private String password;
}
```

**文件路径**：`test6/src/main/java/com/daomanliu/test6/entity/Role.java`

```java
package com.daomanliu.test6.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Role {
    private Long id;
    private String name;
}
```

> 这两个类现在还只是"普通 Java 类"，**没有任何数据库注解**——因为 JDBC 是手写 SQL，实体只是用来装结果。到实验七用 JPA 时，才会加上 `@Entity`。

### 步骤 5：写数据访问层 `UserDao`

**文件路径**：`test6/src/main/java/com/daomanliu/test6/dao/UserDao.java`

新建包 `dao`，建类 `UserDao`：

```java
package com.daomanliu.test6.dao;

import com.daomanliu.test6.entity.Role;
import com.daomanliu.test6.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserDao {
    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public User findByName(String name) {
        List<User> list = jdbc.query("select id, name, password from users where name = ? ",
                (rs, i) -> new User(rs.getLong("id"),  rs.getString("name"), rs.getString("password")), name);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insert(String name, String encodedPassword) {
        return jdbc.update("insert into users (name, password) values (?, ?)", name, encodedPassword);
    }

    public List<Role> findRolesByUserId(Long userId) {
        return jdbc.query("SELECT r.id, r.name FROM roles r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                (rs, i) -> new Role(rs.getLong("id"), rs.getString("name")),
                userId
                );
    }

    public List<User> findAll() {
        return jdbc.query("select id, name, password from users",
                (rs, i) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("password")));
    }

}
```

**逐段解释：**

#### ① 注入 `JdbcTemplate`

```java
private final JdbcTemplate jdbc;
public UserDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
```

- 加了 `spring-boot-starter-jdbc` 后，Spring 会根据 `spring.datasource.*` **自动创建** `JdbcTemplate` Bean；
- 我们只要在构造方法里声明参数，就会自动注入（**构造器注入是官方推荐写法**）；
- `@Repository` 让这个类被扫描成 Bean。

#### ② `query` 的三个参数

```java
jdbc.query(SQL, RowMapper, 可变参数...)
```

| 参数 | 说明 |
|------|------|
| SQL | 用 `?` 占位 |
| `(rs, i) -> new User(...)` | **RowMapper**：把结果集"当前这一行"转成对象。`rs` 是 ResultSet，`i` 是行号（用不上） |
| `name` | 依次填进 `?` 的值 |

> 结果一定是 `List`，哪怕只有一条。所以 `findByName` 里做了 `isEmpty() ? null : get(0)`，因为用户名唯一，最多一条。

#### ③ `update` 用于增删改

```java
jdbc.update("insert into users (name, password) values (?, ?)", name, encodedPassword);
```
**返回值是受影响的行数**（正常是 1）。

> 注意：这里第二个参数传的是 **已经加密好的密码**，方法名 `encodedPassword` 就是提醒你不要传明文。

#### ④ 联表查询角色

```sql
SELECT r.id, r.name
FROM roles r
JOIN user_role ur ON r.id = ur.role_id
WHERE ur.user_id = ?
```

一个用户可能有多个角色，所以返回 `List<Role>`。

#### ⑤ `findAll`

用于"用户列表"接口，注意查询后 **要把 password 置空再返回**（Controller 里做）。

### 步骤 6：写认证控制器（注册 + 登录）

**文件路径**：`test6/src/main/java/com/daomanliu/test6/controller/AuthController.java`

```java
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
```

**核心逻辑讲解：**

#### ① 注册三步

| 步 | 代码 | 意义 |
|----|------|------|
| 1 | 判空 | 防止前端漏传 |
| 2 | `findByName != null` → 400 | 用户名唯一，先查再插（体验更好） |
| 3 | `encoder.encode(明文)` 后 insert | **绝不能存明文** |

> ‼️ **一定要 encode 再存**。如果直接存明文，登录时 `encoder.matches` 会抛 `IllegalArgumentException: Encoded password does not look like BCrypt`。

#### ② 登录三步

| 步 | 代码 | 意义 |
|----|------|------|
| 1 | `findByName` 查用户 | 不存在就是"用户名或密码错误"（**不要提示"用户不存在"**，避免被枚举账号） |
| 2 | `encoder.matches(明文, 密文)` | BCrypt 校验 |
| 3 | 查角色 → 加 `"ROLE_"` 前缀 → 签 token | **关键**，见下 |

#### ③ 为什么角色要加 `ROLE_` 前缀

Spring Security 的 `hasRole("ADMIN")` **内部会自动拼上 `ROLE_`**，实际比对的是权限字符串 `"ROLE_ADMIN"`。
所以 token 里的权限必须是 `"ROLE_ADMIN"`，代码里就统一 `map(r -> "ROLE_" + r.getName())`。

> 如果数据库里存的是 `ROLE_ADMIN`，这里就不能再加前缀，否则变成 `ROLE_ROLE_ADMIN`，永远 403。**前缀只加一次，位置要统一。**

### 步骤 7：改造 `JwtUtils`，让 token 携带角色

**文件路径**：`test6/src/main/java/com/daomanliu/test6/common/JwtUtils.java`

```java
package com.daomanliu.test6.common;

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
}
```

**和 test5 的差异：**

| 变化 | 说明 |
|------|------|
| `generate(username, roles)` 多一个参数 | 登录时把角色写进 token |
| `.claim("roles", roles)` | 自定义 claim，payload 里多一个 `roles` 数组 |
| `getRoles(token)` | 过滤器要用它把权限放回 SecurityContext |
| `EXPIRE` 改成 120 秒 | 给测试留点时间 |
| 抽了个 `parse` | `validate`/`getUsername`/`getRoles` 复用 |

> ⚠️ 注意：角色变了（比如刚被授予 ADMIN）**旧 token 里还是老角色**，必须**重新登录**才能生效。

### 步骤 8：改造过滤器，把角色放进去

**文件路径**：`test6/src/main/java/com/daomanliu/test6/config/JwtAuthFilter.java`

```java
package com.daomanliu.test6.config;

import com.daomanliu.test6.common.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
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

**和 test5 的差异只有三行：**

```diff
  String username = JwtUtils.getUsername(token);
- UsernamePasswordAuthenticationToken auth =
-     new UsernamePasswordAuthenticationToken(username, null,
-         List.of(new SimpleGrantedAuthority("USER")));
+ List<String> roles = JwtUtils.getRoles(token);
+ var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
+ var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
```

**要点：**

- `SimpleGrantedAuthority` 的构造参数必须和 `hasRole` 的规则对得上（即 `ROLE_XXX`）；
- token 里 roles 可能是 `null`（老 token / 没角色的用户），严谨写法应该判空，本工程直接用了，实测空列表不会崩。

### 步骤 9：配置 Security

**文件路径**：`test6/src/main/java/com/daomanliu/test6/config/SecurityConfig.java`

```java
package com.daomanliu.test6.config;

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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/*.html").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"msg\":\"未登录或token无效\"}");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**和 test5 的差异：**

| 变化 | 说明 |
|------|------|
| 新增 `@EnableMethodSecurity` | **必须加**，否则 @PreAuthorize 不生效（会变成所有登录用户都能访问！） |
| 白名单加了 `/register` | 注册接口不能要 token |
| 白名单用 `"/*.html"` 通配 | 一次放行 static 下所有页面（login、register、users、index） |

> 💡 `@EnableMethodSecurity` 是"方法级安全"的开关。**忘了加 = 权限形同虚设**，这是本节最危险的坑。

### 步骤 10：写用户列表接口（带权限校验）

**文件路径**：`test6/src/main/java/com/daomanliu/test6/controller/UserController.java`

```java
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
```

**逐行解释：**

| 代码 | 说明 |
|------|------|
| `@PreAuthorize("hasRole('ADMIN')")` | **方法执行前**检查当前用户是否有 `ROLE_ADMIN`；没有就抛 `AccessDeniedException` → **403** |
| `users.forEach(u -> u.setPassword(null))` | 返回前把密码清空。**接口绝对不能返回密码哈希**（虽然是哈希，也不该给前端） |
| 返回类型 `Result<?>` | 因为 data 是 `List<User>`，用通配符省得写泛型 |

> `hasRole('ADMIN')` = 检查 `ROLE_ADMIN`；`hasAuthority('ROLE_ADMIN')` 等价。别写成 `hasRole('ROLE_ADMIN')`。

### 步骤 11：放前端页面

**文件路径**：以下 4 个文件直接复制，**不用手写**：

| 文件路径 | 作用 |
|----------|------|
| `test6/src/main/resources/static/index.html` | 首页导航（登录 / 注册 / 用户列表） |
| `test6/src/main/resources/static/login.html` | 登录，成功把 token 存 `localStorage` |
| `test6/src/main/resources/static/register.html` | 注册 |
| `test6/src/main/resources/static/users.html` | 用户列表，请求时自动带 `Authorization: Bearer token` |

**访问地址**：`http://localhost:8080/index.html`

### 步骤 12：启动并验证

1. 运行 `Test6Application`。启动失败请先看 FAQ 的"连不上数据库"。
2. 打开 `http://localhost:8080/register.html`，注册一个账号，例如 `daomanliu` / `123456` → 提示"注册成功"。
3. 去数据库里看一眼：

```sql
SELECT * FROM users;
-- 会看到 password 是一长串以 $2a$10$ 开头的密文，这就是 BCrypt
```

4. 打开 `http://localhost:8080/login.html`，登录 → 提示成功并保存 token。
5. 打开 `users.html` → 此时**应该看到 403（没有权限）**，因为这个新用户还没有 ADMIN 角色。
6. 按"步骤 13"给用户授权，**退出后重新登录**，再看 `users.html` → 能看到用户列表了。

### 步骤 13：授予管理员角色（必做，否则 /users 永远 403）

在 MySQL 里执行（把用户名换成你注册的那个）：

```sql
-- 1. 新增 ADMIN 角色（如果已存在会报唯一约束，忽略即可）
INSERT INTO roles (name) VALUES ('ADMIN');

-- 2. 给 daomanliu 这个用户授予 ADMIN
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.name = 'daomanliu' AND r.name = 'ADMIN';

-- 3. 确认
SELECT u.name, r.name
FROM users u
JOIN user_role ur ON u.id = ur.user_id
JOIN roles r ON r.id = ur.role_id;
```

> ⚠️ 授权后 **必须重新登录**！因为角色是在**登录时**写进 token 的，旧 token 里没有 ADMIN。

---

## 五、前端文件清单（直接复制，不要手写）

| 文件路径 | 作用 | 调用的接口 |
|----------|------|-----------|
| `test6/src/main/resources/static/index.html` | 导航首页 | 无 |
| `test6/src/main/resources/static/register.html` | 注册 | `POST /register` |
| `test6/src/main/resources/static/login.html` | 登录并存 token | `POST /login` |
| `test6/src/main/resources/static/users.html` | 用户列表（带 token） | `GET /users` |

---

## 六、接口测试（Apifox）

对应 `接口测试文档.md` 中的 **「POST test6注册测试」「POST test6登录测试」「GET test6用户列表」**。

### 6.1 注册

| 项 | 填写 |
|----|------|
| 方法 | `POST` |
| URL | `{{baseUrl}}/register` |
| Body（json） | `{"name": "xujxli", "password": "123456"}` |

**期望返回**：

```json
{ "code": 200, "msg": "注册成功", "data": null }
```

断言：`$.code` = `200`。

**重复注册同一用户名**：`{"code":400,"msg":"用户名已存在","data":null}`。

### 6.2 登录（提取 token）

| 项 | 填写 |
|----|------|
| 方法 | `POST` |
| URL | `{{baseUrl}}/login` |
| Body | `{"name": "daomanliu", "password": "123456"}` |

**期望返回**：

```json
{ "code": 200, "msg": "登录成功", "data": "eyJhbGciOiJIUzI1NiJ9..." }
```

**后置操作 → 提取变量**：变量名 `token`，表达式 `$.data`。

> 想看看 token 里到底装了什么？把 `data` 的值复制到 <https://jwt.io> 的 Debugger 里，就能看到 payload：
> `{"sub":"daomanliu","roles":["ROLE_ADMIN"],"iat":...,"exp":...}`
> **注意：网上解密工具会让你把 token 贴出去，实验用可以，真实 token 不要贴。**

### 6.3 用户列表

| 项 | 填写 |
|----|------|
| 方法 | `GET` |
| URL | `{{baseUrl}}/users` |
| 认证 | Bearer Token → `{{token}}` |

**有 ADMIN 角色时期望**：

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    { "id": 1, "name": "daomanliu", "password": null },
    { "id": 2, "name": "xujxli", "password": null }
  ]
}
```

**没有 ADMIN 角色时**：HTTP **403**（响应体可能是空的，或 Spring 默认的 Access Denied JSON）。

### 6.4 三种状态码对照（重点）

| 场景 | 请求 | 状态码 | 说明 |
|------|------|--------|------|
| 正常 | 带 ADMIN token 访问 `/users` | **200** | 有认证 + 有权限 |
| 未登录 | 不带 token 访问 `/users` | **401** | 没认证，被 `authenticationEntryPoint` 拦下 |
| 权限不足 | 带普通用户 token 访问 `/users` | **403** | 认证了，但 `@PreAuthorize` 不通过 |

> 这三行是实验报告里最有价值的部分，**一定要截图**。

### 6.5 建议的自动化场景

Apifox → 测试场景：

1. 注册（随机用户名，用 `${randomUsername}` 之类的内置变量）
2. 登录 → 提取 token
3. 访问 `/users` → 断言 **403**（新用户没角色）
4. 去数据库手工授权 → 重新登录 → 断言 **200**

---

## 七、实验报告要点

1. MySQL 里三张表的结构截图（`DESC users;`）；
2. 注册后 `select * from users` 的截图（**要能看出密码是密文**）；
3. `UserDao.java` 与 `AuthController.java` 代码截图；
4. 未授权用户访问 `/users` 返回 403 的截图；
5. 授权 + 重新登录后返回 200 的截图；
6. Apifox 三条接口的截图。

---

## 八、常见问题（FAQ）

### Q1：启动报 `Access denied for user 'root'@'localhost'`

`application.yaml` 里的 `password` 没改成你自己的 MySQL 密码。

### Q2：启动报 `Unknown database 'test6'`

库没建。执行步骤 3.2 的 SQL。

### Q3：启动报 `The server time zone value '...' is unrecognized`

URL 后面少了 `&serverTimezone=Asia/Shanghai`。

### Q4：登录一直提示"用户名或密码错误"，但密码是对的

最常见原因：**注册时没有 `encoder.encode`**，库里存的是明文，导致 `matches` 抛异常或永远 false。可以这样确认：

```sql
SELECT name, LEFT(password, 7) FROM users;
-- 正常应该看到 $2a$10$ 开头
```

如果看到的是明文，清空表重新注册（或手工把密码改成 BCrypt 值）。

### Q5：`/users` 一直 403

按顺序查：
1. 有没有加 `@EnableMethodSecurity`？
2. 数据库里 `user_role` 有没有关联记录？
3. token 里的 roles 是不是 `ROLE_ADMIN`（用 jwt.io 解开看）？
4. **授权后重新登录了吗**？（旧 token 里没有新角色）
5. `hasRole('ADMIN')` 是不是误写成了 `hasRole('ROLE_ADMIN')`？

### Q6：报 403 而不是 401

说明认证通过了（过滤器认识你），但授权被拒。这正是 403 的定义。

### Q7：报错 `Access Denied` 但我是管理员

同上，检查 token 里的 roles 字段。

### Q8：中文用户名存进去变成问号

连接串里加 `useUnicode=true&characterEncoding=utf8`，并且建库时用 `DEFAULT CHARSET utf8mb4`。

### Q9：注册时用户名重复，程序报 500

本工程的 `findByName` 已经先查过一次，正常不会 500。**并发**注册同一个名字才可能撞唯一约束（`DuplicateKeyException`）。可以在全局异常处理里捕获它返回 400（属于进阶内容）。

### Q10：密码能不能解密回来看？

不能。BCrypt 是单向哈希。忘记密码只能"重置"，不能"找回"。这是设计如此。

---

## 九、思考题

1. 如果把 `findByName` 的 SQL 改成字符串拼接，写一个能绕过登录的输入试试（**只在本地实验环境尝试**）。
2. 为什么 `user_role` 用联合主键而不是自增 id 主键？
3. `@PreAuthorize` 除了 `hasRole`，还支持哪些表达式？（提示：`hasAnyRole`、`#id == authentication.name`）
4. 现在每次请求都要查一次数据库吗？（答案：不是，角色在 token 里）这样设计有什么优缺点？（缺点：角色变更不能立即生效——实验九用 Redis 解决 token 时效问题，但角色变更仍需重新登录）

---

## 附录：test6 依赖清单

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
</dependencies>
```

---

**上一节：[实验05-权限管理与JWT.md](./实验05-权限管理与JWT.md)　｜　下一节：[实验07-JPA访问数据库.md](./实验07-JPA访问数据库.md)**
