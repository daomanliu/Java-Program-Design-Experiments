# Java 程序设计课程实验

Java Web 课程配套实验代码：10 个可独立运行的 Spring Boot 工程，覆盖从基础 Web 接口到权限、数据库、事务、缓存、AI 调用的完整演进路线。每个实验都配有零基础可跟做的图文指导书。

| 项目 | 说明 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 4.1.2、Spring Security、Spring Data JPA |
| 构建 | Maven（工程自带 `mvnw`，无需单独安装） |
| 存储 | MySQL 8（test6–test9）、Redis 7（test9） |
| 其他 | JJWT、Spring AI（DeepSeek）、Lombok、原生 HTML + JS |

---

## 📚 实验一览

| # | 实验 | 核心技术 | 工程目录 | 指导书 |
| --- | --- | --- | --- | --- |
| 01 | 基础 Web 程序 | `@RestController`、`@GetMapping`、Spring Boot 启动 | [`test1`](./test1) | [实验01](./实验文档/实验01-基础Web程序.md) |
| 02 | 前后端通信 | JSON、`@RequestBody`、统一返回 `Result`、Lombok | [`test2`](./test2) | [实验02](./实验文档/实验02-前后端通信.md) |
| 03 | 文件上传下载 | `MultipartFile`、`Files`/`Path`、`ResponseEntity` | [`test3`](./test3) | [实验03](./实验文档/实验03-文件上传下载.md) |
| 04 | 调用第三方 API | `RestTemplate`、DTO 反序列化、和风天气 | [`test4`](./test4) | [实验04](./实验文档/实验04-调用第三方API.md) |
| 05 | 权限管理 | Spring Security + JWT 无状态认证 | [`test5`](./test5) | [实验05](./实验文档/实验05-权限管理与JWT.md) |
| 06 | JDBC 访问数据库 | `JdbcTemplate`、MySQL、BCrypt、角色鉴权 | [`test6`](./test6) | [实验06](./实验文档/实验06-JDBC访问数据库.md) |
| 07 | JPA 访问数据库 | `@Entity`、`JpaRepository`、派生查询 | [`test7`](./test7) | [实验07](./实验文档/实验07-JPA访问数据库.md) |
| 08 | 数据库事务 | `@Transactional`、原子写入、回滚验证 | [`test8`](./test8) | [实验08](./实验文档/实验08-数据库事务.md) |
| 09 | Redis 与 Token 黑名单 | `RedisTemplate`、token 存储、TTL | [`test9`](./test9) | [实验09](./实验文档/实验09-Redis与Token黑名单.md) |
| 10 | AI 大模型调用 | Spring AI、`ChatClient`、工具调用、SSE 流式输出 | [`test10`](./test10) | [实验10](./实验文档/实验10-AI大模型调用.md) |

### 实验之间的依赖关系

```text
test1  基础 Web 程序（独立）
test2  前后端通信（独立） ──► test5 权限管理 ──► test6 JDBC ──► test7 JPA ──► test8 事务 ──► test9 Redis
test3  文件传输（独立）
test4  第三方 API（独立） ──► test10 AI 调用
```

- **test1 ~ test4**：四条互不相干的起跑线，可以任意顺序单独完成。
- **test5 ~ test9**：一条连贯主线——登录发令牌 → 存储（JDBC → JPA）→ 一致性（事务）→ 状态与性能（Redis）。
- **test10**：把 test4 的天气查询交给大模型通过 Function Calling 调度。

---

## 📂 目录结构

```text
.
├── test1/                  # 实验01 基础 Web 程序
├── test2/                  # 实验02 前后端通信
├── test3/                  # 实验03 文件上传下载
├── test4/                  # 实验04 调用第三方 API（和风天气）
├── test5/                  # 实验05 Spring Security + JWT
├── test6/                  # 实验06 JDBC 访问数据库
├── test7/                  # 实验07 JPA 访问数据库
├── test8/                  # 实验08 数据库事务
├── test9/                  # 实验09 Redis 与 Token 黑名单
├── test10/                 # 实验10 AI 大模型调用
├── 实验文档/                # 10 份实验指导书 + 总目录
├── 接口测试文档.md          # Apifox 接口测试步骤
└── .gitignore
```

每个 `testN` 都是独立的 Maven 工程，目录结构一致：

```text
testN/
├── pom.xml
├── mvnw / mvnw.cmd              # Maven Wrapper
├── .mvn/wrapper/
└── src/
    ├── main/java/com/daomanliu/testN/
    ├── main/resources/application.yaml
    ├── main/resources/static/   # 前端页面（不要求自己写）
    └── test/java/...
```

---

## 🚀 快速开始

### 环境要求

- JDK 17 及以上
- IntelliJ IDEA 2024.1+（社区版即可）或其他 IDE
- MySQL 8.0（仅 test6–test9）
- Redis 7（仅 test9）
- 浏览器（打开实验页面前端）

### 运行某个实验

```powershell
cd test1
.\mvnw.cmd spring-boot:run
```

启动后默认监听 **http://localhost:8080** （所有工程均未改端口）。带前端页面的实验直接访问：

| 实验 | 页面 |
| --- | --- |
| test2 / test5 / test6–test9 | `/login.html`、`/register.html`、`/users.html`、`/index.html` |
| test3 | `/file.html` |
| test4 / test10 | `/weather.html`（test10 另有 `/chat.html`） |

也可以在 IDEA 里打开对应 `testN` 目录，运行 `TestNApplication` 主类。

---

## ⚙️ 配置与密钥

出于安全考虑，仓库中的密钥、数据库密码、和风天气专属域名都已替换为占位符，**运行前需要自行配置**。

相关配置位于各工程的 `src/main/resources/application.yaml`：

| 环境变量 | 涉及实验 | 作用 |
| --- | --- | --- |
| `QWEATHER_API_KEY` | test4、test10 | 和风天气 API Key |
| `DEEPSEEK_API_KEY` | test10 | DeepSeek API Key |
| `DB_PASSWORD` | test6–test9 | MySQL `root` 密码 |

占位符形如 `"${QWEATHER_API_KEY:your-qweather-api-key}"`，即「优先读环境变量，读不到就用默认值」。临时设置方式（当前终端有效）：

```powershell
$env:QWEATHER_API_KEY = "你的和风天气Key"
$env:DEEPSEEK_API_KEY  = "sk-你的DeepSeekKey"
$env:DB_PASSWORD       = "你的MySQL密码"
cd test10
.\mvnw.cmd spring-boot:run
```

另外，test4 与 test10 的 `qweather.host` 已脱敏为 `https://***.re.qweatherapi.com`，需要填回**你自己账号的专属域名**（新版和风天气控制台分配，不是 `devapi.qweather.com`）。

> 想长期保留本地配置，可以新建一份 `application-local.yaml` 并用 `.gitignore` 排除，避免再次把密钥提交进仓库。

---

## 🗄️ 数据库与 Redis

**MySQL（test6–test9）**：库名 `test6`，建库建表脚本在 [`test6/src/main/resources/data.sql`](./test6/src/main/resources/data.sql)，包含 `users`、`roles`、`user_role` 三张表。

**Redis（test9）**：默认 `localhost:6379`，无密码，用于存储 JWT 与黑名单。

**文件上传（test3）**：上传目录默认为 `D:\upload`，可在 `application.yaml` 的 `file.upload-dir` 修改。

---

## 📖 文档

- [实验指导书总目录](./实验文档/README.md) —— 环境准备、10 个实验的完整步骤
- [接口测试文档](./接口测试文档.md) —— 配合 Apifox 的接口调试说明

---

## 🔐 安全说明

仓库中所有真实凭据均已脱敏：

- 数据库密码、DeepSeek Key、和风天气 Key 一律替换为环境变量占位符
- 和风天气账号专属域名替换为 `***`
- Git 提交历史中不包含任何真实密钥

如需在本地还原，请通过环境变量注入，**不要直接写回配置文件再提交**。

---

## 📄 License

本项目仅用于课程学习与教学演示。
