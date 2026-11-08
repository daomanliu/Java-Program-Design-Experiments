# 实验一　基础 Web 程序（test1）

> 对应工程：`test1`　｜　前置实验：无　｜　端口：8080

## 一、实验目标

做完这个实验，你将能够：

1. 用 IDEA 创建一个 Spring Boot 工程；
2. 看懂启动类 `Test1Application` 的作用；
3. 写一个最简单的接口 `GET /hello`，并在浏览器里看到返回值；
4. 用 Apifox 测试这个接口。

**这个实验一个类、一行注解就够，但它决定了后面 9 个实验的骨架，务必亲手跑通。**

---

## 二、原理速览

### 2.1 什么是 Spring Boot

传统的 Java Web 需要：装 Tomcat → 写 web.xml → 打 war 包 → 丢进 Tomcat 目录。
Spring Boot 把 Tomcat **内嵌**到了程序里，你只要运行 `main` 方法，一个 Web 服务器就起来了。

### 2.2 请求是怎么被处理的

```text
浏览器  GET http://localhost:8080/hello
   │
   ▼
内嵌 Tomcat（监听 8080）
   │
   ▼
Spring MVC 根据路径 /hello 找到 HelloController.hello()
   │
   ▼
方法返回 "Hello World"
   │
   ▼
Spring 把它直接写进 HTTP 响应体
   │
   ▼
浏览器显示 Hello World
```

关键注解：

| 注解 | 作用 |
|------|------|
| `@SpringBootApplication` | 启动类专用，等价于"自动配置 + 组件扫描 + 配置类"三合一 |
| `@RestController` | 标记这个类是接口类，方法返回值直接作为响应体（不是页面名） |
| `@GetMapping("/hello")` | 把这个方法绑定到 **GET** 请求的 `/hello` 路径 |

---

## 三、实验步骤

### 步骤 1：新建工程

**方式 A（IDEA 向导，推荐）**

1. `File → New → Project`
2. 左侧选 **Spring Boot**，右侧：
   - Name：`test1`
   - Location：选到你的"实验"目录下
   - Language：Java
   - Type：Maven
   - Group：`com.daomanliu`
   - Artifact：`test1`
   - Package name：`com.daomanliu.test1`
   - JDK：17
   - Java：17
3. 下一步 **Dependencies**：勾选
   - **Spring Web**（Boot 4 里可能显示为 *Web / WebMVC*，生成的是 `spring-boot-starter-webmvc`）
   - **Lombok**
4. Create。

**方式 B**：打开 <https://start.spring.io>，填一样的坐标，Dependencies 选 Spring Web + Lombok，Generate 后解压，用 IDEA 打开。

> 生成后如果 `pom.xml` 里的父版本不是 4.x，也没关系，只要是 3.x/4.x 都能跑；但 **依赖名和 import 要对得上**（4.x 用 `spring-boot-starter-webmvc` + `jakarta.*`）。

### 步骤 2：确认目录结构

```text
test1/
├── pom.xml
└── src/main/
    ├── java/com/daomanliu/test1/
    │   ├── Test1Application.java
    │   └── controller/            ← 待会新建
    └── resources/
        └── application.properties
```

### 步骤 3：检查 pom.xml

打开 `pom.xml`，确认有这两个依赖即可（完整内容见文末附录）：

```xml
<dependencies>
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
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> **新手提示**：改完 pom 后，右上角会冒出一个"刷新"图标（或按 `Ctrl+Shift+O`），点一下让它去下载依赖。第一次下载可能要几分钟，不要急。

### 步骤 4：看启动类

**文件路径**：`test1/src/main/java/com/daomanliu/test1/Test1Application.java`

```java
package com.daomanliu.test1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Test1Application {

    public static void main(String[] args) {
        SpringApplication.run(Test1Application.class, args);
    }

}
```

**逐行解释：**

- `package com.daomanliu.test1;` —— 包名。**后面所有类都必须放在这个包或其子包下**，否则 Spring 扫描不到（这是新手 90% 的"接口 404"原因）。
- `@SpringBootApplication` —— 开启自动配置，并从当前包开始向下扫描所有 `@Component/@RestController/@Service`。
- `SpringApplication.run(...)` —— 启动内嵌 Tomcat，默认监听 8080。

### 步骤 5：写第一个接口

**新建包**：在 `com.daomanliu.test1` 上右键 → `New → Package` → 输入 `controller`。

**新建类**：在 `controller` 包上右键 → `New → Java Class` → 名字 `HelloController`。

**文件路径**：`test1/src/main/java/com/daomanliu/test1/controller/HelloController.java`

粘贴如下内容：

```java
package com.daomanliu.test1.controller;

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

**逐行解释：**

| 代码 | 说明 |
|------|------|
| `@RestController` | 声明这是一个接口类。方法返回 `String` 时，Spring 直接把它写进响应体，**不会**去找同名页面 |
| `@GetMapping("/hello")` | 只有 **GET** 方法的 `/hello` 才会进这个方法。POST 请求会得到 405 |
| `return "Hello World";` | 响应体就是这 11 个字符 |

> 想加第二个接口？复制一份方法改成 `@GetMapping("/hi")` 就行。**同一个类里路径不能重复。**

### 步骤 6：启动

1. 打开 `Test1Application.java`，点 `main` 方法左边的绿色三角 → **Run**。
2. 控制台出现下面这行，说明成功：

```text
Tomcat started on port 8080 (http) with context path ''
Started Test1Application in 1.234 seconds
```

3. 如果控制台最后是红色的 `APPLICATION FAILED TO START`，先看 **第七节 FAQ**。

### 步骤 7：浏览器验证

打开浏览器访问：

```text
http://localhost:8080/hello
```

页面显示：

```text
Hello World
```

**动手改一改**（加深理解）：

- 把返回值改成 `"你好，Spring Boot"`，**重启**后再访问，看变化；
- 把 `@GetMapping` 改成 `@PostMapping`，再在浏览器地址栏访问 —— 浏览器发的是 GET，所以会看到 **405 Method Not Allowed**；
- 再写一个 `@GetMapping("/hi")`，让两个接口并存。

> ⚠️ 改 Java 代码后必须**重启**（除非装了 devtools）。改 HTML 不用重启。

---

## 四、前端说明

本实验**没有前端页面**，直接用浏览器访问接口即可。

从实验二开始才会出现 HTML 文件，统一放在：

```text
testN/src/main/resources/static/*.html
```

文档里只给路径，你直接复制进去就能访问。

---

## 五、接口测试（Apifox）

对应 `接口测试文档.md` 中的 **「GET test1接口测试」**。

### 5.1 准备工作

1. 打开 Apifox → 新建项目 `JavaWeb实验`；
2. **环境管理** → 新建环境 `本地` → 添加变量 `baseUrl` = `http://localhost:8080`；
3. 右上角切换到 `本地` 环境。

### 5.2 新建接口

| 项 | 填写 |
|----|------|
| 接口名称 | test1-你好接口 |
| 方法 | `GET` |
| URL | `{{baseUrl}}/hello` |
| 请求参数 | 无 |
| Header | 无 |

### 5.3 发送并查看

点 **发送**，返回：

- **Body**：`Hello World`
- **状态码**：`200 OK`
- **Content-Type**：`text/plain;charset=UTF-8`

### 5.4 加断言（可选，但建议做）

在 **运行** 页签添加断言：

| 断言类型 | 期望值 |
|----------|--------|
| HTTP 状态码 | `200` |
| 响应文本 / Body | 包含 `Hello World` |

点"运行"，出现绿色 ✅ 即通过。

### 5.5 顺手验证一下错误情况

把 URL 改成 `{{baseUrl}}/hello2` 再发送，你会看到：

- 状态码 `404`
- 返回一个 Spring 默认的错误 JSON：

```json
{
  "timestamp": "2026-11-06T05:20:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/hello2"
}
```

**这就是"路径没匹配上"的标准表现**，后面排查 404 时先看这个。

---

## 六、实验报告要点

建议截图：

1. IDEA 中 `HelloController.java` 的代码；
2. 控制台 `Started Test1Application` 那一行；
3. 浏览器访问 `/hello` 的结果；
4. Apifox 里该接口的请求 + 响应；
5. 改成 POST 后访问出现的 405 页面（说明你对 GET/POST 有理解）。

---

## 七、常见问题（FAQ）

### Q1：启动报 `Port 8080 was already in use`

上一次启动的进程还没退出。
- 方式 1：打开任务管理器，结束 `java.exe`；
- 方式 2：IDEA 右上角把之前 Run 的窗口关掉；
- 方式 3：在 `application.properties` 里加一行 `server.port=8081`，之后访问 `http://localhost:8081/hello`。

### Q2：启动成功但访问 /hello 是 404

按顺序排查：

1. `HelloController` 是不是放在 `com.daomanliu.test1` **或它的子包**里？（放到 `com.daomanliu` 下就扫描不到）
2. 注解是 `@RestController` 还是只有 `@Controller`？（只有 `@Controller` 会去找页面，返回字符串会被当成视图名）
3. 路径有没有拼错、有没有多余空格；
4. 改完代码重启了吗？

### Q3：IDEA 里 `@RestController` 一片红

依赖没下载好。打开右侧 **Maven** 面板 → 点刷新按钮，等依赖下载完。还不行就 `File → Invalidate Caches`。

### Q4：浏览器显示乱码

本实验返回纯英文，一般不会乱码。如果改成中文后乱码，检查：
- 文件编码（IDEA 右下角）是 `UTF-8`；
- 响应头 `Content-Type` 里有 `charset=UTF-8`（Spring Boot 4 默认就是）。

### Q5：为什么不用写 web.xml、不用装 Tomcat？

因为 Spring Boot 把 Tomcat 作为**内嵌依赖**打进了 jar 里，`SpringApplication.run` 就是在启动它。这就是"开箱即用"。

---

## 八、思考题

1. 把 `@RestController` 换成 `@Controller`，返回值改成 `"hello"`，访问会发生什么？为什么？
2. 同一个方法上同时写 `@GetMapping("/hello")` 和 `@PostMapping("/hello")` 会怎样？
3. 想让接口返回 JSON 而不是纯文本，需要做什么？（提示：返回一个对象试试，实验二会给出答案）

---

## 附录：test1 完整 pom.xml

**文件路径**：`test1/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.2</version>
        <relativePath/>
    </parent>
    <groupId>com.daomanliu</groupId>
    <artifactId>test1</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>test1</name>
    <description>test1</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
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
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**配置文件**：`test1/src/main/resources/application.properties`

```properties
spring.application.name=test1
```

> 这个文件现在只有一行，作用只是给应用起个名字。从实验二开始会改成 `application.yaml` 并加入真正的配置项。

---

**下一节：[实验02-前后端通信.md](./实验02-前后端通信.md)**
