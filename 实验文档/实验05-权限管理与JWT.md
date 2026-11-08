# 实验五　权限管理（test5）

> 对应工程：`test5`　｜　来源：由 **test2** 改造而来　｜　端口：8080

## 一、实验目标

1. 分清 **认证（Authentication，"你是谁"）** 和 **授权（Authorization，"你能干什么"）**；
2. 理解 **JWT（JSON Web Token）** 的结构和无状态认证思想；
3. 会用 Spring Security 保护接口：没有 token 一律 401；
4. 会写登录接口：校验账号密码 → 签发 token；
5. 会写过滤器 `JwtAuthFilter`：每个请求校验 token → 放进安全上下文；
6. 会用 Apifox 完成"登录 → 提取 token → 带 token 访问"的完整链路测试。

---

## 二、原理速览

### 2.1 传统 Session vs JWT

| | 传统 Session | JWT（本实验） |
|---|---|---|
| 登录成功后 | 服务器内存里存一份会话，发给浏览器一个 `JSESSIONID` Cookie | 服务器**什么都不存**，直接签发一个字符串给前端 |
| 后续请求 | 浏览器自动带 Cookie | 前端手动在 `Authorization` 头里带 token |
| 服务器怎么认人 | 拿 Cookie 去内存里查会话 | 用密钥**验签**，签名对就认 |
| 多台服务器 | 需要共享 session（Redis 等） | 天然支持，每台都能独立验签 |
| 退出登录 | 删掉服务端 session | **没法"删"**（无状态）→ 要用黑名单（实验九） |

### 2.2 JWT 长什么样

一个 JWT 是 **三段**，用 `.` 分隔：

```text
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE3MDAwMDAwNjB9 . 4xQm...（签名）
      ①Header                     ②Payload                             ③Signature
```

| 段 | 内容 | 是否加密 |
|----|------|----------|
| ① Header | 算法（HS256）等 | ❌ 只是 Base64，**任何人可解** |
| ② Payload | `sub`(用户名)、`iat`(签发时间)、`exp`(过期时间)、自定义 claim | ❌ 只是 Base64，**任何人可解** |
| ③ Signature | 用服务端密钥对前两段算的签名 | ✅ 无法伪造（没有密钥算不出来） |

> 🔐 **结论：JWT 只能保证"没被篡改"，不能保密。** 所以**永远不要把密码等敏感信息放进 JWT**。

### 2.3 一次带 token 的请求

```text
前端：Authorization: Bearer eyJhbGci...

         ▼
JwtAuthFilter.doFilterInternal()
   ├─ 有 "Bearer " 前缀？ → 取出 token
   ├─ JwtUtils.validate(token) 验签 + 检查过期
   │     ├─ 失败 → 什么都不做（SecurityContext 是空的）
   │     └─ 成功 → 把用户名放进 SecurityContext
   ▼
SecurityConfig 的规则：/hello 需要 authenticated
   ├─ SecurityContext 有认证信息 → 放行 → HelloController 返回 "Hello World"
   └─ 没有 → authenticationEntryPoint → 返回 401 + {"code":401,...}
```

---

## 三、实验步骤

### 步骤 1：以 test2 为基础建 test5

**方式 A（推荐，省事）**：把 `test2` 整个目录复制一份，改名为 `test5`，然后：

1. 改 `pom.xml` 里的 `artifactId`、`name`、`description` 为 `test5`；
2. 把 `src/main/java/com/daomanliu/test2` 目录改名为 `test5`；
3. 把每个 .java 文件的 `package` 和 `import` 里的 `test2` 全部替换成 `test5`（IDEA 里 `Ctrl+Shift+R` 全局替换）；
4. 把启动类改名：右键 `Test2Application.java` → `Refactor → Rename` → `Test5Application`（**类名和文件名一起改**），顺便把里面 `Test2Application.class` 的引用改掉；
5. 把 `application.yaml` 里的 `spring.application.name` 改成 `test5`（或者按源码改回 `application.properties`）。

**方式 B**：新建工程 `test5`，把 test2 的三个类复制过来改包名。

> 后面的实验六~九都是这么"复制上一个工程"，熟练之后 5 分钟就能搞定。

### 步骤 2：加依赖

**文件路径**：`test5/pom.xml`

在 `<dependencies>` 里追加：

```xml
<!-- Spring Security：认证 + 授权框架 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT 三件套，版本必须一致，0.11.5 -->
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
```

**为什么要三个 jjwt 包？**

| 包 | 作用 | 作用域 |
|----|------|--------|
| `jjwt-api` | 你写代码时用的接口（`Jwts`、`Claims`） | 编译 + 运行 |
| `jjwt-impl` | 具体实现 | 运行期 |
| `jjwt-jackson` | 用 Jackson 序列化 JSON 部分 | 运行期 |

> 只加 `jjwt-api` 会在运行时报 `Unable to find an implementation using ...`。

### 步骤 3：配置文件

**文件路径**：`test5/src/main/resources/application.properties`

```properties
spring.application.name=test5
```

> 注意 test5 用的是 `.properties`（键值对无缩进），不是 `.yaml`。两种格式效果一样，不要混用同一个文件名。

### 步骤 4：复用 `Result`和`HelloController`

**文件路径**：`test5/src/main/java/com/daomanliu/test5/common/Result.java`

和实验二完全一样，只改包名：

```java
package com.daomanliu.test5.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<T>(200, "success", data);
    }
    public static <T> Result<T> success(String msg, T data) {
        return new Result<T>(200, msg, data);
    }
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>(code, msg, null);
    }
}
```

**文件路径**：`src/main/java/com/daomanliu/test1/controller/HelloController.java`  
和实验一完全一样，只改包名：
```java
package com.daomanliu.test5.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }
}

```

### 步骤 5：写 JWT 工具类

**文件路径**：`test5/src/main/java/com/daomanliu/test5/common/JwtUtils.java`

在 `common` 包里新建类 `JwtUtils`：

```java
package com.daomanliu.test5.common;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class JwtUtils {
    private static final Key KEY = Keys.hmacShaKeyFor("daomanliu-test5-jwt-secret-key-2026".getBytes());
    private static final long EXPIRE = 60 * 1000;

    public static String generate(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(KEY)
                .compact();
    }

    public static boolean validate(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public static String getUsername(String token) {
        return Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody().getSubject();
    }
}
```

**逐段解释：**

#### ① 密钥

```java
private static final Key KEY = Keys.hmacShaKeyFor("daomanliu-test5-jwt-secret-key-2026".getBytes());
```

- HS256 要求密钥 **至少 256 位 = 32 字节**。这串字符是 34 个字符，刚好够。
- **太短会在启动/签发时直接抛 `WeakKeyException`**——这是新手最常踩的坑。
- 真实项目里这个密钥要放配置/环境变量，并且**不能泄露**：谁拿到密钥，谁就能伪造 token。

#### ② 过期时间

```java
private static final long EXPIRE = 60 * 1000;   // 60 秒！
```

**只有 60 秒**（实验六起改成 120 秒）。做实验时登录完要**马上**去访问 `/hello`，否则会 401。

#### ③ 签发

| 方法 | 写进 payload 的内容 |
|------|---------------------|
| `setSubject(username)` | `sub` 字段 = 用户名 |
| `setIssuedAt(new Date())` | `iat` = 现在 |
| `setExpiration(...)` | `exp` = 现在 + 60 秒 |
| `signWith(KEY)` | 用密钥算签名 |
| `compact()` | 拼成 `xxx.yyy.zzz` 字符串 |

#### ④ 校验

```java
Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token);
```
这一句同时做三件事：**验签、检查是否过期、检查格式**。任何一项不过就抛 `JwtException`，被 catch 住返回 false。

> `parseClaimsJws` 会抛多种子类异常（`ExpiredJwtException`、`SignatureException`、`MalformedJwtException`），统一 catch 父类 `JwtException` 即可。

### 步骤 6：实体类（沿用 test2）

**文件路径**：`test5/src/main/java/com/daomanliu/test5/entity/User.java`

```java
package com.daomanliu.test5.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String name;
    private String password;
}
```

### 步骤 7：改造登录接口，返回 token

**文件路径**：`test5/src/main/java/com/daomanliu/test5/controller/LoginController.java`

```java
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
```

**和 test2 的差异只有两行：**

```diff
- public Result<User> login(@RequestBody User user) {
+ public Result<String> login(@RequestBody User user) {
      if (test.equals(user)) {
-         return Result.success("登录成功", user);
+         String token = JwtUtils.generate(user.getName());
+         return Result.success("登录成功", token);
      }
```

返回体变成：

```json
{ "code": 200, "msg": "登录成功", "data": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0Ii..." }
```

前端拿到 `data` 这个字符串，就存进 `localStorage`。

### 步骤 8：配置 Spring Security

**文件路径**：`test5/src/main/java/com/daomanliu/test5/config/SecurityConfig.java`

新建包 `config`，建类 `SecurityConfig`：

```java
package com.daomanliu.test5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login.html").permitAll()
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

**逐项解释（这一段是实验五的核心，每一行都要懂）：**

| 代码 | 作用 | 不写会怎样 |
|------|------|-----------|
| `@EnableWebSecurity` | 开启 Security 的自动配置 | 整个配置不生效 |
| `@RequiredArgsConstructor` | Lombok 给 `final` 字段生成构造方法 → 注入 `JwtAuthFilter` | 编译不过（final 字段没初始化） |
| `csrf(...).disable()` | 关掉 CSRF 防护 | 所有 POST 请求被 403（因为前端没带 CSRF token）。**前后端分离 + token 认证的项目必须关** |
| `SessionCreationPolicy.STATELESS` | 不创建、不使用 HttpSession | 每个请求都会新建 session，性能差且与 token 机制冲突 |
| `requestMatchers("/login", "/login.html").permitAll()` | 这两个路径不需要 token | 登录接口自己也被拦住，永远登不进去（死循环） |
| `anyRequest().authenticated()` | 其余全部要认证 | 接口裸奔 |
| `authenticationEntryPoint(...)` | 未认证时的响应：HTTP 401 + 自定义 JSON | 返回 Spring 默认的登录页/一坨 HTML，前端没法处理 |
| `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` | 把我们的过滤器插在用户名密码过滤器**之前** | token 解析太晚，请求已经被拒绝了 |

> 💡 关于 `"/login.html"`：静态页面本身也要放行，否则连登录页都打不开。这也是为什么这里把 HTML 也写进了白名单。

### 步骤 9：写 JWT 过滤器

**文件路径**：`test5/src/main/java/com/daomanliu/test5/config/JwtAuthFilter.java`

在 `config` 包里建类 `JwtAuthFilter`：

```java
package com.daomanliu.test5.config;

import com.daomanliu.test5.common.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (JwtUtils.validate(token)) {
                String username = JwtUtils.getUsername(token);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("USER")));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

**逐行解释：**

| 代码 | 说明 |
|------|------|
| `extends OncePerRequestFilter` | Spring 提供的基类，保证**一次请求只执行一遍** |
| `@Component` | 交给 Spring 管理，才能注入到 SecurityConfig 里 |
| `request.getHeader("Authorization")` | 取请求头，值是 `Bearer eyJhbGci...` |
| `header.substring(7)` | 去掉 `"Bearer "`（7 个字符） |
| `JwtUtils.validate(token)` | 验签 + 查过期 |
| `new SimpleGrantedAuthority("USER")` | **给这个请求一个权限标记**。注意：这里写死了 `USER`，还没有角色概念（实验六会改成从 token 里读角色） |
| `SecurityContextHolder.getContext().setAuthentication(auth)` | **把"我已认证"写进当前线程的上下文**。只有这一句执行了，后面的 `authenticated()` 才会放行 |
| `filterChain.doFilter(...)` | **必须调用**，否则请求就断在这了（这是过滤器新手最容易忘的一行） |
| 整体没有 `else { 拒绝 }` | token 不合法时**什么都不做**，让后面的 Security 规则去拒绝，逻辑更清晰 |

### 步骤 10：放前端页面

**文件路径**：`test5/src/main/resources/static/login.html`

不用写，直接复制。它在 test2 的基础上多了两个能力：

| 行为 | 对应代码 |
|------|----------|
| 登录成功后保存 token | `localStorage.setItem('token', data.data)` |
| 点"访问 /hello"时带上 token | `fetch('/hello', { headers: { 'Authorization': 'Bearer ' + token } })` |
| 没有 token 时给出提示 | `if (!token) { msg.textContent = '未登录，没有 token，无权访问'; }` |
| 根据状态码区分成功/失败 | `if (resp.ok) 成功 else 显示 resp.status` |

### 步骤 11：启动并验证（四个场景，逐个走一遍）

运行 `Test5Application`。控制台**不会**再打印 Spring Security 默认的随机密码（因为我们定义了 `SecurityFilterChain`），这是正常的。

打开浏览器 `http://localhost:8080/login.html`：

**场景 A：没有 token 直接访问受保护接口**

1. 先**不要登录**，直接点"访问 /hello" → 页面提示"未登录，没有 token，无权访问"（这是前端拦的）；
2. 想验证后端也拦得住，用地址栏直接访问 `http://localhost:8080/hello` → 浏览器显示：

```json
{"code":401,"msg":"未登录或token无效"}
```

> 这个 JSON 就是 `authenticationEntryPoint` 里手写的那段字符串。**看到它就说明你的 401 处理生效了。**

**场景 B：登录成功 → 带 token 访问**

1. 输入 `test` / `test`，点登录 → 提示"登录成功（token 已保存）"；
2. **立刻**（60 秒内）点"访问 /hello" → 显示：

```text
有权限！服务器返回：Hello World
```

**场景 C：token 过期**

1. 登录后**等 70 秒**；
2. 再点"访问 /hello" → 显示 `401 无权限：{"code":401,"msg":"未登录或token无效"}`。

**场景 D：token 被篡改**

1. 登录后，按 `F12` 打开控制台，执行：

```javascript
localStorage.setItem('token', localStorage.getItem('token').slice(0, -3) + 'xxx');
```

2. 再点"访问 /hello" → 401（签名对不上）。

---

## 四、前端文件清单（直接复制，不要手写）

| 文件路径 | 作用 | 调用的接口 |
|----------|------|-----------|
| `test5/src/main/resources/static/login.html` | 登录 + 保存 token + 带 token 访问 /hello | `POST /login`、`GET /hello` |

**访问地址**：`http://localhost:8080/login.html`

---

## 五、接口测试（Apifox）

对应 `接口测试文档.md` 中的 **「POST test5登录测试」「GET test5JWT验证」**。
这份接口文档的认证方式标注为 **Bearer**，就是本实验要配的东西。

### 5.1 登录，并自动提取 token

1. 新建接口：
   - 方法 `POST`，URL `{{baseUrl}}/login`
   - Body（json）：`{"name": "test", "password": "test"}`
2. 发送，返回：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE3MDAwMDAwNjB9.xxxxx"
}
```

3. 关键一步：**后置操作 → 提取变量**
   - 变量名：`token`
   - 表达式：`$.data`

> 之后所有接口都能用 `{{token}}` 引用它。

### 5.2 带 token 访问 /hello

1. 新建接口：方法 `GET`，URL `{{baseUrl}}/hello`
2. **认证** 页签 → 选择 `Bearer Token` → 填入 `{{token}}`
   （Apifox 会自动加 `Authorization: Bearer xxx` 头）
3. 发送，期望：

```text
状态码 200
Body: Hello World
```

### 5.3 异常用例

| 用例 | 操作 | 期望 |
|------|------|------|
| 不带 token | 认证方式选"无"，请求 `/hello` | HTTP **401** + `{"code":401,"msg":"未登录或token无效"}` |
| token 乱填 | 认证 Bearer 填 `abc.def.ghi` | HTTP **401** |
| 过期 token | 登录后等 70 秒再请求 | HTTP **401** |
| 密码错误 | 登录接口传 `{"name":"test","password":"x"}` | HTTP **200** + body `code=401`（注意区别！） |

> **这张对比表是本实验最值得写进报告的**：同一个"401"，一个是 HTTP 状态码（Security 层拒绝），一个是业务码（Controller 里返回）。Apifox 断言时要分清：
> - 断言 **HTTP 状态码 = 401** → 用"状态码"断言；
> - 断言 **body 里的 code = 401** → 用"JSON 字段"断言 `$.code`。

### 5.4 保存成测试场景（可选）

Apifox 的 **自动化测试 → 测试场景** 里，把"登录 → /hello"串起来，勾选"用上一步的变量"，就能一键跑完整链路。

---

## 六、实验报告要点

1. `JwtUtils.java`、`SecurityConfig.java`、`JwtAuthFilter.java` 三个类的截图；
2. 未登录访问 `/hello` 返回 401 的 JSON 截图；
3. 登录成功、页面显示 token 已保存的截图；
4. 带 token 访问成功显示 "Hello World" 的截图；
5. 等 60 秒后访问失败（过期）的截图；
6. Apifox 里"登录 → 提取 token → Bearer 访问"的截图。

---

## 七、常见问题（FAQ）

### Q1：启动后访问任何接口都是 401，包括 /login

- 检查 `permitAll()` 里有没有 `"/login"`；
- 检查请求方法：Security 的 `requestMatchers` 默认不区分方法，但路径必须完全一致；
- 检查是不是把 `SecurityConfig` 放到了 `com.daomanliu.test5` 的**子包之外**（扫不到）。

### Q2：登录接口报 403 而不是 401

CSRF 没关。检查 `.csrf(AbstractHttpConfigurer::disable)` 是否生效。

### Q3：启动报 `WeakKeyException: The signing key's size is ... bits which is not secure enough`

密钥太短。HS256 至少 32 字节（256 位），把密钥字符串加长即可。

### Q4：启动报 `Unable to find an implementation using ... jjwt-impl`

只加了 `jjwt-api`，把 `jjwt-impl` 和 `jjwt-jackson` 补上。

### Q5：token 明明没过期，却一直 401

- 检查前端有没有正确加 `Bearer ` 前缀（**注意后面有个空格**）；
- 检查 `header.substring(7)` 取出来的是不是完整 token；
- 检查服务器时间是否准确（JWT 的 `exp` 是绝对时间）。

### Q6：401 和 403 到底怎么区分

| 状态码 | 含义 | 本实验什么时候出现 |
|--------|------|--------------------|
| **401 Unauthorized** | **没认证**（不知道你是谁） | 没带 token / token 无效 / 过期 |
| **403 Forbidden** | **认证了，但没权限**（知道你是谁，但你不够格） | 实验六里普通用户访问 `/users` |

### Q7：为什么登录返回的是 HTTP 200，body 里却是 401

因为登录失败是"业务判断"，Controller 正常 return 了一个 `Result`。这种"HTTP 永远 200，业务码在 body"的风格叫 **RESTful 弱约束风格**，前后端分离项目很常见。想做严格 REST，就返回 `ResponseEntity.status(401).body(...)`。

### Q8：静态页面 login.html 也要放行吗

要。不然浏览器请求 `/login.html` 时也被当成"未认证"，直接 401，连登录页都看不到。

### Q9：现在退出登录怎么办

本实验**做不到**——JWT 是无状态的，服务端不保存任何东西。前端只能把 `localStorage` 里的 token 删掉，但**这个 token 在过期前仍然有效**。想真正让 token 作废，就要用 **Redis 黑名单**，这正是实验九的内容。这个"缺陷"就是实验九存在的原因。

---

## 八、思考题

1. 如果把 `EXPIRE` 改成 7 天，安全性会怎样变化？怎么在"体验"和"安全"之间折中？（提示：access token + refresh token）
2. 为什么 `JwtAuthFilter` 里 token 无效时"什么都不做"就够了？
3. `SecurityContextHolder` 里的信息是跟什么绑定的？为什么请求结束就没了？（提示：ThreadLocal）
4. 现在所有登录用户拿到的权限都是写死的 `"USER"`。如果要做"管理员才能访问某些接口"，需要改哪几个地方？（实验六会给出答案）

---

## 附录：test5 完整依赖清单

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security-test</artifactId>
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
</dependencies>
```

---

**上一节：[实验04-调用第三方API.md](./实验04-调用第三方API.md)　｜　下一节：[实验06-JDBC访问数据库.md](./实验06-JDBC访问数据库.md)**
