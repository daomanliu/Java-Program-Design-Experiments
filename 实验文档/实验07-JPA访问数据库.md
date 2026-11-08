# 实验七　JPA 访问数据库（test7）

> 对应工程：`test7`　｜　来源：由 **test6** 演进而来　｜　端口：8080　｜　复用 `test6` 数据库

## 一、实验目标

1. 理解 **ORM（对象关系映射）**：用"操作对象"代替"写 SQL"；
2. 会用 `@Entity` / `@Id` / `@GeneratedValue` 把类映射成表；
3. 会写 `JpaRepository` 接口，**一行方法声明就有一个查询**；
4. 会区分 **派生查询**（`findByName`）和 **原生 SQL**（`@Query(nativeQuery = true)`）；
5. 理解 `ddl-auto` 的含义，知道为什么这里设成 `none`；
6. 能看懂 Hibernate 打印的 SQL 日志。

---

## 二、原理速览

### 2.1 三层关系

| 概念 | 是什么 |
|------|--------|
| **JPA** | 一套 **规范/接口**（`jakarta.persistence.*`），只定义"应该怎么用" |
| **Hibernate** | JPA 的**实现**，Spring Boot 默认用它，真正和数据库打交道 |
| **Spring Data JPA** | 在 JPA 之上再包一层，让你**只写接口不用写实现** |

> 一句话：JPA 是"标准"，Hibernate 是"干活的人"，Spring Data JPA 是"帮你省事的秘书"。

### 2.2 和 test6 的 JDBC 对比

| | JDBC（test6） | JPA（test7） |
|---|---|---|
| 查询 | 手写 SQL + RowMapper | 方法名 `findByName` 自动生成 SQL |
| 插入 | `insert into users ...` | `userRepository.save(user)` |
| 实体 | 普通 POJO | 加 `@Entity` 注解 |
| 切换数据库 | SQL 可能要改 | 基本不用改 |

**代码量对比**（同样一个"按名字查用户"）：

JDBC 的写法：

```java
List<User> list = jdbc.query("select id,name,password from users where name = ?",
        (rs, i) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("password")), name);
return list.isEmpty() ? null : list.get(0);
```

JPA 的写法：

```java
return userRepository.findByName(name);   // 就这一行，框架帮你写完上面所有
```

### 2.3 派生查询是怎么"猜"出 SQL 的

Spring Data JPA 解析**方法名**，把它翻译成 SQL：

| 方法名 | 生成的 SQL（示意） |
|--------|-------------------|
| `findByName(String name)` | `select ... from users where name = ?` |
| `findByIdAndName(...)` | `select ... where id = ? and name = ?` |
| `countByName(...)` | `select count(*) ... where name = ?` |
| `existsByName(...)` | `select ... where name = ? limit 1` |

支持的动词：`find / read / get / query / count / exists / delete / remove`
支持的关键字：`And / Or / Between / LessThan / GreaterThan / Like / OrderBy / In / IsNull` 等。

> 方法名写错了，**启动直接报错**（`No property xxx found for type User`），这也是一种保护。

---

## 三、实验准备

**复用实验六的数据库**，不需要重新建库。确认三张表在：

```sql
USE test6;
SHOW TABLES;   -- roles, user_role, users
```

> ⚠️ 一定不要为了"省事"打开 `ddl-auto: create`，那会把现有数据**全部清空**（见步骤 2）。

---

## 四、实验步骤

### 步骤 1：复制 test6 得到 test7

复制目录 → 改 `artifactId`、包名 `com.daomanliu.test7`、启动类 `Test7Application`、`spring.application.name`。

**要保留的文件：**

- `common/Result.java`、`common/JwtUtils.java`
- `config/SecurityConfig.java`、`config/JwtAuthFilter.java`
- `entity/User.java`、`entity/Role.java`（**马上要改造**）
- `controller/AuthController.java`、`controller/UserController.java`（**马上要改造**）

**要删掉的文件：**

- `dao/UserDao.java` —— 本实验用 Repository 取代它

### 步骤 2：换依赖

**文件路径**：`test7/pom.xml`

**删掉**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc-test</artifactId>
    <scope>test</scope>
</dependency>
```

**换成**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

**`mysql-connector-j`、Security、jjwt、Lombok 全部保留。**

> `spring-boot-starter-data-jpa` 内部已经带了 JDBC，所以 `JdbcTemplate` 也还在（只是我们不再用），**不用再单独加 jdbc 依赖**。

### 步骤 3：改配置

**文件路径**：`test7/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: test7
  datasource:
    url: jdbc:mysql://localhost:3306/test6?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
```

**新加的两项（本节重点）：**

| 配置 | 取值 | 含义 |
|------|------|------|
| `spring.jpa.hibernate.ddl-auto` | `none` | **不自动建表/改表**。表已经由实验六建好了 |
| `spring.jpa.show-sql` | `true` | 把 Hibernate 生成的 SQL 打印到控制台（学习神器） |

**`ddl-auto` 的 5 个取值必须知道：**

| 值 | 行为 | 风险 |
|----|------|------|
| `none` | 什么都不做（**本实验**） | 表不存在就报错 |
| `validate` | 启动时校验实体与表是否匹配 | 不匹配就启动失败，比较安全 |
| `update` | 自动加字段/表 | **不会删**，字段类型可能改错 |
| `create` | 每次启动 **先删表再建** | 💀 数据全丢 |
| `create-drop` | 启动建、关闭删 | 只适合单元测试 |

> 生产环境常用 `validate` 或 `none`，**永远不要用 `create`**。

### 步骤 4：把实体映射成表

**文件路径**：`test7/src/main/java/com/daomanliu/test7/entity/User.java`

```java
package com.daomanliu.test7.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String password;
}
```

**注解逐个解释：**

| 注解 | 作用 |
|------|------|
| `@Entity` | 告诉 JPA：这个类对应一张表 |
| `@Table(name = "users")` | 表名是 `users`。**不加的话默认表名是类名 `user`**（MySQL 里 `user` 是关键字，很容易出错） |
| `@Id` | 主键字段 |
| `@GeneratedValue(strategy = IDENTITY)` | 主键由数据库自增（对应 MySQL 的 `AUTO_INCREMENT`） |
| `@Data`（Lombok） | 生成 getter/setter。**JPA 需要 getter/setter 和"无参构造"** |

> ⚠️ **无参构造**：JPA 通过反射创建对象，必须有 public/protected 无参构造。本工程只写了 `@Data`——因为类里没有 `final` 字段，Lombok 会生成一个无参构造，所以恰好能用。**更稳妥的写法是显式加上 `@NoArgsConstructor`**，或者像下面的 `Role` 一样三个注解开全。

**文件路径**：`test7/src/main/java/com/daomanliu/test7/entity/Role.java`

```java
package com.daomanliu.test7.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}
```

**字段名和列名怎么对应？**

| 实体字段 | 数据库列 | 规则 |
|----------|----------|------|
| `id` | `id` | 同名 |
| `name` | `name` | 同名 |
| `userId`（假设） | `user_id` | **驼峰自动转下划线**（Spring Boot 默认的命名策略） |

> 如果你的列名和规则对不上，用 `@Column(name = "xxx")` 显式指定。

### 步骤 5：写 Repository（本实验的"灵魂"）

**文件路径**：`test7/src/main/java/com/daomanliu/test7/repository/UserRepository.java`

新建包 `repository`，建 **接口**（不是类！）`UserRepository`：

```java
package com.daomanliu.test7.repository;

import com.daomanliu.test7.entity.Role;
import com.daomanliu.test7.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByName(String name);

    @Query(value = "select r.* from roles r join user_role ur on r.id = ur.role_id where ur.user_id = ?1", nativeQuery = true)
    List<Role> findRolesByUserId(Long userId);
}
```

**逐行解释：**

#### ① 继承 `JpaRepository<User, Long>`

泛型参数：**第一个是实体类型，第二个是主键类型**。

继承之后，**白送**这些方法（不用写一行实现）：

| 方法 | 作用 |
|------|------|
| `save(entity)` | 新增或更新（主键有值=更新，无值=新增） |
| `findAll()` | 查全部 |
| `findById(id)` | 按主键查，返回 `Optional` |
| `deleteById(id)` | 按主键删 |
| `count()` | 总数 |
| `existsById(id)` | 是否存在 |

> 这些方法来自 `JpaRepository` → `ListCrudRepository` → `Repository` 的继承链。

#### ② 派生查询 `findByName`

```java
Optional<User> findByName(String name);
```

Spring 解析成：`select * from users where name = ?`。

**为什么返回 `Optional` 而不是 `User`？**
因为可能查不到。用 `Optional` 强迫调用方处理"查不到"的情况，比返回 `null` 更安全：

```java
User dbUser = userRepository.findByName(user.getName()).orElse(null);
```

（对比 test6 的 `list.isEmpty() ? null : list.get(0)`，高下立判。）

#### ③ 原生 SQL 查询

```java
@Query(value = "select r.* from roles r join user_role ur on r.id = ur.role_id where ur.user_id = ?1", nativeQuery = true)
List<Role> findRolesByUserId(Long userId);
```

**为什么要写原生 SQL？** 因为 `user_role` 是**中间表**，我们没有为它建实体类。JPA 的派生查询只能查"实体自己的字段"，跨表就必须：

- 要么建关联实体（进阶）；
- 要么写 `@Query`（本实验的选择）。

| 参数 | 说明 |
|------|------|
| `value = "..."` | SQL 语句 |
| `?1` | **第 1 个参数**（原生 SQL 里用 `?1` 表示第 1 个占位符） |
| `nativeQuery = true` | 表示这是**原生 SQL**（直接发给 MySQL），不是 JPQL |

> 如果是 JPQL（面向实体），写法是 `@Query("select r from Role r join ...")`，不写 `nativeQuery`。**不写 `nativeQuery = true` 却用 SQL 语法 → 启动/运行时报错。**

### 步骤 6：改造认证控制器

**文件路径**：`test7/src/main/java/com/daomanliu/test7/controller/AuthController.java`

```java
package com.daomanliu.test7.controller;

import com.daomanliu.test7.common.JwtUtils;
import com.daomanliu.test7.common.Result;
import com.daomanliu.test7.entity.User;
import com.daomanliu.test7.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        if (user.getName() == null || user.getPassword() == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (userRepository.findByName(user.getName()).isPresent()) {
            return Result.fail(400, "用户名已存在");
        }
        user.setPassword(encoder.encode(user.getPassword()));
        userRepository.save(user);
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody User user) {
        User dbUser = userRepository.findByName(user.getName()).orElse(null);
        if (dbUser == null || !encoder.matches(user.getPassword(), dbUser.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        List<String> roles = userRepository.findRolesByUserId(dbUser.getId())
                .stream().map(r -> "ROLE_" + r.getName()).toList();
        String token = JwtUtils.generate(dbUser.getName(), roles);
        return Result.success("登录成功", token);
    }
}
```
**注意**：注册时判断用户是否存在是用的`userRepository.findByName(user.getName()).isPresent()`  
**和 test6 的对应关系：**

| test6（JDBC） | test7（JPA） |
|---------------|--------------|
| `userDao.findByName(name) != null` | `userRepository.findByName(name).isPresent()` |
| `userDao.insert(name, encoded)` | `user.setPassword(encoded); userRepository.save(user);` |
| `userDao.findByName(...).getPassword()` | 一样，但用 `orElse(null)` |
| `userDao.findRolesByUserId(id)` | `userRepository.findRolesByUserId(id)` |

**注意 `save` 的两种语义：**

- `user.getId() == null` → 生成 `INSERT`，并**自动回填**自增 id；
- `user.getId() != null` → 生成 `UPDATE`。

> 本实验注册时 id 是 null，所以走 INSERT。**千万别手动给 id 设值**，否则会变成 UPDATE，更新一条不存在的记录（静默失败）。

### 步骤 7：改造用户列表接口

**文件路径**：`test7/src/main/java/com/daomanliu/test7/controller/UserController.java`

```java
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
```

**变化只有一处：`userDao.findAll()` → `userRepository.findAll()`。**

> ⚠️ 这里的 `users.forEach(u -> u.setPassword(null))` 有个隐藏风险（test9 会更明显）：如果这些实体还在 **JPA 的持久化上下文**里，修改会被"脏检查"捕获并写回数据库。本方法没有 `@Transactional`，请求结束时事务已提交，通常不会写回；但**更安全的写法是返回一个新对象或 DTO**。这一点请在实验九再回来看。

### 步骤 8：其余配置类照抄 test6

以下文件**不用改逻辑**，只把包名 `test6` 换成 `test7`：

| 文件路径 | 说明 |
|----------|------|
| `test7/src/main/java/com/daomanliu/test7/common/Result.java` | 不变 |
| `test7/src/main/java/com/daomanliu/test7/common/JwtUtils.java` | 不变 |
| `test7/src/main/java/com/daomanliu/test7/config/JwtAuthFilter.java` | 不变 |
| `test7/src/main/java/com/daomanliu/test7/config/SecurityConfig.java` | 不变（含 `@EnableMethodSecurity`） |

### 步骤 9：放前端页面

**文件路径**（直接从 test6 复制，内容完全一致）：

| 文件路径 | 作用 |
|----------|------|
| `test7/src/main/resources/static/index.html` | 导航 |
| `test7/src/main/resources/static/register.html` | 注册 |
| `test7/src/main/resources/static/login.html` | 登录 |
| `test7/src/main/resources/static/users.html` | 用户列表 |

**访问地址**：`http://localhost:8080/index.html`

> 前端**完全不用改**，因为接口路径和返回格式都没变——这就是"分层"的好处：换了数据访问技术，前端无感。

### 步骤 10：启动并观察 SQL 日志

运行 `Test7Application`。控制台会打印类似：

```text
Hibernate: select u1_0.id,u1_0.name,u1_0.password from users u1_0 where u1_0.name=?
Hibernate: insert into users (name,password) values (?,?)
```

**这些 SQL 是 Hibernate 自动生成的**，`show-sql: true` 让它们显示出来。对照着看，能学到很多：

- 派生查询 `findByName` → `where u1_0.name=?`（**用了 `?` 占位符，安全**）；
- `save` → `insert`；
- 表名 `users`、列名 `name`/`password` 全自动映射。

### 步骤 11：功能验证

1. 打开 `/register.html`，注册 `jpa_user` / `123456`；
2. 数据库确认：

```sql
SELECT * FROM users WHERE name = 'jpa_user';
-- 有记录，密码是 $2a$10$ 开头
```

3. 登录 → 拿到 token；
4. 访问 `/users` → 新用户没角色，**403**；
5. 授权（同实验六）后重新登录 → **200**，能列出用户。

---

## 五、前端文件清单（直接复制，不要手写）

| 文件路径 | 作用 |
|----------|------|
| `test7/src/main/resources/static/index.html` | 导航首页 |
| `test7/src/main/resources/static/register.html` | 注册（POST /register） |
| `test7/src/main/resources/static/login.html` | 登录（POST /login） |
| `test7/src/main/resources/static/users.html` | 用户列表（GET /users） |

---

## 六、接口测试（Apifox）

接口与实验六**完全一致**（因为业务没变，只换了持久层）。建议直接**复制实验六的接口目录**，改个名。

### 6.1 注册

`POST {{baseUrl}}/register`，Body：`{"name": "jpa_user", "password": "123456"}`

期望：`{"code":200,"msg":"注册成功","data":null}`

### 6.2 登录

`POST {{baseUrl}}/login`，Body：`{"name": "jpa_user", "password": "123456"}`

期望：`code=200`，`data` 是一段 JWT。**后置操作提取变量 `token` = `$.data`**。

### 6.3 用户列表

`GET {{baseUrl}}/users`，认证 Bearer `{{token}}`

| 场景 | 期望 |
|------|------|
| 有 ADMIN 角色 | 200 + 用户数组（password 全为 null） |
| 无 ADMIN 角色 | HTTP 403 |
| 不带 token | HTTP 401 |

### 6.4 和实验六做对比的"验证型测试"

在 Apifox 里把同一组请求分别打到 test6 和 test7（端口不同，或者先后启动），**返回结果应该完全一样**。这证明：

> **持久层的替换对接口契约没有影响** —— 这是分层架构的核心价值，也是本节实验报告最该写的一句话。

---

## 七、实验报告要点

1. `User.java`、`Role.java` 的注解截图；
2. `UserRepository.java` 截图（包括派生查询和 @Query）；
3. 控制台里 Hibernate 打印的 SQL 截图（**重点**）；
4. 数据库里数据写入成功的截图；
5. Apifox 三条接口截图；
6. 一句话总结 JDBC 与 JPA 的差异。

---

## 八、常见问题（FAQ）

### Q1：启动报 `Table 'test6.user' doesn't exist`

实体没写 `@Table(name = "users")`，JPA 默认找 `user` 表。

### Q2：启动报 `No property 'xxx' found for type 'User'`

派生查询的方法名写错了。检查 `findByName` 里的 `Name` 是不是实体里真实存在的字段（**首字母大写**）。

### Q3：启动报 `Error creating bean with name 'userRepository'`

常见原因：
- 实体上没有 `@Entity`；
- Repository 接口不需要写 `@Repository`，只要放在主启动类的子包里就会自动扫描；
- 写成了 `class` 而不是 `interface`。

### Q4：数据被清空了！

99% 是把 `ddl-auto` 改成了 `create`。改成 `none`（或 `update`）后重启，数据不会自己回来，只能重录。

### Q5：`save()` 报 `detached entity passed to persist`

通常是手动设置了 id 又调 `save`，或者实体来自另一个事务。删掉手工设置的 id 试试。

### Q6：`findRolesByUserId` 报 `could not extract ResultSet` / 语法错

- 检查有没有 `nativeQuery = true`；
- 检查表名写的是数据库真实表名（`roles`/`user_role`），不是实体名。

### Q7：控制台没有任何 SQL

- `show-sql: true` 没配对位置（必须在 `spring.jpa` 下面）；
- 或者你用的是 `logging.level.org.hibernate.SQL` 的方式但级别没生效。

### Q8：启动比 test6 慢好几秒

正常。JPA 启动时要扫描实体、建立元数据模型。

### Q9：中文乱码

同实验六：连接串加 `useUnicode=true&characterEncoding=utf8`。

---

## 九、思考题

1. 如果要实现"按用户名模糊搜索"，方法名该怎么写？（提示：`findByNameContaining`）
2. 为什么 `findByName` 返回 `Optional` 而 `findRolesByUserId` 返回 `List`？
3. `@Query` 里的 `?1` 和 `:name` 两种占位符有什么区别？（提示：后者配合 `@Param`）
4. 如果以后要给 `user_role` 也建实体类，`findRolesByUserId` 该怎么改写？（提示：`@ManyToMany`）

---

## 附录：test7 依赖清单

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
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
        <artifactId>spring-boot-starter-data-jpa-test</artifactId>
        <scope>test</scope>
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

**上一节：[实验06-JDBC访问数据库.md](./实验06-JDBC访问数据库.md)　｜　下一节：[实验08-数据库事务.md](./实验08-数据库事务.md)**
