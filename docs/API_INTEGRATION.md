# 数据源接入指南

本文件说明如何把内置的演示语料替换为真实业务数据源，以及各接口的调用方式。

---

## 一、当前数据链路

```
fetchNewFeedback()              采集一批新增反馈（当前返回演示语料）
        ↓
expandTopic()                   主题词扩展为客户口语表达
        ↓
preMatchTopic()                 文本预匹配，未命中则跳过，节省 AI 调用
        ↓
analyzeFeedback()               LLM 结构化标注
        ↓
落库 + 命中计数 + 预警判定
        ↓
detectSurge()                   主题突增检测
```

主流程在 `server/src/jobs/insightRunner.ts`。

---

## 二、接入真实数据源

### 2.1 替换采集函数

编辑 `server/src/jobs/insightRunner.ts` 中的 `fetchNewFeedback()`：

```typescript
async function fetchNewFeedback(): Promise<FeedbackItem[]> {
  // 替换前：演示语料
  // return generateDemoFeedback(BATCH_SIZE);

  // 替换后：从业务系统拉取
  const [surveys, claims, tickets] = await Promise.all([
    fetchSurveyAnswers(since),
    fetchClaimFeedback(since),
    fetchServiceTickets(since)
  ]);

  return [...surveys, ...claims, ...tickets].map(toFeedbackItem);
}
```

返回值需满足 `FeedbackItem`（见 `server/src/types.ts`）：

| 字段 | 必填 | 说明 |
|------|------|------|
| `content` | ✅ | 反馈正文 |
| `source` | ✅ | 渠道编码，见 `constants.ts` 的 `SOURCES` |
| `sourceId` | 建议 | 业务系统内的唯一 ID，用于去重；**不填会导致重复入库** |
| `rating` | 可选 | 客户评分 1-5，有助于 AI 判定 |
| `productLine` | 可选 | 产品线编码 |
| `language` | 可选 | `zh-HK` / `en` / `mixed`，不填则由 `detectLanguage` 推断 |
| `publishedAt` | 可选 | 反馈发生时间 |

### 2.2 字段映射示例

```typescript
function toFeedbackItem(row: SurveyRow): FeedbackItem {
  return {
    content: row.open_answer,
    source: 'survey',
    sourceId: `survey-${row.id}`,          // 必须全局唯一
    rating: row.nps_score,                  // 若是 NPS 0-10 需换算
    productLine: mapProduct(row.product_code),
    authorName: maskName(row.customer_name),// 出境前脱敏
    publishedAt: new Date(row.submitted_at)
  };
}
```

### 2.3 按需接入各渠道

| 渠道 | 常见接入方式 | 注意 |
|------|------------|------|
| 问卷 | 问卷系统 Open API，按提交时间增量拉取 | NPS 0-10 与 CSAT 1-5 需统一换算 |
| 索赔 | 索赔系统工单表定时导出或视图 | 关注结案评价字段与拒赔标记 |
| 客服工单 | 工单系统 API / 数据库只读副本 | 需剥离客服内部备注，只保留客户原话 |
| 社媒 | 平台公开接口 | 仅取公开内容，注意平台条款 |
| 应用商店 | 官方 API 或定期导出 | 评论自带评分，可直接映射 |
| 邮件 | 邮件系统归档 | 需去除签名档与历史引用 |

---

## 三、文件导入（无需对接接口）

适合一次性导入历史数据做回测。

### JSON 格式

```json
[
  {
    "content": "理賠拖咗三個星期都未批",
    "source": "claim",
    "sourceId": "claim-10086",
    "rating": 1,
    "productLine": "medical",
    "publishedAt": "2026-08-01T10:00:00Z"
  }
]
```

也接受 `{ "data": [...] }` 包装。

### CSV 格式

首行为表头，必须包含 `content` 列（或 `text`）：

```csv
content,source,sourceId,rating,productLine
理賠拖咗好耐,claim,c-001,1,medical
Claim rejected,claim,c-002,1,travel
```

### 调用

```bash
curl -X POST http://localhost:3001/api/feedbacks/import \
  -H "Content-Type: application/json" \
  -d '{"format":"csv","content":"content,source\n理賠好慢,claim"}'
```

响应：

```json
{ "total": 2, "created": 2 }
```

`created` 小于 `total` 通常是因为 `source + sourceId` 重复被跳过。

---

## 四、接口参考

### 主题词

```bash
# 列表
GET /api/topics

# 创建
curl -X POST http://localhost:3001/api/topics \
  -H "Content-Type: application/json" \
  -d '{"text":"理赔时效"}'

# AI 扩展口语变体
curl -X POST http://localhost:3001/api/topics/{id}/expand

# 确认启用
curl -X PATCH http://localhost:3001/api/topics/{id}/approve \
  -H "Content-Type: application/json" -d '{"approved":true}'
```

### 反馈

```bash
# 筛选查询
GET /api/feedbacks?sentiment=negative&urgency=critical&productLine=travel&sortBy=urgency&page=1&limit=20

# 概览统计
GET /api/feedbacks/stats

# 归因报告
GET /api/feedbacks/insight?productLine=travel

# 单条即时分析
curl -X POST http://localhost:3001/api/feedbacks/analyze \
  -H "Content-Type: application/json" \
  -d '{"content":"明明買咗全保，最後話唔賠，搵笨！","productLine":"travel"}'

# 人工复核
curl -X PATCH http://localhost:3001/api/feedbacks/{id}/review \
  -H "Content-Type: application/json" \
  -d '{"sentiment":"negative","topics":["拒赔争议"],"urgency":"critical"}'
```

### 预警

```bash
GET /api/alerts?unhandledOnly=true
PATCH /api/alerts/{id}/handle
```

### 手动触发

```bash
curl -X POST http://localhost:3001/api/check-feedbacks
```

---

## 五、WebSocket

```javascript
import { io } from 'socket.io-client';

const socket = io('http://localhost:3001');

socket.emit('subscribe', ['理赔时效']);

socket.on('feedback:new', (fb) => {
  console.log('新反馈', fb.aiSummary, fb.urgency);
});

socket.on('alert', (alert) => {
  console.log('预警', alert.title, alert.urgency);
});
```

---

## 六、接入时注意的合规事项

1. **数据脱敏**：客户姓名、联系方式、保单号在送往境外 LLM 前必须脱敏，或改用私有化部署模型处理含 PII 的字段
2. **最小必要**：只采集分析所必需的字段，不要把整个客户档案灌进来
3. **留存期限**：明确原始反馈的留存与清理策略
4. **输出定位**：AI 归因与建议仅供内部参考，不作为对外结论，界面需明示
