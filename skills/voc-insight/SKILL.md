---
name: voc-insight
description: 保险客户声音（VoC）分析技能。查询与筛选客户反馈、对单条文本做情感与主题标注、生成产品评分归因报告、查看预警。Use when users ask about 客户反馈、满意度分析、负面反馈、投诉预警、理赔时效、销售误导、拒赔争议、评分归因、"最近客户在抱怨什么"、"这个产品评分为什么低"、"帮我分析这段客户原话"、"生成客户声音报告"、"analyze customer feedback"、"voice of customer report"、"why is the rating low"。
---

# VoC Insight — 保险客户声音分析技能

调用本地 VoC 服务的 REST 接口完成客户反馈分析。数据采集与 LLM 标注由服务完成，
本技能只负责查询、筛选与呈现。

## 前置条件

后端需运行在 `http://localhost:3001`：

```bash
curl http://localhost:3001/api/health
```

未启动则先运行（两套后端二选一，接口一致）：

```bash
# Node 版
cd server && npm run dev

# Java 版（Spring Boot，需先启动 docker-compose 里的 MySQL）
cd server-java && mvn spring-boot:run
```

完整接口文档（含请求示例、枚举值、在线调试）见 Swagger UI：
`http://localhost:3001/swagger-ui.html`（按客户反馈 / 主题词 / 预警中心 / 系统分组）。

## 接口约定

- 除 SSE 外所有接口返回统一结构 `Result{code, message, data, timestamp}`，取数据时解包 `data` 字段
- `code=0` 成功；`code=400` 参数校验失败，`message` 为具体字段错误（多个错误以「；」分隔）
- 业务错误码从 1000 开始：1002 主题词不存在、1003 反馈不存在、1004 预警不存在
- 参数有严格白名单校验：`source / sentiment / urgency / productLine` 只接受业务字典枚举值，
  `limit` 上限 200，`sortOrder` 仅 `asc / desc`，传错会直接返回 400

## 业务字典

分析前先熟悉这套枚举，所有查询与输出都要对齐。

### 主题标签（12 个，不要用列表外的标签）

理赔时效、赔付金额争议、拒赔争议、核保与投保、客服响应、服务态度、
条款清晰度、销售误导、续保与退保、APP 与网站体验、价格与性价比、理赔资料繁琐

### 产品线编码

`travel` 旅行保险 · `medical` 医疗保险 · `accident` 个人意外险 ·
`home` 家居保险 · `motor` 汽车保险 · `pet` 宠物保险

### 渠道编码

`survey` 问卷 · `claim` 索赔 · `service` 客服工单 ·
`social` 社媒 · `appstore` 应用商店 · `email` 邮件

### 紧急度四级

| 级别 | 含义 |
|------|------|
| critical | 拒赔、销售误导、威胁投诉至监管机构、扬言退保并公开曝光 |
| action | 明确要求跟进、索赔受阻、多次催促未果 |
| attention | 有明确不满但无升级诉求 |
| info | 一般咨询、中性描述、正面反馈 |

## 核心工作流

### 1. 明确意图

- **看整体状况** → 取概览统计
- **找某类问题** → 按主题 / 情感 / 紧急度筛选反馈
- **分析一句话** → 单条即时分析
- **解释评分** → 生成归因报告

### 2. 概览统计

```bash
curl http://localhost:3001/api/feedbacks/stats
```

返回反馈总量、今日新增、负面数与占比、待处置预警数、待复核数、平均评分，
以及按情感 / 渠道 / 产品线的分布。

### 3. 筛选反馈

```bash
# 某产品线的紧急负面反馈，按紧急度排序
curl "http://localhost:3001/api/feedbacks?productLine=travel&sentiment=negative&urgency=critical&sortBy=urgency&limit=20"

# 最近 7 天的理赔时效相关问题
curl "http://localhost:3001/api/feedbacks?urgency=action&timeRange=7d&limit=50"

# 低置信度、需要人工复核的条目
curl "http://localhost:3001/api/feedbacks?pendingReview=true&sortBy=confidence&sortOrder=asc"
```

支持的参数：`source` `sentiment` `urgency` `productLine` `topicId` `keyword`
`pendingReview`(true/false) `timeRange`(24h/today/7d/30d)
`sortBy`(createdAt/urgency/rating/confidence/publishedAt) `sortOrder`(asc/desc)
`page`(>=1) `limit`(1-200)

### 4. 单条文本即时分析

```bash
curl -X POST http://localhost:3001/api/feedbacks/analyze \
  -H "Content-Type: application/json" \
  -d '{"content":"明明買咗全保，最後話唔賠，搵笨！","productLine":"travel"}'
```

返回情感、主题标签、紧急度、定级理由、归因摘要、置信度。

支持繁体中文、粤语口语、英文、中英混排。

### 5. 评分归因报告

```bash
# 全部产品线
curl http://localhost:3001/api/feedbacks/insight

# 指定产品线
curl "http://localhost:3001/api/feedbacks/insight?productLine=travel"
```

返回反馈总数、平均评分、负面占比、主题分布（含各主题负面数）、
AI 归因结论、改进建议。

### 6. 预警

```bash
# 未处置的预警
curl "http://localhost:3001/api/alerts?unhandledOnly=true"

# 标记处置
curl -X PATCH http://localhost:3001/api/alerts/{id}/handle
```

## 输出模板

```markdown
## 客户声音分析报告 — {产品线}
> 统计周期: {时间范围} | 样本: {total} 条 | 负面占比: {negativeRatio}

### 总体状况
{summary}

### 主题分布
| 主题 | 反馈数 | 其中负面 |
|------|-------|---------|
| 理赔时效 | 32 | 28 |
| 拒赔争议 | 11 | 11 |

### 重点关注
- **{aiSummary}** — {urgency} · {productLine} · 置信度 {confidence}

### 改进建议
1. {suggestion}
2. {suggestion}
```

## 使用注意

- 主题标签必须取自 12 个预设值，不要自造
- AI 输出仅供参考，不构成业务或合规结论，报告中需保留这一定位
- 置信度低于 0.7 的判定尚未人工确认，引用时应说明
- 涉及客户原话引用时注意脱敏
