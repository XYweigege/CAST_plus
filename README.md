# VoC Insight · 保险客户声音智能分析平台

> 汇聚多源客户反馈，用 LLM 完成情感判定、主题归因与风险分级，把「没人看的问卷开放题」变成「可行动的流程改进清单」。
>
> 技术栈：**Java 17 + Spring Boot 3 服务端** · **React 19 + Vite 客户端** · MySQL / Redis / RabbitMQ。

---

## 一、要解决什么问题

保险公司的客户声音散落在四处：满意度问卷的开放题、索赔结案后的评价、客服工单记录、应用商店评论。它们的共同点是——**量很大，但基本没人系统性地看**。

由此产生三个具体问题：

| 问题             | 现状                              | 后果                                   |
| ---------------- | --------------------------------- | -------------------------------------- |
| 标签靠人工维护   | 业务人员手工填写关键词与分类      | 覆盖不全，客户的新说法跟不上涨         |
| 负面发现滞后     | 人工翻评论，T+1 甚至更久          | 拒赔、销售误导类高风险反馈错失介入窗口 |
| 评分只看得见数字 | 知道某产品 NPS 掉了，不知道为什么 | 无法定位到具体业务环节，改进无从下手   |

本系统针对这三点，做的是**从人工管理到智能分析的升级**，而不是从零建一套系统。

---

## 二、核心功能

### 1. 多源反馈汇聚

统一入口接入 6 类渠道：客户问卷（`survey`）、索赔反馈（`claim`）、客服工单（`service`）、社媒公开内容（`social`）、应用商店评论（`appstore`）、客户邮件（`email`）。支持 CSV / JSON 文件批量导入。

### 2. LLM 结构化标注

每条反馈由 AI 输出**六元组**，而非简单的正负面：

```json
{
  "sentiment": "negative",
  "topics": ["理赔时效", "客服响应"],
  "urgency": "critical",
  "urgencyReason": "拒赔未给出清晰解释且明确表示将投诉至监管机构",
  "aiSummary": "索赔审核周期过长且客服电话渠道无人接听",
  "confidence": 0.93
}
```

- `topics` 从预设的 12 个保险业务主题中选取（见 `BusinessDict.TOPIC_TAGS`），**不允许模型自造标签**，避免标签爆炸
- `urgency` 四级定级（`critical` / `action` / `attention` / `info`），`action` / `critical` 直接决定是否需要告警
- `confidence` 低于阈值（0.7）的判定**不写入终态**，转人工复核

### 3. 主题词智能扩展

业务人员维护的是书面术语（如「理赔时效」），客户说的是口语（「拖咗好耐都未賠」）。直接字符串匹配召回率极低。

系统用 LLM 把业务术语翻译成客户口语表达变体，并构成闭环：

```
人工录入主题词 → AI 扩展口语变体 → 人工确认启用 → 参与匹配并累计命中次数 → 淘汰零命中变体
```

AI 生成的变体默认 `approved=false`、`isActive=false`，需人工确认后才生效——**模型负责穷举，人负责把关**。扩展结果会写入 Redis 缓存预热，分析任务并发消费时直接命中，避免重复调用 LLM。

### 4. 异步分析与分级预警

「立即分析」或定时任务将采集到的反馈**投递到 RabbitMQ 消息队列**，消费者以并发 2~8 的速率处理，起到**天然限流、保护 LLM 配额**的作用。处理完成的结果经 SSE 实时推送到前端。

- **单条预警**：`action` / `critical` 级别反馈触发 SSE 推送 + 写入预警中心 + 发送邮件
- **主题突增检测**：某主题 24 小时内负面反馈超过阈值（默认 5 条）即触发预警，这往往是系统性问题的前兆，单条反馈看不出来。预警带 12 小时静默期（Redis TTL），避免告警疲劳

### 5. 评分归因报告

针对某产品线，输出「评分为什么是这个数」：

- **统计部分由代码完成**（反馈量、平均评分、负面占比、主题分布）——确定性计算，可复现
- **归纳部分交给 LLM**（归因结论 + 改进建议）——基于负面反馈摘要归纳

刻意不让模型做计数任务，那是它不擅长且容易编造的地方。

---

## 三、AI 用在哪些地方

AI 统一集中在服务端 [AiService](server-java/src/main/java/com/voc/insight/ai/AiService.java)，经 [AiClient](server-java/src/main/java/com/voc/insight/ai/dto/AiClient.java) 调用 OpenRouter（默认 `deepseek/deepseek-v3.2`，可用 `AI_MODEL` 覆盖）。真正用到 LLM 的只有三类**语义任务**：

### 1. 反馈结构化标注（核心）

对每条客户反馈输出**六元组**，替代只给正负面的通用情感 API：

- `sentiment` 情感、`topics` 主题（从预设 12 个白名单中选取，不允许自造标签）、`urgency` 四级紧急度、`urgencyReason` 定级理由、`aiSummary` 一句话归因、`confidence` 置信度
- 输出经**枚举白名单校验 + 置信度钳制**三重防线，防止模型输出脏数据
- 入口：定时任务 / MQ 消费者、文件导入、演示数据、前端「AI 试算」

### 2. 主题词智能扩展

把业务书面术语（如「理赔时效」）翻译成客户口语变体（如「拖咗好耐都未賠」），解决直接字符串匹配召回率低的问题。结果**缓存到 Redis 7 天**，避免并发消费时重复调用 LLM；人工确认启用后才参与匹配。

### 3. 评分归因报告（归纳部分）

在代码完成统计（反馈量、平均评分、负面占比、主题分布）之后，**仅取前 30 条负面反馈摘要**交给 LLM 生成归因结论 + 改进建议，既控制 token 成本，也刻意不让模型做它不擅长的计数任务。

### 设计边界：AI 不做全自动，可降级

- **低置信度有出口**：`confidence < 0.7` 的判定不写入终态，转人工复核队列，人工确认即终态
- **降级不中断**：未配置 `OPENROUTER_API_KEY` 或调用失败时，走负面词表 + 评分的保守规则兜底（`confidence` 固定 0.3，自动进复核），保证演示与开发不依赖外部服务

也就是说——**采集、去重、统计、预警规则全用代码做；只有「理解客户说了什么、把业务术语翻译成口语、归纳为什么掉分」这三类语义活交给 LLM。**

---

## 四、技术架构

```
┌──────────────────────────────────────────────────────────┐
│  React 19 + Vite + TS + TailwindCSS 4                     │
│  反馈洞察 │ 主题词管理 │ 评分归因 │ AI 试算                │
└────────────────────────┬─────────────────────────────────┘
                         │ REST（/api）+ SSE 实时推送
┌────────────────────────┴─────────────────────────────────┐
│  Spring Boot 3 · Java 17 · MyBatis-Plus 3.5               │
│  controller  Alert / Feedback / Topic / Notify            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ InsightJob(定时) ─► InsightService ─► RabbitMQ 队列  │  │
│  │  采集 → 预热扩展缓存 → 投递 → 消费者(并发)分析        │  │
│  │  ─► FeedbackProcessService ─► SSE / 预警 / 邮件      │  │
│  └─────────────────────────────────────────────────────┘  │
│  AiService(标注/扩展/归因) · FeedbackSource(语料/导入)     │
└────────────────────────┬─────────────────────────────────┘
        ┌──────────────┬─┴──────────────┬──────────────┐
        │  MySQL 8     │    Redis 7      │  RabbitMQ   │
        │  业务持久化    │ 去重/扩展缓存/   │  异步分析队列 │
        │              │  预警冷却        │              │
        └──────────────┴────────────────┴──────────────┘
```

| 层级                    | 技术                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------ |
| 前端（`client`）        | React 19 · Vite 7 · TypeScript · TailwindCSS 4 · lucide-react · SSE（`EventSource`） |
| 服务端（`server-java`） | Java 17 · Spring Boot 3.3 · MyBatis-Plus · Spring WebSocket/SSE · springdoc(Swagger) |
| 数据                    | MySQL 8 · Redis 7 · RabbitMQ 3.13                                                    |
| AI                      | OpenRouter（默认 `deepseek/deepseek-v3.2`，可用 `AI_MODEL` 覆盖）                    |
| 中间件编排              | Docker Compose                                                                       |

> 实时推送采用 **SSE**（`GET /api/notify/stream`），较传统的「前端轮询 / WebSocket 全双工」更轻量，浏览器 `EventSource` 自动重连。

---

## 五、关键设计决策

### 为什么不直接调情感分析 API

通用情感模型只输出极性，不懂保险语义：

- 「當初 agent 話全保，原來一堆除外責任」—— 通用模型判为中性，实际是**销售误导**，合规高风险
- 「個 app 好難用，upload document 次次 fail」—— 中英混排，通用模型容易漏判负面
- 「Claim rejected... escalate to the Insurance Authority」—— 需要识别为**监管投诉升级信号**，而不只是"不满"

而且业务要的不是极性，是「哪个环节出了问题、要不要现在处理」。

### 为何用消息队列承接 LLM 分析

批量导入或定时采集时，逐条同步调用 LLM 既慢又容易触发限流。引入 RabbitMQ 后：

- 生产端只需**投递任务**，立即返回
- 消费者**并发 + prefetch** 控速，天然保护第三方 LLM 配额
- 失败自动重试（`max-attempts: 3`），个别批次失败不影响整体

### 多语言与方言处理

香港客户的反馈是繁体中文、粤语口语、英文、中英混排的混合体。Prompt 中显式列出粤语负面表达（`唔賠`/`搵笨`/`搞咁耐`/`極不負責任`）并给出 few-shot 示例，覆盖四类语言形态。

### 人机协同而非全自动

AI 判定置信度低于 0.7 时，记录标记 `isReviewed=false` 进入**待复核队列**，前端提供一键校正。人工修正后的结果即为终态，并可作为后续 few-shot 样本回流。

这不是不信任模型，而是**让低置信度的判断有出口**——在合规敏感的保险场景，一条被误判的投诉比一条被漏标的赞美好得多。

### 降级不中断

未配置 `OPENROUTER_API_KEY` 时，系统用规则兜底（负面词表 + 评分），流程照常跑通，只是质量下降。同理，Redis 不可用时去重与冷却自动回退到数据库查询。保证演示和开发不依赖外部服务。

---

## 六、快速开始

### 前置

- Docker（运行 MySQL / Redis / RabbitMQ）
- JDK 17、Maven 3.8+
- Node.js ≥ 18（推荐 20 LTS）
- 一个 OpenRouter API Key

### 1. 启动基础设施

```bash
cd server-java
docker compose up -d
```

将自动创建 MySQL（内置建库建表 + 主题词种子数据），并拉起 Redis 与 RabbitMQ。

### 2. 配置并启动服务端

服务端配置在 `server-java/src/main/resources/application.yml`，关键项可用环境变量覆盖：

| 环境变量                                | 说明         | 默认                     |
| --------------------------------------- | ------------ | ------------------------ |
| `OPENROUTER_API_KEY`                    | LLM 调用密钥 | 留空走规则兜底           |
| `AI_MODEL`                              | 使用的模型   | `deepseek/deepseek-v3.2` |
| `SMTP_HOST` / `SMTP_USER` / `SMTP_PASS` | 预警邮件     | 留空不发送               |
| `NOTIFY_EMAIL`                          | 预警接收邮箱 | 留空                     |
| `REDIS_HOST` / `PORT`                   | Redis 连接   | `localhost:6379`         |
| `RABBITMQ_HOST` / `PORT`                | 队列连接     | `localhost:5672`         |

```bash
cd server-java
# Windows PowerShell
$env:OPENROUTER_API_KEY="sk-or-v1-xxx"
mvn spring-boot:run
```

服务端默认监听 `3001` 端口。

### 3. 启动客户端

```bash
cd client
npm install
npm run dev
```

Vite 已在 `vite.config.ts` 中将 `/api` 代理到 `http://localhost:3001`，与后端 SSE 通道共用前缀。

### 4. 访问

| 服务            | 地址                                    |
| --------------- | --------------------------------------- |
| 前端页面        | http://localhost:5173                   |
| 后端 API        | http://localhost:3001/api               |
| Swagger 文档    | http://localhost:3001/swagger-ui.html   |
| 健康检查        | http://localhost:3001/api/health        |
| RabbitMQ 控制台 | http://localhost:15672（guest / guest） |

**首次体验路径**：

1. 进入「主题词管理」，添加 `理赔时效`（建库种子数据已预置 4 个主题词）
2. 返回「反馈洞察」，点击「生成演示数据」→ 立刻得到一批已标注反馈
3. 点击主题词卡片上的「AI 扩展」→ 查看生成的客户口语表达变体，确认启用
4. 点击「立即分析」→ 观察分析任务进入 MQ、结果经 SSE 实时流入与预警
5. 进入「评分归因」→ 选择产品线，查看主题分布与 AI 改进建议
6. 进入「AI 试算」→ 粘贴任意文本，验证单条标注效果（不落库）

---

## 七、数据模型

MySQL 库 `voc_insight`，三张核心表：

### topic（主题词）

| 字段             | 说明                            |
| ---------------- | ------------------------------- |
| `text`           | 主题词（唯一）                  |
| `category`       | 归属类别（理赔 / 合规 / 服务…） |
| `is_active`      | 是否参与监控                    |
| `hit_count`      | 命中次数，驱动调优闭环          |
| `auto_generated` | 是否 AI 生成                    |
| `approved`       | 人工确认状态                    |

### feedback（客户反馈）

| 字段                                  | 说明                                             |
| ------------------------------------- | ------------------------------------------------ |
| `source` + `source_id`                | 渠道 + 业务系统内唯一 ID（**联合唯一约束去重**） |
| `content` / `rating` / `product_line` | 原始反馈、评分 1-5、产品线                       |
| `sentiment` / `topics` / `urgency`    | AI 输出的情感、主题(JSON)、紧急度                |
| `confidence`                          | 置信度 0-1                                       |
| `is_reviewed`                         | 是否已人工复核                                   |
| `topic_id`                            | 归属主题词 ID                                    |

### alert（预警）

| 字段                  | 说明                                    |
| --------------------- | --------------------------------------- |
| `type`                | `negative`（单条）/ `surge`（主题突增） |
| `urgency`             | 紧急度                                  |
| `is_read` / `handled` | 已读 / 是否处置                         |
| `feedback_id`         | 关联反馈 ID                             |

---

## 八、项目结构

```
├── server-java/                        服务端（Spring Boot）
│   ├── pom.xml
│   ├── docker-compose.yml              MySQL / Redis / RabbitMQ 编排
│   ├── sql/schema.sql                  建库建表 + 主题词种子数据
│   └── src/main/
│       ├── resources/application.yml   配置（端口 / 数据源 / MQ / LLM…）
│       └── java/com/voc/insight/
│           ├── VocInsightApplication.java   启动类（@EnableScheduling）
│           ├── ai/                     AiService、FeedbackAnalysis、PromptBuilder
│           ├── common/                 Result / ResultCode / 全局异常 / 分页
│           ├── config/                 CORS / MyBatis-Plus / RabbitMQ / Swagger
│           ├── constant/BusinessDict   主题 / 紧急度 / 渠道等业务字典
│           ├── controller/             Alert / Feedback / Topic / Notify
│           ├── dto/                    请求参数对象
│           ├── entity/                 Feedback / Topic / Alert
│           ├── job/InsightJob          定时分析任务
│           ├── mapper/                 MyBatis-Plus Mapper
│           ├── mq/                     分析任务消息 + 生产者 / 消费者
│           ├── service/                业务服务（Insight / Topic / Feedback / Mail…）
│           └── vo/                     统计、归因报告、主题扩展返回值
├── client/                             客户端（React + Vite）
│   └── src/
│       ├── App.tsx                     四个功能页
│       ├── constants.ts                业务字典（与 BusinessDict 保持一致）
│       ├── services/                   api.ts 封装 + socket.ts(SSE 客户端)
│       ├── components/                 FeedbackTable / FilterSortBar / Layout…
│       └── utils/sortFeedbacks.ts      排序
├── skills/voc-insight/SKILL.md         Agent Skill
└── docs/                               需求文档、运行指南、接入说明
```

---

## 九、生产落地需要补的部分

当前版本是**可运行的完整原型**，面向真实生产还需补充：

| 事项           | 说明                                                                                            |
| -------------- | ----------------------------------------------------------------------------------------------- |
| 数据源接入     | `FeedbackSourceService` 的演示语料需替换为问卷 / 索赔 / 客服系统的真实接口或定时导出            |
| 数据合规       | 香港《个人资料（私隐）条例》下，客户原始数据出境前须脱敏，或改用私有化部署模型处理含 PII 的字段 |
| 权限与多租户   | 当前无鉴权，需按部门隔离数据与预警路由                                                          |
| 高可用         | MySQL / Redis / RabbitMQ 单实例运行，生产建议主从与集群化部署                                   |
| LLM 限流与重试 | MQ 消费者并发已做控速，建议补充花费(feature)预算与更细粒度超时策略                              |

---

## 十、已知限制

- `feedback.topics` 以 JSON 字符串存储，主题筛选走 `contains` 匹配，大数据量下应改为关联表
- 演示语料为构造数据，用于验证链路与 Prompt 效果，不反映任何真实客户意见
- AI 生成的归因与建议仅供参考，不构成业务或合规结论
- 服务端暂无自动化测试套件，语料库本身自带人工标注，可作为后续标注准确率评测集
