# Java Web 实验指导书（test1 – test10）

> 本套文档面向 **零基础** 的同学：只要你会装软件、会复制粘贴、能看懂一点点 Java，按照文档一步一步做，就能把 10 个实验全部跑通。
>
> 文档约定：
> - **所有后端代码都写出了"写在哪、怎么写、为什么这么写"**，代码块可以直接对照抄。
> - **前端页面不要求你写**，文档里只给出文件路径，你把它放进工程就能用。
> - 每个实验都附带 **Apifox 接口测试** 的完整步骤（与工程根目录的 `接口测试文档.md` 对应）。
> - 实验九（Redis）会单独讲 **Redis 的安装、使用、token 存储与黑名单**。

---

## 1. 实验一览

| 序号 | 实验名称 | 核心技术 | 工程目录 | 前置实验 |
|------|----------|----------|----------|----------|
| 01 | 基础 Web 程序 | Spring Boot 启动、`@RestController`、`@GetMapping` | `test1` | 无 |
| 02 | 前后端通信 | JSON、`@RequestBody`、统一返回 `Result`、Lombok | `test2` | 无 |
| 03 | 文件传输 | `MultipartFile`、`Files`/`Path`、下载 `ResponseEntity` | `test3` | 无 |
| 04 | 调用第三方 API | `RestTemplate`、DTO 反序列化、密钥配置 | `test4` | 无 |
| 05 | 权限管理 | Spring Security + JWT 无状态认证 | `test5` | 02 |
| 06 | JDBC 访问数据库 | `JdbcTemplate`、MySQL、BCrypt、角色鉴权 | `test6` | 05 |
| 07 | JPA 访问数据库 | `@Entity`、`JpaRepository`、派生查询、native SQL | `test7` | 06 |
| 08 | 数据库事务 | `@Transactional`、原子写入、回滚验证 | `test8` | 07 |
| 09 | 引入 Redis | `RedisTemplate`、token 存储、黑名单、TTL | `test9` | 08 |
| 10 | AI 调用 | Spring AI、`ChatClient`、工具调用、SSE 流式输出 | `test10` | 04 |

### 1.1 实验之间的依赖关系

```text
test1  基础 Web 程序（独立）
test2  前后端通信（独立） ──► test5 权限管理 ──► test6 JDBC ──► test7 JPA ──► test8 事务 ──► test9 Redis
test3  文件传输（独立）
test4  第三方 API（独立） ──► test10 AI 调用
```

**一句话说明：**

- test1 ~ test4 是四条互不相干的起跑线，每个都能单独做。
- test5 是把 test2 的"登录"升级成"登录 + 发令牌"，从这里开始变成一条主线。
- test6 ~ test9 是这条主线的连续演进：**存储方式（JDBC → JPA）→ 数据一致性（事务）→ 性能与状态（Redis）**。
- test10 是把 test4 的"天气查询"交给大模型去调用（Function Calling）。

---

## 2. 环境准备

### 2.1 软件清单

| 软件 | 版本要求 | 用途 | 哪些实验需要 |
|------|----------|------|--------------|
| JDK | 17（必须 17 及以上） | 运行 Java | 全部 |
| IntelliJ IDEA | 2024.1 及以上（社区版即可） | 写代码、跑工程 | 全部 |
| Maven | 3.9.x（工程自带 `mvnw`，可不单独装） | 下载依赖 | 全部 |
| MySQL | 8.0.x | 数据库 | test6 ~ test9 |
| Redis | 7.x | 缓存 / 黑名单 | test9 |
| Apifox | 最新版 | 接口测试 | 全部 |
| 浏览器 | Chrome / Edge | 打开前端页面 | 全部 |

> 说明：test1 ~ test5、test10 **不需要** MySQL 和 Redis，装不装都不影响。

### 2.2 JDK 17

1. 到 Oracle 或 Adoptium（推荐 Temurin）下载 JDK 17 安装包，一路下一步。
2. 安装完成后打开 PowerShell 验证：

```powershell
java -version
javac -version
```

看到 `java version "17.x.x"` 就说明成功。
如果提示"不是内部或外部命令"，请检查 **系统环境变量 → Path** 里有没有 `%JAVA_HOME%\bin`。

### 2.3 IntelliJ IDEA

1. 下载并安装 IDEA（社区版够用）。
2. 第一次打开时，在 `Customize → All settings → Build, Execution, Deployment → Build Tools → Maven` 里确认 JDK 选的是 17。
3. 打开工程的方式：`File → Open`，选中某个 `testN` 目录（**注意：选到 pom.xml 所在的这一层**，不要选中整个"实验"目录，否则 IDEA 会把 10 个工程当成一个混乱的项目）。

### 2.4 MySQL 8

1. 安装 MySQL 8（记住 root 密码）。
2. 用 Navicat / DataGrip / IDEA 自带的 Database 工具，或者命令行连上去：

```powershell
mysql -u root -p
```

3. 建库建表在 **实验六** 里统一执行，这里只要保证能连上就行。

### 2.5 Redis（只有实验九需要）

Windows 上 Redis 官方不直接提供安装包，三选一：

- **方案 A（最简单，推荐）**：用 Docker

  ```powershell
  docker run -d --name redis -p 6379:6379 redis:7
  ```

- **方案 B**：下载社区维护的 Windows 版（搜索 "tporadowski/redis" 的 Release，解压后双击 `redis-server.exe`），再开一个窗口运行 `redis-cli.exe`。
- **方案 C**：WSL2 里 `sudo apt install redis-server`。

验证：

```powershell
redis-cli ping
# 返回 PONG 就说明 Redis 起来了
```

### 2.6 Apifox

1. 官网下载安装，注册登录。
2. 新建一个项目，名字随意，例如 `JavaWeb实验`。
3. 后面的接口都建在这个项目里（**建议按实验分目录**：test1、test2 …… test10）。

---

## 3. 通用约定（10 个实验都一样）

### 3.1 工程坐标与包名

| 项目 | 值 |
|------|-----|
| GroupId | `com.daomanliu` |
| ArtifactId | `test1` ~ `test10` |
| 包名 | `com.daomanliu.test1` ~ `com.daomanliu.test10` |
| 启动类 | `TestNApplication.java`（放在包根目录） |
| 端口 | 8080（默认，同一个时间只能启动一个工程） |

### 3.2 标准目录结构

```text
testN/
├── pom.xml                         依赖清单
└── src/main/
    ├── java/com/daomanliu/testN/
    │   ├── TestNApplication.java   启动类（固定写法）
    │   ├── controller/             接收 HTTP 请求
    │   ├── service/                业务逻辑（test10 用）
    │   ├── entity/                 实体类
    │   ├── dao/                    JDBC 数据访问（test6）
    │   ├── repository/             JPA 数据访问（test7 及以后）
    │   ├── config/                 Security 配置、过滤器
    │   └── common/                 Result、JwtUtils 等工具
    └── resources/
        ├── application.yaml        配置文件
        └── static/                 前端页面（HTML，能直接被访问）
```

> `src/main/resources/static/` 是 Spring Boot 的"静态资源目录"，放在里面的 `login.html` 可以直接用 `http://localhost:8080/login.html` 打开，**不需要写任何 Controller**。这就是文档里"前端只给路径"的原因。

### 3.3 统一返回格式 `Result`

从 test2 开始，所有接口都返回同一个外壳：

```json
{
  "code": 200,
  "msg": "success",
  "data": { }
}
```

- `code`：**业务状态码**（200 成功，400 参数错，401 未登录，403 无权限，404 找不到，500 服务端错）。
- `msg`：给人看的中文提示。
- `data`：真正的业务数据，失败时是 `null`。

> ⚠️ 重点：这里的 `code` 是 **响应体里的字段**，不是 HTTP 状态码。例如 test2 登录失败时 HTTP 状态码仍是 200，只是body里的 `code` 是 401。做接口测试时不要搞混（第 6 节会讲怎么断言）。

### 3.4 Spring Boot 4 的依赖名变化

本套工程用的是 **Spring Boot 4.x**，和网上很多 3.x 教程的依赖名不一样：

| 3.x 的写法 | 4.x 的写法（本工程用的） |
|-----------|--------------------------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |

两种写法在新版本里都能跑，**建议直接照抄文档里的 pom**，不要自己猜。
另外，4.x 全面使用 `jakarta.*` 命名空间（不是老教程里的 `javax.*`），导入包时注意。

---

## 4. 建议的学习路线

```text
第 1 天：实验 01（跑通第一个接口）+ 实验 02（浏览器 ↔ 后端传 JSON）
第 2 天：实验 03（上传下载）+ 实验 04（调外部 API）
第 3 天：实验 05（登录发 token，能拦住未登录请求）
第 4 天：实验 06（数据落到 MySQL）+ 实验 07（换成 JPA 写法）
第 5 天：实验 08（事务）+ 实验 09（Redis 与退出登录）
第 6 天：实验 10（大模型 + 工具调用）
```

**每个实验的固定节奏：**

1. 读"实验目标"，知道这一节要解决什么问题；
2. 跟着"步骤"建工程、抄代码（**按顺序抄，先抄实体/工具类，再抄 Controller**）；
3. 启动工程，用浏览器或 Apifox 验证；
4. 做"接口测试"，把现象截图留档（实验报告要用）；
5. 卡住了先看"常见问题"。

---

## 5. Apifox 通用操作（做一次，10 个实验都复用）

后面每个实验都会说"用 Apifox 测 XXX 接口"，这里先把公共动作讲清楚。

### 5.1 建环境（配一次就行）

1. 打开项目 → 右上角 **环境管理** → 新建环境，命名 `本地`。
2. 添加变量：

   | 变量名 | 值 |
   |--------|-----|
   | `baseUrl` | `http://localhost:8080` |

3. 保存后，在右上角把当前环境切换成 `本地`。

> 之后所有接口的地址都写成 `{{baseUrl}}/login` 这种形式，换电脑、换端口只要改环境变量。

### 5.2 新建一个接口

1. 左侧目录树 → 右键 → **新建接口**。
2. 填：接口名称、请求方法（GET / POST）、URL（`{{baseUrl}}/xxx`）。
3. POST 接口在 **Body** 页签选 `json`，把 JSON 粘进去。
4. 点 **发送**，下方看 **返回响应**（有美化/原始/预览几种视图）。

### 5.3 断言（让测试自动判断对错）

在接口的 **运行** 页签（或"测试用例"里）→ **添加断言**：

- 断言类型 **JSON 字段**：`$.code` 等于 `200`
- 断言类型 **HTTP 状态码**：等于 `401`（有的接口失败时是 HTTP 401，比如 test5 的 `/hello`）

> 断言写好后，点"运行"会显示 ✅ 或 ❌，做实验报告时截图最有说服力。

### 5.4 提取变量（上一个接口的值给下一个接口用）

例如"登录拿到 token，再带着 token 访问受保护接口"：

1. 打开 **登录** 接口 → **后置操作** → **提取变量**。
2. 变量名 `token`，表达式 `$.data`（因为 token 在 `data` 字段里）。
3. 保存。之后在别的接口里把认证方式选 **Bearer Token**，值填 `{{token}}` 即可。

### 5.5 与 `接口测试文档.md` 的关系

工程根目录的 `接口测试文档.md` 是 **Apifox 导出的接口清单**（widdershins 格式），里面列出了所有实验的接口、请求体示例和返回结构。

它不能直接"导入"成 Apifox 工程，正确用法是：

- 当作**对照表**：照着它的路径和示例，在 Apifox 里一条条建；
- 每个实验文档的"接口测试"小节已经把该实验的接口整理好了，直接照抄即可。

---

## 6. 代码检查提醒（我没有改动你的任何代码）

在通读 10 个工程时发现下面几处 **可能想让你确认一下** 的地方。它们大多不影响运行，部分在特定场景下会出问题，**请你自行判断是否要改**。

| # | 位置 | 现象 / 风险 | 建议 |
|---|------|-------------|------|
| 1 | `test3/application.yaml` | `file.upload-dir: D:\upload` 目录不存在时上传直接 500，代码里没有 `Files.createDirectories` | 先手动建好 `D:\upload` 目录 |
| 2 | `test3/FileController.upload` | 直接用 `getOriginalFilename()` 拼路径，没过滤 `../`，存在路径穿越风险 | 教学可接受，生产要过滤 |
| 3 | `test4/dto/GeoResponse.java` | 注释写反了：`lat` 是**纬度**、`lon` 是**经度** | 改注释即可，代码逻辑是对的 |
| 4 | `test4`、`test10/application.yaml` | 和风天气 key、DeepSeek api-key 明文写在配置里 | 截图/提交前记得打码 |
| 5 | `test5/config/SecurityConfig.java` | `tools.jackson.databind.ObjectMapper`、`SecurityProperties` 两个 import 没用到 | 可删，不影响运行 |
| 6 | `test5/common/JwtUtils.java` | `EXPIRE = 60 * 1000` 只有 **60 秒**（test6 起改成 120 秒） | 测试时要抓紧，属正常设计 |
| 7 | `test6/src/main/resources/data.sql` | 不会自动执行：Spring Boot 默认 `spring.sql.init.mode=embedded`，MySQL 属外部库；且里面有 `CREATE DATABASE`，真自动跑也会报错 | **手动**在 MySQL 里执行，见实验六 |
| 8 | `test6` 的 `roles` 表 | 建表后是空的，不手工插 ADMIN，`/users` 永远 403 | 见实验六"授予管理员" |
| 9 | `test7/…/UserRepository.java`、`test8/…/UserRepository.java` | 有未使用的 `Role` import | 只是黄线警告 |
| 10 | `test9/UserController.profile()` | 连续两次 `findByName`，且第一次就 `setPassword(null)`；在 OSIV（默认开启）下对托管实体的修改有被脏检查写库的风险 | 建议改成"查一次 → 返回副本/置空后返回同一对象"，请你确认 |
| 11 | `test9/AuthController.logout` | 直接 `header.substring(7)`，没带 `Authorization` 或不是 `Bearer ` 开头会抛异常（缺 header 是 400） | 生产环境建议判空 |
| 12 | `test9` 多处 | `Jwts`、`Date`、`TimeUnit` 等 import 冗余 | 可删 |
| 13 | `test10/pom.xml` | `spring-ai-starter-model-openai` 用的是 `2.1.0-RC2`，预发布版本有时从中央仓库拉不到 | 拉不到就加 spring milestone 仓库（实验十 FAQ 有写法） |
| 14 | `test2/LoginController` | 登录成功把含明文密码的 `User` 返回给了前端 | 教学无妨，真实项目要脱敏 |
| 15 | `test8/AuthController.register` | 先 `findByName` 再 `save`，高并发下仍可能触发 `users.name` 唯一约束冲突（500） | 可在全局异常处理里捕获 `DuplicateKeyException` |

---

## 7. 各实验文档索引

| 文档 | 内容 |
|------|------|
| [实验01-基础Web程序.md](./实验01-基础Web程序.md) | 第一个接口 `/hello` |
| [实验02-前后端通信.md](./实验02-前后端通信.md) | 浏览器 POST JSON，统一返回体 |
| [实验03-文件上传下载.md](./实验03-文件上传下载.md) | 上传、列表、下载 |
| [实验04-调用第三方API.md](./实验04-调用第三方API.md) | 和风天气：城市查询 + 实时天气 |
| [实验05-权限管理与JWT.md](./实验05-权限管理与JWT.md) | 登录发 token、过滤器验 token |
| [实验06-JDBC访问数据库.md](./实验06-JDBC访问数据库.md) | JdbcTemplate、BCrypt、角色权限 |
| [实验07-JPA访问数据库.md](./实验07-JPA访问数据库.md) | 实体映射、Repository |
| [实验08-数据库事务.md](./实验08-数据库事务.md) | 注册三步写入的原子性 |
| [实验09-Redis与Token黑名单.md](./实验09-Redis与Token黑名单.md) | Redis 安装、token 存储、退出登录黑名单 |
| [实验10-AI大模型调用.md](./实验10-AI大模型调用.md) | ChatClient、工具调用、SSE |

---

## 8. 常见环境问题速查

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动报 `Port 8080 was already in use` | 上一个工程没关 | 关掉旧进程，或在 `application.yaml` 加 `server.port: 8081` |
| IDEA 里所有 `@RestController` 标红 | 依赖没下载完 | 右侧 Maven 面板 → 刷新（Reload All Maven Projects） |
| 访问页面 404 | 文件没放在 `src/main/resources/static/` 下 | 检查路径与大小写 |
| 中文返回乱码 | 编码问题 | 4.x 默认 UTF-8；检查文件本身是否 UTF-8 保存 |
| `Access denied` | 账号没有对应角色 | 见实验六的"授予管理员" |
| 连不上 MySQL | 服务没启动 / 密码错 / 时区 | `net start mysql80`；核对 `application.yaml`；URL 里带 `serverTimezone=Asia/Shanghai` |
| 连不上 Redis | 没启动 / 端口不对 | `redis-cli ping` 返回 PONG 才行 |

---

**开始吧：打开 [实验01-基础Web程序.md](./实验01-基础Web程序.md)。**
