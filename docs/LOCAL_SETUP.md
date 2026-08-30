# 本地运行指南

手把手在本地跑通完整前后端。

## 前置要求

| 工具 | 版本要求 | 检查命令 |
|------|----------|----------|
| Node.js | ≥ 18（推荐 20 LTS） | `node -v` |
| npm | ≥ 9 | `npm -v` |

需要准备一个 [OpenRouter API Key](https://openrouter.ai/settings/keys)。**没有也能跑**，系统会降级为规则兜底，只是标注质量下降。

---

## 第一步：安装依赖

```bash
cd server
npm install

cd ../client
npm install
```

> npm 慢可以切国内镜像：`npm config set registry https://registry.npmmirror.com`

---

## 第二步：配置环境变量

```bash
cp server/.env.example server/.env
```

编辑 `server/.env`，至少填入：

```env
DATABASE_URL="file:./dev.db"
PORT=3001
CLIENT_URL=http://localhost:5173

OPENROUTER_API_KEY=sk-or-v1-你的key
```

可选配置：

| 变量 | 说明 |
|------|------|
| `AI_MODEL` | 覆盖默认模型，默认 `deepseek/deepseek-v3.2` |
| `CRON_SCHEDULE` | 定时分析频率，默认 `*/30 * * * *` |
| `SMTP_*` / `NOTIFY_EMAIL` | 邮件预警，不配则只有站内与实时推送 |

---

## 第三步：初始化数据库

```bash
cd server
npx prisma generate
npx prisma db push
```

看到 `Generated Prisma Client` 与 `Your database is now in sync` 即成功。SQLite 无需额外安装，数据库文件生成在 `server/prisma/dev.db`。

---

## 第四步：启动

开两个终端：

```bash
# 终端 1
cd server
npm run dev
```

后端成功输出：

```
📊 保险客户声音智能分析系统 - 服务已启动
🌐 Server running on http://localhost:3001
🔌 WebSocket ready
⏰ Insight check scheduled: */30 * * * *
🤖 AI model: deepseek/deepseek-v3.2
```

若未配置 API Key，末尾会多一行 `⚠️ 未配置 API Key，将使用规则兜底`。

```bash
# 终端 2
cd client
npm run dev
```

---

## 第五步：体验流程

访问 **http://localhost:5173**

1. **主题词管理** → 添加 `理赔时效`（书面业务术语）
2. **反馈洞察** → 点「生成演示数据」，立刻得到一批已标注反馈
3. **主题词管理** → 点该主题词卡片上的 ✨ 图标，AI 会生成客户口语表达变体（如「拖咗好耐都未賠」「幾時先賠到」），点「确认启用」后参与匹配
4. **反馈洞察** → 点「立即分析」，观察新反馈流入；`需处理`/`紧急` 级别的会实时弹出预警
5. **评分归因** → 选一个产品线，查看主题分布与 AI 改进建议
6. **AI 试算** → 粘贴任意文本（支持粤语 / 繁中 / 英文 / 混排），验证单条标注效果

---

## 验证 AI 标注质量

```bash
cd server
export OPENROUTER_API_KEY=sk-or-v1-你的key
npm run eval
```

输出情感、主题、紧急度三项准确率。详见 README「效果评估」章节。

只跑不依赖 API 的单元测试：

```bash
cd server && npm test
```

---

## 常见问题

### Q1：后端报 `Cannot find module 'xxx'`

依赖没装全，重新安装并生成 Client：

```bash
cd server
npm install
npx prisma generate
```

### Q2：前端页面空白或接口报错

前端 Vite 代理指向 `http://localhost:3001`（见 `client/vite.config.ts`）。确认后端 `PORT` 一致且已启动。

### Q3：所有反馈的 AI 归因都是原文截断

说明走了规则兜底——`OPENROUTER_API_KEY` 未生效。检查：

- `.env` 文件是否放在 `server/` 目录下（不是项目根目录）
- Key 是否以 `sk-or-v1-` 开头
- 修改 `.env` 后是否重启了后端

### Q4：`prisma db push` 报错

```bash
node -v          # 确认 ≥ 18
npx prisma generate
npx prisma db push
```

改过 `schema.prisma` 后都要重跑这两条。

### Q5：邮件收不到

邮件是可选功能。未配置 `SMTP_*` 时系统只做站内预警与 WebSocket 推送，不影响其他功能。QQ 邮箱需用**授权码**而非登录密码。

### Q6：怎么看数据库

```bash
cd server
npx prisma studio
```

---

## 端口汇总

| 服务 | 端口 |
|------|------|
| 前端页面 | 5173 |
| 后端 API | 3001 |
| Prisma Studio | 5555 |
