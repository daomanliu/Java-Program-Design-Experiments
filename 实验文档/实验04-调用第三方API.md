# 实验四　调用第三方 API（test4）

> 对应工程：`test4`　｜　前置实验：无　｜　端口：8080
>
> 本实验调用 **和风天气（QWeather）** 的两个接口：
> ① 城市搜索（城市名 → 经纬度）　② 实时天气（经纬度 → 天气）

## 一、实验目标

1. 理解"我的后端再去调别人的后端"这种 **服务端调用** 模型；
2. 会用 `RestTemplate` 发 GET 请求并把 JSON 转成 Java 对象（DTO）；
3. 会用 `UriComponentsBuilder` 安全地拼接带中文的 URL；
4. 会用 `@Value` 把 **host** 和 **key** 放进配置文件；
5. 会处理"两级调用"：先查城市，再查天气；
6. 会用 Apifox 同时测试"自己的接口"和"第三方的原始接口"。

---

## 二、原理速览

### 2.1 为什么要走两级

和风天气的实时天气接口只认 **经纬度**，不认城市名。所以：

```text
用户输入"北碚"
   │
   ▼  ① GET ${host}/geo/v2/city/lookup?location=北碚&key=xxx
和风 Geo API 返回 [{ name:"北碚", lat:"29.81", lon:"106.44", adm1:"重庆市" }]
   │
   ▼  ② GET ${host}/weather/v1/current/29.81/106.44?key=xxx
和风 Weather API 返回 { condition:{text:"多云"}, temperature:{value:21.5,unit:"°C"}, ... }
   │
   ▼
包装成 Result 返回给前端
```

### 2.2 用到的类

| 类 | 作用 |
|----|------|
| `RestTemplate` | 发 HTTP 请求的"客户端"，一行代码发 GET 并拿到对象 |
| `HttpComponentsClientHttpRequestFactory` | 让 RestTemplate 底层用 Apache HttpClient（支持 gzip、连接池等） |
| `UriComponentsBuilder` | 拼 URL，自动做 URL 编码（中文城市名必须用） |
| `@Value` | 从 yaml 读配置 |
| `DTO`（`GeoResponse`/`WeatherResponse`） | 只声明我们关心的字段，其余字段忽略 |

> **DTO = Data Transfer Object**，中文叫"数据传输对象"，就是"专门用来接收/发送数据的类"。

---

## 三、实验准备：申请和风天气 Key

> 和风天气的免费开发版每天有调用额度，做实验完全够用。

### 3.1 注册

1. 打开 <https://www.qweather.com>（和风天气官网），点右上角 **控制台/登录**，注册账号；
2. 登录后进入 **控制台**；
3. 左侧 **项目管理** → **创建项目**，名称随意（例如 `java-web-lab`）；
4. 在项目里 **创建凭据（KEY）**，类型选 **Web API**；
5. 记下两个东西：

| 名称 | 形如 | 说明 |
|------|------|------|
| **API KEY** | `${QWEATHER_API_KEY}`（32 位） | 每个请求都要带 |
| **API Host** | `***.re.qweatherapi.com` | **新版控制台每个账号一个"专属域名"**，不是 `devapi.qweather.com` |

> ⚠️ **关键坑**：网上很多老教程还在用 `devapi.qweather.com`。新版账号如果用自己的专属 Host，用老域名会返回 **401/403**。请以控制台"项目设置"里显示的 **API Host** 为准。

### 3.2 先用浏览器验证 Key 能通

拿到 key 和 host 后，直接在浏览器里访问（把两处 xxx 换成你自己的）：

```text
https://你的host/geo/v2/city/lookup?location=北碚&key=你的key
```

能看到一段 JSON（里面有 `"name":"北碚"`）就说明账号没问题。
**这一步一定要先做**，不然接口写完了却调不通，分不清是代码问题还是账号问题。

---

## 四、实验步骤

### 步骤 1：新建工程 test4

Artifact `test4`，Package `com.daomanliu.test4`，依赖：**Spring Web** + **Lombok**。

### 步骤 2：加一个依赖

**文件路径**：`test4/pom.xml`

在 `<dependencies>` 里加：

```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
```

**为什么加它？** 因为代码里用了 `HttpComponentsClientHttpRequestFactory`（底层是 Apache HttpClient 5）。不加这个依赖，启动时会抛 `NoClassDefFoundError: org/apache/hc/client5/...`。
版本由父工程统一管理，**不用写 `<version>`**。

### 步骤 3：配置文件

**文件路径**：`test4/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: test4
qweather:
  host: https://***.re.qweatherapi.com
  key: ${QWEATHER_API_KEY}
```

> 把 `host` 和 `key` 换成你自己的。`https://` 前缀要写全，代码里是直接拼的。
>
> 🔐 真实项目里 key 不应该写进 git，应该用环境变量：`key: ${QW_KEY:}'`。本实验为了简单直接写死。

### 步骤 4：复用 `Result`

**文件路径**：`test4/src/main/java/com/daomanliu/test4/common/Result.java`

与实验二 **完全相同**（只改包名），这里不再重复贴，直接复制 `test2` 的那个文件过来改 `package` 即可。

### 步骤 5：写城市搜索的 DTO

**文件路径**：`test4/src/main/java/com/daomanliu/test4/dto/GeoResponse.java`

新建包 `dto`，建类 `GeoResponse`：

```java
package com.daomanliu.test4.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeoResponse {
    private String code;
    private List<Location> location;

    @Data
    public static class Location {
        private String name;    // 城市名
        private String lat;     // 纬度
        private String lon;     // 经度
        private String adm1;    // 省份
    }
}
```

**解释：**

- 和风返回的 JSON 里 `location` 是个**数组**，所以用 `List<Location>`；
- `Location` 是**静态内部类**，只声明我们关心的 4 个字段，**返回 JSON 里的其它字段会被 Jackson 自动忽略**（这是 DTO 最讨巧的地方）；
- `lat`/`lon` 官方返回的是**字符串**（如 `"29.81"`），所以这里声明成 `String`，后面再转 `double`；
- 注释里 `lat` 是**纬度**、`lon` 是**经度**（工程源码注释此处写反了，按这个为准）。

### 步骤 6：写天气的 DTO

**文件路径**：`test4/src/main/java/com/daomanliu/test4/dto/WeatherResponse.java`

```java
package com.daomanliu.test4.dto;

import lombok.Data;

@Data
public class WeatherResponse {
    private Condition condition;      // 天气现象
    private Temp temperature;         // 温度
    private Temp feelsLike;           // 体感温度
    private Double humidity;          // 湿度（0~1 之间的小数！）
    private Wind wind;                // 风
    private Temp pressure;            // 气压
    private Temp visibility;          // 能见度

    @Data
    public static class Condition {
        private String text;          // 如"晴""少云"
        private String code;
    }

    @Data
    public static class Temp {
        private Double value;
        private String unit;          // 如 "°C"
    }

    @Data
    public static class Wind {
        private Direction direction;
        private Temp speed;
        private Integer scale;        // 风力等级
    }

    @Data
    public static class Direction {
        private Integer degree;
        private String compass;       // 如 "n"、"sw"
    }
}
```

**结构对应关系**（看懂这张图，你就懂 DTO 怎么写了）：

```json
{
  "condition":    { "text": "多云", "code": "101" },   → Condition
  "temperature":  { "value": 21.5, "unit": "°C" },     → Temp
  "feelsLike":    { "value": 20.1, "unit": "°C" },     → Temp
  "humidity":     0.68,                                 → Double
  "wind": {
     "direction": { "degree": 180, "compass": "s" },   → Direction
     "speed":     { "value": 3.2, "unit": "km/h" },    → Temp
     "scale":     2                                    → Integer
  },
  "pressure":     { "value": 1002, "unit": "hPa" },    → Temp
  "visibility":   { "value": 12000, "unit": "km" }     → Temp
}
```

> **注意两个细节**：
> 1. `humidity` 是 **0~1 的小数**（不是百分数），前端显示要 ×100；
> 2. `visibility` 的单位字段可能写 `km` 但值是**米**（12000），所以 test10 里除以 1000 显示成 12 km。

### 步骤 7：写天气控制器

**文件路径**：`test4/src/main/java/com/daomanliu/test4/controller/WeatherController.java`

新建包 `controller`，建类 `WeatherController`：

```java
package com.daomanliu.test4.controller;

import com.daomanliu.test4.common.Result;
import com.daomanliu.test4.dto.GeoResponse;
import com.daomanliu.test4.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    @Value("${qweather.host}")
    private String host;

    @Value("${qweather.key}")
    private String key;

    private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

    @GetMapping
    public Result<WeatherResponse> getWeather(@RequestParam("city") String city) {
        URI geoUri = UriComponentsBuilder
                .fromUriString(host + "/geo/v2/city/lookup")
                .queryParam("location", city)
                .queryParam("key", key)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        GeoResponse geo = restTemplate.getForObject(geoUri, GeoResponse.class);
        if (geo == null || geo.getLocation() == null || geo.getLocation().isEmpty()) {
            return Result.fail(404, "找不到城市" + city);
        }

        GeoResponse.Location loc =  geo.getLocation().get(0);

        String lat = String.format("%.2f", Double.parseDouble(loc.getLat()));
        String lon = String.format("%.2f", Double.parseDouble(loc.getLon()));

        String url = host + "/weather/v1/current/" + lat + "/" + lon + "?key=" + key;

        WeatherResponse weather = restTemplate.getForObject(url, WeatherResponse.class);

        if (weather == null || weather.getCondition() == null) {
            return Result.fail(500, "天气查询失败");
        }
        return Result.success(loc.getName() + "(" + loc.getAdm1() + ")", weather);
    }
}
```

**逐段解释：**

#### ① 注入配置

```java
@Value("${qweather.host}") private String host;
@Value("${qweather.key}")  private String key;
```
从 yaml 里取出 `https://xxx.re.qweatherapi.com` 和 32 位 key。

#### ② 创建 RestTemplate（只创建一次，作为成员变量）

```java
private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
```
- 不要每次请求都 `new`，它内部有连接池；
- 传入 `HttpComponentsClientHttpRequestFactory` 可以走 Apache HttpClient 5（需要步骤 2 的依赖）。

#### ③ 第一次调用：城市搜索

```java
UriComponentsBuilder.fromUriString(host + "/geo/v2/city/lookup")
        .queryParam("location", city)     // 中文城市名
        .queryParam("key", key)
        .build()
        .encode(StandardCharsets.UTF_8)   // 关键：把"北碚"编码成 %E5%8C%97%E7%A2%9A
        .toUri();
```

- **一定不能**直接字符串拼接：中文不编码，服务器会返回 400/乱码；
- `build().encode().toUri()` 的顺序不要调换。

#### ④ 判空（非常重要）

```java
if (geo == null || geo.getLocation() == null || geo.getLocation().isEmpty())
```
三层都判：网络失败返回 null、返回里没有 location 字段、location 是空数组（城市查不到）。

#### ⑤ 经纬度处理

```java
String lat = String.format("%.2f", Double.parseDouble(loc.getLat()));
```
- 官方返回字符串，先转 `double` 再保留两位小数；
- 保留两位小数是因为 **v1 天气接口要求最多两位**，传太多位会 400。

#### ⑥ 第二次调用：实时天气

```java
String url = host + "/weather/v1/current/" + lat + "/" + lon + "?key=" + key;
WeatherResponse weather = restTemplate.getForObject(url, WeatherResponse.class);
```
- 经纬度在**路径**里，不再用 `UriComponentsBuilder`（因为都是数字，没有编码问题）；
- `getForObject(url, Class)` 会自动把 JSON 转成对象。

#### ⑦ 返回

```java
return Result.success(loc.getName() + "(" + loc.getAdm1() + ")", weather);
```
把"城市名(省份)"作为 msg，天气数据作为 data。前端就能直接显示标题。

### 步骤 8：放前端页面

**文件路径**：`test4/src/main/resources/static/weather.html`

不用写，直接复制。它做的事情：

| 行为 | 对应代码 |
|------|----------|
| 输入城市，点查询 | `fetch('/weather?city=' + encodeURIComponent(city))` |
| 渲染天气 | 从 `data.data.condition.text`、`data.data.temperature.value` 等取值 |
| 显示城市名 | 用的是响应里的 `msg`（后端拼好的"城市(省份)"） |

### 步骤 9：启动并验证

1. 运行 `Test4Application`；
2. 浏览器打开 `http://localhost:8080/weather.html`；
3. 输入 `北碚`，点查询 → 显示当前天气；
4. 再试 `北京`、`上海`、`不存在的城市xyz`（应提示找不到）。

**命令行验证也可以**：

```powershell
curl "http://localhost:8080/weather?city=北碚"
```

---

## 五、前端文件清单（直接复制，不要手写）

| 文件路径 | 作用 | 调用的接口 |
|----------|------|-----------|
| `test4/src/main/resources/static/weather.html` | 输入城市 → 显示实时天气 | `GET /weather?city=城市名` |

**访问地址**：`http://localhost:8080/weather.html`

---

## 六、接口测试（Apifox）

对应 `接口测试文档.md` 中的 **「GET test4天气查询」「GET test4GeoAPI」「GET test4和风天气 API」**。

> 这里要测 **三个** 接口：一个是你自己的（/weather），两个是第三方的（直连和风）。
> 直连测试的价值：**出问题时能立刻分清是我的代码问题，还是 key/账号问题。**

### 6.1 自己的接口：查询天气

| 项 | 填写 |
|----|------|
| 方法 | `GET` |
| URL | `{{baseUrl}}/weather` |
| Query 参数 | `city` = `北京` |

**期望返回（结构节选）**：

```json
{
  "code": 200,
  "msg": "北京(北京市)",
  "data": {
    "condition": { "code": "101", "text": "多云" },
    "feelsLike": { "unit": "°C", "value": 18.4 },
    "humidity": 0.53,
    "pressure": { "unit": "hPa", "value": 1013 },
    "temperature": { "unit": "°C", "value": 20.1 },
    "visibility": { "unit": "km", "value": 15000 },
    "wind": {
      "direction": { "compass": "n", "degree": 0 },
      "scale": 2,
      "speed": { "unit": "km/h", "value": 7.2 }
    }
  }
}
```

断言建议：

| 断言类型 | 表达式 | 期望 |
|----------|--------|------|
| JSON 字段 | `$.code` | `200` |
| JSON 字段 | `$.data.condition.text` | 存在（非空） |
| JSON 字段 | `$.data.temperature.value` | 存在 |

### 6.2 第三方接口 1：城市搜索（GeoAPI）

| 项 | 填写 |
|----|------|
| 方法 | `GET` |
| URL | `https://你的host/geo/v2/city/lookup` |
| Query 参数 | `location` = `北京`，(可选)`key` = 你的key |

> **建议把 host 和 key 也做成环境变量**（例如 `qwHost`、`qwKey`），这样换账号只改一处。

**期望返回**：

```json
{
  "code": "200",
  "location": [
    {
      "name": "北京",
      "id": "101010100",
      "lat": "39.90",
      "lon": "116.41",
      "adm2": "北京",
      "adm1": "北京市",
      "country": "中国",
      "tz": "Asia/Shanghai",
      "utcOffset": "+08:00",
      "type": "city",
      "rank": "10",
      "fxLink": "https://www.qweather.com/weather/beijing-101010100.html"
    }
  ],
  "refer": { "sources": ["QWeather"], "license": ["QWeather Developers License"] }
}
```

- 断言 `$.code` = `"200"`（**注意是字符串**，不是数字！第三方接口很多用字符串状态码）
- 断言 `$.location[0].name` = `北京`

### 6.3 第三方接口 2：实时天气

| 项 | 填写 |
|----|------|
| 方法 | `GET` |
| URL | `https://你的host/weather/v1/current/39.90/116.41` |
| Query 参数 | `key` = 你的key |

返回结构与 6.1 的 `data` 一样（多几个字段如 `validTime`、`cloudCover`、`uvIndex`，我们的 DTO 没接收而已）。

- 断言 `$.condition.text` 存在
- 断言 `$.temperature.unit` = `°C`

### 6.4 异常用例

| 用例 | 操作 | 期望 |
|------|------|------|
| 城市不存在 | `/weather?city=阿斯加德` | `code=404, msg="找不到城市阿斯加德"` |
| 少传参数 | `/weather`（不带 city） | HTTP **400**（缺少必填参数） |
| 第三方 key 错误 | 把 yaml 里的 key 改错重启 | `code=500, msg="天气查询失败"`（因为 Geo 返回里没有 location） |

---

## 七、实验报告要点

1. 和风控制台里 **API Host + KEY** 的截图（key 记得打码）；
2. 浏览器直连 Geo 接口成功的截图；
3. `WeatherController.java` 代码截图；
4. `weather.html` 查询"北碚"成功的截图（能看到温度、天气现象）；
5. Apifox 三条接口的截图；
6. 查询不存在城市返回 404 的截图。

---

## 八、常见问题（FAQ）

### Q1：返回 `code=404`，明明输入的是真实城市

看控制台日志里拼出来的 URL。常见原因：
- 城市名带了空格；
- 和风对某些小地名不识别，换 `重庆市北碚区` 这种写法试试；
- host 写错，请求根本没到和风（此时 geo 为 null）。

### Q2：启动报 `NoClassDefFoundError: org/apache/hc/client5/...`

少了 `httpclient5` 依赖，见步骤 2。

### Q3：返回 401 / 403（第三方报错）

- key 写错或过期；
- **host 用了老的 `devapi.qweather.com`**，换成控制台给的专属 host；
- 免费额度用完了（控制台能看到调用量）。

### Q4：中文城市名报 400

没有做 URL 编码。检查是不是把 `.encode(StandardCharsets.UTF_8)` 删了。

### Q5：返回的 `humidity` 是 0.53，不是 53

它就是 **0~1 的小数**，前端显示时要 `Math.round(humidity * 100) + '%'`。

### Q6：每次请求都很慢（1~2 秒）

因为要串行调两次第三方接口。真实项目可以：
- 用 Redis 缓存结果（实验九的思路）；
- 用 `CompletableFuture` 并发；
- 换用"城市ID + 天气"合并接口。

### Q7：能不能把 key 放在前端 JS 里？

**绝对不行**。前端代码用户能直接看到，key 会泄露并被盗刷。**密钥只能放在后端配置文件里**，前端只调你自己的接口。

---

## 九、思考题

1. 如果和风返回的 JSON 里出现 DTO 没声明的字段，Jackson 会报错吗？为什么？
2. 为什么 `RestTemplate` 要作为成员变量而不是在方法里 `new`？
3. 如果把 `geo.getLocation().get(0)` 换成 `get(1)`，会出现什么问题？怎么保证不越界？
4. 怎样把这个接口改造成"支持中英文城市名"？

---

## 附录：test4 依赖清单

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
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
    </dependency>
</dependencies>
```

---

**上一节：[实验03-文件上传下载.md](./实验03-文件上传下载.md)　｜　下一节：[实验05-权限管理与JWT.md](./实验05-权限管理与JWT.md)**
