# Java 后端开发技术文档（server-java）

> 面向需要理解或继续开发 `server-java`（Spring Boot 3）后端的读者。
> 覆盖：技术栈 → 分层架构 → 统一响应契约 → **每个接口** 的请求/响应/校验 → 异步分析流程 → AI 层 → 去重/预警/实时推送 → 配置与已知缺口。

---

## 目录

- [一、技术栈与依赖](#一技术栈与依赖)
- [二、分层架构](#二分层架构)
- [三、统一契约](#三统一契约)
  - 3.1 响应封装 `Result`
  - 3.2 分页封装 `PageResult`
  - 3.3 状态码 `ResultCode`
  - 3.4 全局异常处理
- [四、接口总览](#四接口总览)
- [五、主题词接口 `/api/topics`](#五主题词接口-apitopics)
- [六、客户反馈接口 `/api/feedbacks`](#六客户反馈接口-apifeedbacks)
- [七、预警中心接口 `/api/alerts`](#七预警中心接口-apialerts)
- [八、系统与实时通知接口](#八系统与实时通知接口)
- [九、异步分析主流程](#九异步分析主流程)
- [十、AI 层设计](#十ai-层设计)
- [十一、去重 / 预警 / 实时推送](#十一去重--预警--实时推送)
- [十二、配置说明](#十二配置说明)
- [十三、已知缺口与后续优化](#十三已知缺口与后续优化)

---

## 一、技术栈与依赖

| 技术                   | 版本       | 用途                            |
| ---------------------- | ---------- | ------------------------------- |
| Java / Spring Boot     | 17 / 3.3.5 | 基础框架                        |
| Spring Web             | 3.3        | HTTP 服务 + REST + `RestClient` |
| Spring WebSocket / MVC | 3.3        | SSE 实时推送（`SseEmitter`）    |
| MyBatis-Plus           | 3.5.7      | ORM + 分页插件                  |
| MySQL                  | 8.0        | 业务持久化（`utf8mb4`）         |
| Redis                  | 7          | 去重 / 扩展缓存 / 突增预警冷却  |
| RabbitMQ               | 3.13       | 异步分析队列（限流 + 重试）     |
| springdoc-openapi      | 2.6        | Swagger UI 接口文档             |
| Lombok                 | 最新       | 减少样板代码                    |

> 依赖清单见 [pom.xml](server-java/pom.xml)，中间件编排见 [docker-compose.yml](server-java/docker-compose.yml)：MySQL `3307`、Redis `6379`、RabbitMQ `5672`（控制台 `15672`）。

---

## 二、分层架构

```
┌─────────────────────────────────────────────────────────────┐
│ Controller 层（4 个）：Alert / Feedback / Topic / Notify      │
│  只做参数绑定 + 校验 + 组装 Result，不含业务逻辑               │
├─────────────────────────────────────────────────────────────┤
│ Service 层                                                     │
│  InsightService      分析主流程 + 突增检测                    │
│  FeedbackProcessService 保存→推送→预警的统一写入管道           │
│  TopicService          主题词增删改 + AI 扩展 + 人工确认       │
│  AlertService          预警状态流转                            │
│  FeedbackSourceService 语料生成 + CSV/JSON 导入解析            │
│  NotifyService         SSE 广播                               │
│  MailService           预警邮件                               │
├─────────────────────────────────────────────────────────────┤
│ MQ 层：AnalyzeTaskProducer / AnalyzeTaskConsumer               │
│   分析任务异步解耦 + 并发控速 + 重试                          │
├─────────────────────────────────────────────────────────────┤
│ AI 层：AiClient(HTTP) / AiService(标注/扩展/归因) / PromptBuilder│
├─────────────────────────────────────────────────────────────┤
│ 数据层：Mapper(MyBatis-Plus) + MySQL                            │
└─────────────────────────────────────────────────────────────┘
```

分层原则：Controller 不写业务；写入统一收敛到 `FeedbackProcessService`（定时 / 导入 / 演示三处复用）；AI 调用、邮件、数据源各自独立，替换只影响单个文件。

---

## 三、统一契约

### 3.1 响应封装 `Result<T>`

见 [Result.java](server-java/src/main/java/com/voc/insight/common/Result.java)。**所有接口**返回固定结构：

```json
{
  "code": 0,          // 0 成功，非 0 失败
  "message": "成功",  // 提示信息
  "data": { ... },    // 业务数据
  "timestamp": 1756564800000
}
```

前端统一按 `code === 0` 判断成功，成功后取 `data` 使用。HTTP 状态恒为 200，业务错误也走 `code` 表达（见 [client api.ts](client/src/services/api.ts) 里的 `ResultWrapper` 解包逻辑）。

### 3.2 分页封装 `PageResult<T>`

见 [PageResult.java](server-java/src/main/java/com/voc/insight/common/PageResult.java)：

```json
{
  "total": 128,
  "pages": 7,
  "current": 1,
  "size": 20,
  "records": [ ... ]
}
```

`pages = ceil(total / size)`。

### 3.3 状态码 `ResultCode`

见 [ResultCode.java](server-java/src/main/java/com/voc/insight/common/ResultCode.java)：

| code | 含义             |
| ---- | ---------------- |
| 0    | 成功             |
| 400  | 参数错误         |
| 404  | 资源不存在       |
| 409  | 资源冲突         |
| 500  | 系统繁忙         |
| 1001 | 主题词已存在     |
| 1002 | 主题词不存在     |
| 1003 | 反馈不存在       |
| 1004 | 预警不存在       |
| 1005 | 反馈内容不能为空 |
| 1006 | 导入数据解析失败 |

业务错误码从 1000 起，避免与 HTTP 状态码混淆。

### 3.4 全局异常处理

见 [GlobalExceptionHandler.java](server-java/src/main/java/com/voc/insight/common/GlobalExceptionHandler.java)，用 `@RestControllerAdvice` 把所有异常统一转为 `Result`：

| 异常                                                                                                                      | 处理后 code          |
| ------------------------------------------------------------------------------------------------------------------------- | -------------------- |
| `BizException`（业务）                                                                                                    | 自定义 code          |
| `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` / `HandlerMethodValidationException` | 400 + 拼接的校验消息 |
| 缺参 `MissingServletRequestParameterException`                                                                            | 400                  |
| 类型不匹配 / JSON 不可读                                                                                                  | 400                  |
| 接口不存在 `NoResourceFoundException`                                                                                     | 404                  |
| 兜底 `Exception`                                                                                                          | 500                  |

Controller 无需写 try/catch，只需抛 `BizException` 或靠字段校验注解即可。

---

## 四、数据模型

数据库为 MySQL 库 `voc_insight`，共 **3 张表**，建表语句见 [sql/schema.sql](server-java/sql/schema.sql)。

### 4.1 实体关系

```
Topic（主题词）  1 ──── N   Feedback（客户反馈，topic_id 关联）
Alert（预警）   N ─── 1 ─── Feedback（软关联，feedback_id 非外键）
```

> Alert 与 Feedback 是**软关联**：`feedback_id` 只是普通字段，无外键约束。因为突增预警（`type='surge'`）不对应单条反馈，加外键会限制这类场景。

### 4.2 `topic` 主题词表

| 字段                        | 类型           | 说明                             |
| --------------------------- | -------------- | -------------------------------- |
| `id`                        | VARCHAR(64) PK | 主键                             |
| `text`                      | VARCHAR(255)   | 主题词（唯一，`uk_topic_text`）  |
| `category`                  | VARCHAR(100)   | 归属类别（理赔/合规/服务…）      |
| `is_active`                 | TINYINT(1)     | 是否参与监控（默认 1）           |
| `hit_count`                 | INT            | 命中次数，驱动调优闭环（默认 0） |
| `auto_generated`            | TINYINT(1)     | 是否 AI 生成（默认 0）           |
| `approved`                  | TINYINT(1)     | 人工确认状态（默认 1）           |
| `created_at` / `updated_at` | DATETIME       | 创建 / 更新时间                  |

**三个字段构成调优闭环**：`autoGenerated`（区分来源）→ `approved`（AI 变体默认 false，需人工确认）→ `hitCount`（累计命中、淘汰零命中词）。

### 4.3 `feedback` 客户反馈表

字段分四组：

**① 原始信息：**

| 字段           | 类型           | 说明                                                   |
| -------------- | -------------- | ------------------------------------------------------ |
| `id`           | VARCHAR(64) PK | 主键                                                   |
| `title`        | VARCHAR(500)   | 标题                                                   |
| `content`      | TEXT           | 反馈正文（非空）                                       |
| `source`       | VARCHAR(32)    | 渠道编码（survey/claim/service/social/appstore/email） |
| `source_id`    | VARCHAR(128)   | 业务系统内唯一 ID，**去重依据**                        |
| `url`          | VARCHAR(1000)  | 原文链接                                               |
| `rating`       | INT            | 客户评分 1-5                                           |
| `product_line` | VARCHAR(32)    | 产品线编码（travel/medical/accident/home/motor/pet）   |
| `language`     | VARCHAR(16)    | zh-HK / en / mixed                                     |
| `author_name`  | VARCHAR(100)   | 客户名（应脱敏）                                       |
| `published_at` | DATETIME       | 反馈发生时间                                           |
| `created_at`   | DATETIME       | 入库时间                                               |

**② AI 分析输出：**

| 字段             | 类型         | 说明                                  |
| ---------------- | ------------ | ------------------------------------- |
| `sentiment`      | VARCHAR(16)  | 情感 positive/neutral/negative        |
| `topics`         | VARCHAR(500) | 主题标签 JSON 数组                    |
| `urgency`        | VARCHAR(16)  | 紧急度 info/attention/action/critical |
| `urgency_reason` | VARCHAR(500) | 定级理由                              |
| `ai_summary`     | VARCHAR(500) | 一句话归因                            |
| `confidence`     | DECIMAL(4,3) | 置信度 0-1                            |

**③ 人工复核：**

| 字段          | 类型       | 说明                 |
| ------------- | ---------- | -------------------- |
| `human_label` | TEXT       | 人工修正结果 JSON    |
| `is_reviewed` | TINYINT(1) | 是否已复核（默认 0） |

**④ 关联：** `topic_id` VARCHAR(64) —— 归属主题词 ID

**索引：** `uk_source_sid`(source+source_id 联合唯一)、`idx_feedback_created_at`、`idx_feedback_urgency`、`idx_feedback_sentiment`、`idx_feedback_topic_id`

> `uk_source_sid` 是去重的核心（DB 兜底）；Redis 作快速去重前置。`source_id` 为 NULL 不参与唯一性判定，允许多条。

### 4.4 `alert` 预警表

| 字段          | 类型           | 说明                                 |
| ------------- | -------------- | ------------------------------------ |
| `id`          | VARCHAR(64) PK | 主键                                 |
| `type`        | VARCHAR(32)    | 预警类型 negative / surge / critical |
| `title`       | VARCHAR(500)   | 标题（非空）                         |
| `content`     | TEXT           | 内容                                 |
| `urgency`     | VARCHAR(16)    | 紧急度                               |
| `is_read`     | TINYINT(1)     | 是否已读（默认 0）                   |
| `handled`     | TINYINT(1)     | 业务是否处置（默认 0）               |
| `feedback_id` | VARCHAR(64)    | 关联反馈 ID（软关联）                |
| `created_at`  | DATETIME       | 创建时间                             |

索引：`idx_alert_created_at`。

> **种子数据**：schema.sql 会预置 4 个启用中的主题词（理赔时效 / 拒赔争议 / 销售误导 / 客服响应），便于首次运行直接体验。

---

## 五、接口总览

| 控制器             | 前缀                 | 接口数 |
| ------------------ | -------------------- | ------ |
| TopicController    | `/api/topics`        | 8      |
| FeedbackController | `/api/feedbacks`     | 9      |
| AlertController    | `/api/alerts`        | 6      |
| NotifyController   | 无前缀（`/api/...`） | 3      |

> ⚠️ **路径命名注意**：`/stats`、`/insight`、`/analyze`、`/import`、`/generate-demo` 这些**静态路径**都需声明在 `/{id}` 之前，否则会被 `/{id}` 捕获。本项目中由多个 Controller 拆分规避了该问题，无需内存路由调整。

---

## 六、主题词接口 `/api/topics`

Controller: [TopicController.java](server-java/src/main/java/com/voc/insight/controller/TopicController.java)
请求体: [TopicSaveDTO.java](server-java/src/main/java/com/voc/insight/dto/TopicSaveDTO.java)

| 方法   | 路径                       | 说明                             |
| ------ | -------------------------- | -------------------------------- |
| GET    | `/api/topics`              | 列表（按命中次数、创建时间降序） |
| GET    | `/api/topics/{id}`         | 详情                             |
| POST   | `/api/topics`              | 创建                             |
| PUT    | `/api/topics/{id}`         | 更新                             |
| DELETE | `/api/topics/{id}`         | 删除                             |
| PATCH  | `/api/topics/{id}/toggle`  | 启停切换                         |
| POST   | `/api/topics/{id}/expand`  | **AI 扩展口语变体**              |
| PATCH  | `/api/topics/{id}/approve` | **人工确认 / 否决变体**          |

### 5.1 创建 POST `/api/topics`

请求体：

```json
{ "text": "理赔时效", "category": "理赔", "isActive": true }
```

校验：`text` 必填且 ≤50 字；重复时抛 `1001 主题词已存在`。实现时 `createTopic` 会 trim + 查重，`autoGenerated=false`、`approved=true`。

### 5.2 AI 扩展 POST `/api/topics/{id}/expand`

返回 `TopicExpandVO`：

```json
{
  "variants": [
    "理赔时效",
    "理赔慢",
    "拖咗好耐都未批",
    "claim processing delay",
    "..."
  ],
  "created": [
    /* 新落库的变体 Topic 数组 */
  ]
}
```

设计要点（见 [TopicServiceImpl.expand](server-java/src/main/java/com/voc/insight/service/impl/TopicServiceImpl.java)）：

1. 先查已有词表再插，避免重复（`text` 有唯一约束 + 并发 try/catch 兜底）
2. 新变体 **`approved=false, isActive=false`** —— 不确认不参与监控，这是「AI 穷举、人工把关」的落点
3. `category` 继承父主题词

### 5.3 人工确认 PATCH `/api/topics/{id}/approve`

请求体：`{"approved": true}`（缺失 `approved` 抛 400；`false` 即否决）。

确认通过时 `approved=true, isActive=true`，开始参与监控匹配。

---

## 七、客户反馈接口 `/api/feedbacks`

Controller: [FeedbackController.java](server-java/src/main/java/com/voc/insight/controller/FeedbackController.java)
查询参数: [FeedbackQueryDTO.java](server-java/src/main/java/com/voc/insight/dto/FeedbackQueryDTO.java)

| 方法   | 路径                           | 说明                           |
| ------ | ------------------------------ | ------------------------------ |
| GET    | `/api/feedbacks`               | 列表（多维筛选 + 排序 + 分页） |
| GET    | `/api/feedbacks/stats`         | 概览统计                       |
| GET    | `/api/feedbacks/insight`       | 评分归因报告                   |
| GET    | `/api/feedbacks/{id}`          | 详情                           |
| POST   | `/api/feedbacks/analyze`       | 单条即时分析（不落库）         |
| POST   | `/api/feedbacks/import`        | CSV / JSON 批量导入            |
| POST   | `/api/feedbacks/generate-demo` | 生成演示数据                   |
| PATCH  | `/api/feedbacks/{id}/review`   | 人工复核                       |
| DELETE | `/api/feedbacks/{id}`          | 删除                           |

### 6.1 列表 GET `/api/feedbacks`

支持多维筛选（query string，均校验枚举）：

| 参数             | 可选值                                          | 说明                                    |
| ---------------- | ----------------------------------------------- | --------------------------------------- |
| `page` / `limit` | int                                             | 页码 ≥1；每页 1~200，默认 1/20          |
| `source`         | survey/claim/service/social/appstore/email      | 渠道                                    |
| `sentiment`      | positive/neutral/negative                       | 情感                                    |
| `urgency`        | info/attention/action/critical                  | 紧急度                                  |
| `productLine`    | travel/medical/accident/home/motor/pet          | 产品线                                  |
| `topicId`        | string                                          | 归属主题词 ID                           |
| `keyword`        | ≤100 字                                         | 匹配 `content` 或 `aiSummary`           |
| `pendingReview`  | true/false                                      | `true` 仅看待复核（`isReviewed=false`） |
| `timeRange`      | 24h/today/7d/30d                                | 时间范围                                |
| `sortBy`         | createdAt/rating/confidence/publishedAt/urgency | 默认 createdAt                          |
| `sortOrder`      | asc/desc                                        | 默认 desc                               |

返回 `PageResult<Feedback>`。

**设计要点（`urgency` 内存排序）**：紧急度的业务顺序是 `critical < action < attention < info`，与字典序不同，且 MyBatis-Plus 不支持自定义排序规则，故 `sortBy=urgency` 时走**全量取出 → 内存排序 → 切片**（见 [FeedbackServiceImpl.page](server-java/src/main/java/com/voc/insight/service/impl/FeedbackServiceImpl.java)）。已知性能代价，生产应加 `urgencyOrder` 数值字段走 DB 排序。

### 6.2 概览统计 GET `/api/feedbacks/stats`

返回 `FeedbackStatsVO`：`total / today / negative / negativeRatio / pendingAlert / pendingReview / avgRating`，及 `bySentiment / bySource / byProduct` 分组计数。

### 6.3 评分归因报告 GET `/api/feedbacks/insight`

- 参数 `productLine`（可选，枚举校验；不带则统计全部产品线）
- 内部取最新 300 条反馈，交由 `AiService.generateInsightReport`（统计用代码、归纳用 LLM，见第十章）。返回 `InsightReport`：`totalFeedback / avgRating / negativeRatio / topTopics[] / summary / suggestions[]`

### 6.4 单条即时分析 POST `/api/feedbacks/analyze`

请求体 [AnalyzeDTO.java](server-java/src/main/java/com/voc/insight/dto/AnalyzeDTO.java)：`content`(必填 ≤2000)、`productLine`、`rating`(1~5)、`language`。

返回 `FeedbackAnalysis`（六元组），**不落库**，专用于前端「AI 试算」/ 调试 Prompt。

### 6.5 批量导入 POST `/api/feedbacks/import`

请求体 [ImportDTO.java](server-java/src/main/java/com/voc/insight/dto/ImportDTO.java)：`content`(必填 ≤1MB)、`format`(json/csv，默认 json)。

返回 `{"created": N}`。解析见 [FeedbackSourceService.parseFeedbackFile](server-java/src/main/java/com/voc/insight/service/FeedbackSourceService.java)：JSON 兼容裸数组与 `{data:[...]}`；CSV 按表头（`content`/`text` 二选一作为正文列）解析。解析到 0 条抛 `1006`。

### 6.6 生成演示数据 POST `/api/feedbacks/generate-demo`

请求体（可选）：`{"count": 60}`，范围 1~500，超范围抛 400。
返回 `{"created": N}`。语料库每条自带人工标注，既是演示数据也是潜在的评测集。

### 6.7 人工复核 PATCH `/api/feedbacks/{id}/review`

请求体 [ReviewDTO.java](server-java/src/main/java/com/voc/insight/dto/ReviewDTO.java)，仅提交要修正的字段：`sentiment` / `topics([]≤10)` / `urgency`。

设计（见 [FeedbackServiceImpl.review](server-java/src/main/java/com/voc/insight/service/impl/FeedbackServiceImpl.java)）：写入 `humanLabel` 保留完整修正记录，`isReviewed=true`，**`confidence=1`**（人工确认即终态，不再进入复核）。

### 6.8 删除 DELETE `/api/feedbacks/{id}`

按 ID 删除；返回空 `data`。

---

## 八、预警中心接口 `/api/alerts`

Controller: [AlertController.java](server-java/src/main/java/com/voc/insight/controller/AlertController.java)

| 方法   | 路径                      | 说明                      |
| ------ | ------------------------- | ------------------------- |
| GET    | `/api/alerts`             | 列表（含未读/未处置计数） |
| PATCH  | `/api/alerts/{id}/read`   | 标记已读                  |
| PATCH  | `/api/alerts/read-all`    | 全部已读                  |
| PATCH  | `/api/alerts/{id}/handle` | **标记业务已处置**        |
| DELETE | `/api/alerts/{id}`        | 删除单条                  |
| DELETE | `/api/alerts`             | 清空全部                  |

### 7.1 列表 GET `/api/alerts`

| 参数            | 可选值     | 默认 |
| --------------- | ---------- | ---- |
| `page`          | ≥1         | 1    |
| `limit`         | 1~200      | 50   |
| `unreadOnly`    | true/false | 不筛 |
| `unhandledOnly` | true/false | 不筛 |

返回：

```json
{
  "data": [ /* Alert[] 当前页 */ ],
  "unreadCount": 3,
  "unhandledCount": 1,
  "pagination": { "total": 10, "pages": 1, "current": 1, "size": 50, "records": [...] }
}
```

**两级状态设计**：`isRead`（看到了）与 `handled`（处理完了）是两个独立状态位。统计里的 `pendingAlert` 用 `handled=false`，避免「已读未处理」被算作已处理导致统计失真。

---

## 九、系统与实时通知接口

Controller: [NotifyController.java](server-java/src/main/java/com/voc/insight/controller/NotifyController.java)

| 方法 | 路径                   | 说明                            |
| ---- | ---------------------- | ------------------------------- |
| GET  | `/api/notify/stream`   | **SSE 实时推送通道**            |
| GET  | `/api/health`          | 健康检查                        |
| POST | `/api/check-feedbacks` | 手动触发一轮分析（异步投递 MQ） |

### 8.1 SSE 通道 GET `/api/notify/stream`

- 返回 `SseEmitter`（超时 0 = 不超时，靠心跳与客户端 `EventSource` 自动重连维持）
- 事件：`feedback:new`（新反馈）、`alert`（新预警）
- 单向广播：见 [NotifyService.java](server-java/src/main/java/com/voc/insight/service/NotifyService.java)；并发维护 `ConcurrentHashMap.newKeySet()` 的事件源集合，生产失败即移除断连连接

**为什么选 SSE 而非 WebSocket**：本系统只需要服务端单向推送（新反馈、预警），不需要客户端反向发消息。SSE 基于 HTTP、`EventSource` 原生自动重连，无需 WebSocket 的握手/协议/心跳，实现与运维成本更低。前端接入见 [socket.ts](client/src/services/socket.ts)。

### 8.2 手动分析 POST `/api/check-feedbacks`

返回 `{"message": "...", "queued": N}`，`queued` 为投递到 MQ 的分析任务数。结果由消费者异步产生，经 SSE 实时到达（见第九章）。

---

## 十、异步分析主流程

### 9.1 整体链路

见 [InsightService.java](server-java/src/main/java/com/voc/insight/service/InsightService.java) 与 [InsightJob.java](server-java/src/main/java/com/voc/insight/job/InsightJob.java)：

```
定时任务 @Scheduled(cron) ──► runCheck()
  ├─ 拉取全部 isActive 主题词（空则跳过）
  ├─ 采集一批反馈（当前为演示语料，见 FeedbackSourceService.fetchNewFeedback）
  ├─ 对每个主题词：
  │     aiService.expandTopic(text)   // 预热扩展结果写入 Redis，避免消费者重复调 AI
  │     对每条反馈调用 analyzeTaskProducer.send(...)  // 投递到 RabbitMQ
  └─ detectSurge()                     // 突增检测（读已落库数据，在生产者侧同步执行）

消费者 AnalyzeTaskConsumer（并发 2~8）─► processAnalyzeTask(topicId, item)
  ├─ 主题词已删/停用 → 丢弃
  ├─ expandTopic 命中 Redis 缓存 → preMatch 预匹配
  ├─ aiService.analyzeFeedback  AI 六元组标注
  ├─ 归属判断：命中扩展词（字面） 或 AI 主题与主题词相关（语义）
  ├─ FeedbackProcessService.saveFeedback → 去重 → 落库 → 推送 → 预警
  └─ match 命中则主题 hitCount +1
```

### 9.2 为什么用 RabbitMQ

- 生产端只投递任务立即返回，避免逐条同步调 LLM 的阻塞与慢响应
- 消费者**并发（2~8）+ prefetch(5)** 控速，天然保护第三方 LLM 配额（见 `application.yml` 的 `spring.rabbitmq.listener.simple`）
- 失败**自动重试**（max-attempts 3、initial-interval 5s），个别批次失败不影响整体

### 9.3 突增检测 `detectSurge`

- 统计 24h 内 `sentiment=negative` 的反馈，按主题聚合计数
- 超过阈值（默认 `surge-threshold=5`）且在白名单 `TOPIC_TAGS` 内 → 创建 `surge` 预警并推送
- **静默期**：Redis 冷却 key（TTL 即静默期，默认 12h）；Redis 不可用回退 DB 查询

> 局限：阈值是固定绝对值（5 条），未考虑基线，生产宜用相对基线的统计（与过去 30 天均值的标准差比较）。

---

## 十一、AI 层设计

### 10.1 AiClient（HTTP 客户端）

见 [AiClient.java](server-java/src/main/java/com/voc/insight/ai/AiClient.java)：

- 用 Spring 6.1+ 的 **`RestClient`**（同步，无需 WebFlux），连接 OpenRouter
- `isConfigured()` 判断是否真正可用：非空 **且** 不以 `your_` 开头占位——避免用占位 Key 发必然失败的空请求刷 401
- `chat(system, user, temperature, maxTokens)` 统一调用 `/chat/completions` 并解析 `choices[0].message.content`

### 10.2 AiService（业务封装）

见 [AiService.java](server-java/src/main/java/com/voc/insight/ai/AiService.java)，封装三块 LLM 能力 + 规则兜底：

1. **主题词扩展** `expandTopic`：结果缓存 Redis（按主题词哈希，7 天；兜底结果仅 10 分钟，避免污染）
2. **反馈标注** `analyzeFeedback`：输出**六元组**，经「JSON 提取 → 枚举白名单校验 → 置信度钳制 0~1」三重防线
3. **归因报告** `generateInsightReport`：统计用代码算，仅取**前 30 条负面摘要**交 LLM 归纳（控制 token、不让模型计数）

**规则兜底** `fallbackAnalysis`（未配置 AI / 调用失败时）：只做负面识别（负面词表 + 评分 ≤2），`confidence` 固定 0.3 → 自动进待复核队列；不做正面识别（保守策略：宁可正判中，不把负判中）。

### 10.3 PromptBuilder（提示词集中管理）

见 [PromptBuilder.java](server-java/src/main/java/com/voc/insight/ai/PromptBuilder.java)，三个 Prompt：

| Prompt                | 温度 | maxTokens | 关键约束                                                                                           |
| --------------------- | ---- | --------- | -------------------------------------------------------------------------------------------------- |
| `buildExpandPrompt`   | 0.2  | 400       | 覆盖繁中/粤语/英文/混排；**不加泛化词**；6~15 个                                                   |
| `buildAnalysisPrompt` | 0.1  | 500       | 主题从白名单选不造标签；`critical` 显式含「威胁投诉至监管机构」；few-shot 三条；只输出 JSON 空模板 |
| `buildInsightPrompt`  | 0.3  | 600       | summary 3 句内「要观点不要罗列」；suggestions 3 条「可执行不要空话」                               |

温度刻意设低（标注 0.1）保证同文本多次分析结果一致。

---

## 十二、去重 / 预警 / 实时推送

见 [FeedbackProcessService.java](server-java/src/main/java/com/voc/insight/service/FeedbackProcessService.java)：

1. **去重**：优先 Redis `SADD voc:dedup:{source}`（O(1)，返回 0 表示已存在）；Redis 不可用回退 DB 查 `source+sourceId`；DB 唯一约束 `uk_source_sid` 兜底。`sourceId` 为空不判重。
2. **保存 + 推送**：落库后 SSE 广播 `feedback:new`
3. **预警**：仅 `urgency ∈ {action, critical}` 触发（避免告警疲劳）→ 写 `alert` 表 + SSE 广播 `alert` + `mailService.sendAlert(...)`（未配置 SMTP/收件邮箱时静默跳过）

---

## 十三、配置说明

见 [application.yml](server-java/src/main/resources/application.yml)，端口 `3001`。

| 配置项                       | 环境变量覆盖                   | 默认                                            | 说明               |
| ---------------------------- | ------------------------------ | ----------------------------------------------- | ------------------ |
| 数据库                       | —                              | MySQL `localhost:3307/voc_insight` root/root123 | 本机 Docker        |
| Redis                        | `REDIS_HOST/PORT`              | localhost:6379                                  | 去重/扩展缓存/冷却 |
| RabbitMQ                     | `RABBITMQ_HOST/PORT/USER/PASS` | localhost:5672 guest/guest                      | 分析队列           |
| Mail                         | `SMTP_HOST/USER/PASS`          | 空                                              | 预警邮件（可选）   |
| 通知邮箱                     | `NOTIFY_EMAIL`                 | 空                                              | 预警收件人         |
| OpenRouter                   | `OPENROUTER_API_KEY`           | 空（走兜底）                                    | AI 服务            |
| AI 模型                      | `AI_MODEL`                     | deepseek/deepseek-v3.2                          | 可切换对比         |
| 分析 cron（6 位含秒）        | —                              | `0 */30 * * * ?`                                | 定时频率           |
| 批量大小 / 突增阈值 / 静默期 | —                              | 12 / 5 / 12h                                    | `voc.insight.*`    |
| 置信度阈值                   | —                              | 0.7                                             | 低于转人工复核     |

Swagger：`GET /swagger-ui.html`、`GET /v3/api-docs`。

---

## 十四、已知缺口与后续优化

| 缺口                       | 说明                                                                  | 建议                                 |
| -------------------------- | --------------------------------------------------------------------- | ------------------------------------ |
| `settings` 接口未实现      | 前端 `settingsApi` 仍调用 `/api/settings`，Java 后端无对应 Controller | 前端移除或后端补 SettingsController  |
| `topics` 存 JSON 字符串    | `feedback.topics` 参与 `contains` 匹配，无法走索引、可能误匹配        | 建 `FeedbackTopic` 关联表            |
| `urgency` 内存排序         | `sortBy=urgency` 需全量取后在内存排，数据量大有性能风险               | 加 `urgencyOrder` 数值字段走 DB 排序 |
| 无鉴权/多租户              | 当前全量开放                                                          | 引入 Spring Security + 部门/角色隔离 |
| 中间件单实例               | MySQL/Redis/RabbitMQ 各单点                                           | 生产主从 + 集群                      |
| 无自动化测试               | 语料自带人工标注，可作为评测集                                        | 补充 Junit 测试 + 标注准确率评测     |
| `check-feedbacks` 同步探测 | 触发接口同步返回 queued，耗时随批次增长                               | 可返回任务 ID，异步查状态            |
