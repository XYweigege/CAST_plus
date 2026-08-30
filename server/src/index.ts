import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { Server } from 'socket.io';
import dotenv from 'dotenv';
import cron from 'node-cron';

import { prisma } from './db.js';
import topicsRouter from './routes/topics.js';
import feedbacksRouter from './routes/feedbacks.js';
import settingsRouter from './routes/settings.js';
import alertsRouter from './routes/alerts.js';
import { runInsightCheck } from './jobs/insightRunner.js';

dotenv.config();

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: {
    origin: process.env.CLIENT_URL || 'http://localhost:5173',
    methods: ['GET', 'POST']
  }
});

app.use(cors());
app.use(express.json({ limit: '5mb' }));

// Routes
app.use('/api/topics', topicsRouter);
app.use('/api/feedbacks', feedbacksRouter);
app.use('/api/settings', settingsRouter);
app.use('/api/alerts', alertsRouter);

app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// 手动触发一次反馈分析
app.post('/api/check-feedbacks', async (_req, res) => {
  try {
    await runInsightCheck(io);
    res.json({ message: 'Insight check completed' });
  } catch (error) {
    console.error('Failed to run insight check:', error);
    res.status(500).json({ error: 'Failed to run insight check' });
  }
});

// WebSocket
io.on('connection', (socket) => {
  console.log('Client connected:', socket.id);

  socket.on('subscribe', (topics: string[]) => {
    topics.forEach(t => socket.join(`topic:${t}`));
    console.log(`Socket ${socket.id} subscribed to:`, topics);
  });

  socket.on('unsubscribe', (topics: string[]) => {
    topics.forEach(t => socket.leave(`topic:${t}`));
  });

  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
  });
});

// 定时分析：默认每 30 分钟一轮，可用 CRON_SCHEDULE 覆盖
const CRON_SCHEDULE = process.env.CRON_SCHEDULE || '*/30 * * * *';

cron.schedule(CRON_SCHEDULE, async () => {
  console.log('🔄 Running scheduled insight check...');
  try {
    await runInsightCheck(io);
    console.log('✅ Scheduled insight check completed');
  } catch (error) {
    console.error('❌ Scheduled insight check failed:', error);
  }
});

export { io };

const PORT = process.env.PORT || 3001;

httpServer.listen(PORT, () => {
  console.log(`
  📊 保险客户声音智能分析系统 - 服务已启动
  🌐 Server running on http://localhost:${PORT}
  🔌 WebSocket ready
  ⏰ Insight check scheduled: ${CRON_SCHEDULE}
  🤖 AI model: ${process.env.AI_MODEL || 'deepseek/deepseek-v3.2'}${process.env.OPENROUTER_API_KEY ? '' : '  ⚠️  未配置 API Key，将使用规则兜底'}
  `);
});

process.on('SIGINT', async () => {
  console.log('Shutting down...');
  await prisma.$disconnect();
  process.exit(0);
});
