import { Router } from 'express';
import { prisma } from '../db.js';
import { sortFeedbacks } from '../utils/sortFeedbacks.js';
import { analyzeFeedback, generateInsightReport } from '../services/ai.js';
import { generateDemoFeedback, parseFeedbackFile } from '../services/feedbackSource.js';

const router = Router();

// ============ 列表查询 ============
router.get('/', async (req, res) => {
  try {
    const {
      page = '1',
      limit = '20',
      source,
      sentiment,
      urgency,
      productLine,
      topicId,
      topic,
      keyword,
      pendingReview,
      timeRange,
      timeFrom,
      timeTo,
      sortBy = 'createdAt',
      sortOrder = 'desc'
    } = req.query;

    const pageNum = parseInt(page as string);
    const limitNum = parseInt(limit as string);
    const skip = (pageNum - 1) * limitNum;

    const where: any = {};
    if (source) where.source = source;
    if (sentiment) where.sentiment = sentiment;
    if (urgency) where.urgency = urgency;
    if (productLine) where.productLine = productLine;
    if (topicId) where.topicId = topicId;
    // topics 以 JSON 数组字符串存储，主题筛选退化为包含匹配
    if (topic) where.topics = { contains: String(topic) };
    if (keyword) {
      where.OR = [
        { content: { contains: String(keyword) } },
        { aiSummary: { contains: String(keyword) } }
      ];
    }
    if (pendingReview === 'true') where.isReviewed = false;

    if (timeRange) {
      const now = new Date();
      let dateFrom: Date | null = null;
      switch (timeRange) {
        case '24h':
          dateFrom = new Date(now.getTime() - 24 * 60 * 60 * 1000);
          break;
        case 'today':
          dateFrom = new Date(now);
          dateFrom.setHours(0, 0, 0, 0);
          break;
        case '7d':
          dateFrom = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
          break;
        case '30d':
          dateFrom = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
          break;
      }
      if (dateFrom) where.createdAt = { gte: dateFrom };
    } else if (timeFrom || timeTo) {
      where.createdAt = {};
      if (timeFrom) where.createdAt.gte = new Date(timeFrom as string);
      if (timeTo) where.createdAt.lte = new Date(timeTo as string);
    }

    // urgency 需要按业务语义排序（critical 在前），Prisma 无法直接表达，走内存排序
    const needsMemorySort = sortBy === 'urgency';
    let orderBy: any = { createdAt: sortOrder === 'asc' ? 'asc' : 'desc' };
    if (!needsMemorySort) {
      if (sortBy === 'rating') orderBy = { rating: sortOrder === 'asc' ? 'asc' : 'desc' };
      else if (sortBy === 'confidence') orderBy = { confidence: sortOrder === 'asc' ? 'asc' : 'desc' };
      else if (sortBy === 'publishedAt') orderBy = { publishedAt: sortOrder === 'asc' ? 'asc' : 'desc' };
    }

    const [rows, total] = await Promise.all([
      prisma.feedback.findMany({
        where,
        orderBy,
        ...(needsMemorySort ? {} : { skip, take: limitNum }),
        include: { topic: { select: { id: true, text: true, category: true } } }
      }),
      prisma.feedback.count({ where })
    ]);

    let data: any[];
    if (needsMemorySort) {
      const sorted = sortFeedbacks(rows, 'urgency', sortOrder === 'asc' ? 'asc' : 'desc');
      data = sorted.slice(skip, skip + limitNum);
    } else {
      data = rows;
    }

    res.json({
      data,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        totalPages: Math.ceil(total / limitNum)
      }
    });
  } catch (error) {
    console.error('Error fetching feedbacks:', error);
    res.status(500).json({ error: 'Failed to fetch feedbacks' });
  }
});

// ============ 概览统计 ============
router.get('/stats', async (_req, res) => {
  try {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const [
      total,
      todayCount,
      negative,
      pendingAlert,
      pendingReview,
      ratedAgg,
      bySentiment,
      bySource,
      byProduct
    ] = await Promise.all([
      prisma.feedback.count(),
      prisma.feedback.count({ where: { createdAt: { gte: today } } }),
      prisma.feedback.count({ where: { sentiment: 'negative' } }),
      prisma.alert.count({ where: { handled: false } }),
      prisma.feedback.count({ where: { isReviewed: false } }),
      prisma.feedback.aggregate({ _avg: { rating: true } }),
      prisma.feedback.groupBy({ by: ['sentiment'], _count: { sentiment: true } }),
      prisma.feedback.groupBy({ by: ['source'], _count: { source: true } }),
      prisma.feedback.groupBy({ by: ['productLine'], _count: { productLine: true } })
    ]);

    res.json({
      total,
      today: todayCount,
      negative,
      negativeRatio: total ? Number((negative / total).toFixed(3)) : 0,
      pendingAlert,
      pendingReview,
      avgRating: ratedAgg._avg.rating ? Number(ratedAgg._avg.rating.toFixed(2)) : null,
      bySentiment: toRecord(bySentiment, 'sentiment'),
      bySource: toRecord(bySource, 'source'),
      byProduct: toRecord(byProduct, 'productLine')
    });
  } catch (error) {
    console.error('Error fetching stats:', error);
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

function toRecord(rows: any[], key: string): Record<string, number> {
  return rows.reduce((acc: Record<string, number>, item: any) => {
    const k = item[key] ?? 'unknown';
    acc[k] = item._count[key];
    return acc;
  }, {} as Record<string, number>);
}

// ============ 评分归因报告 ============
router.get('/insight', async (req, res) => {
  try {
    const { productLine } = req.query;

    const where: any = {};
    if (productLine) where.productLine = String(productLine);

    const rows = await prisma.feedback.findMany({
      where,
      select: { content: true, rating: true, sentiment: true, topics: true, aiSummary: true },
      orderBy: { createdAt: 'desc' },
      take: 300
    });

    const report = await generateInsightReport(String(productLine ?? '全部产品线'), rows);
    res.json(report);
  } catch (error) {
    console.error('Error generating insight:', error);
    res.status(500).json({ error: 'Failed to generate insight' });
  }
});

// ============ 单条详情 ============
router.get('/:id', async (req, res) => {
  try {
    const feedback = await prisma.feedback.findUnique({
      where: { id: req.params.id },
      include: { topic: true }
    });

    if (!feedback) {
      return res.status(404).json({ error: 'Feedback not found' });
    }

    res.json(feedback);
  } catch (error) {
    console.error('Error fetching feedback:', error);
    res.status(500).json({ error: 'Failed to fetch feedback' });
  }
});

// ============ 单条文本即时分析（不落库，用于验证 Prompt 效果）============
router.post('/analyze', async (req, res) => {
  try {
    const { content, productLine, source, rating, language } = req.body;

    if (!content || typeof content !== 'string') {
      return res.status(400).json({ error: 'content is required' });
    }

    const analysis = await analyzeFeedback({
      content,
      source: source ?? 'survey',
      productLine,
      rating: rating != null ? Number(rating) : undefined,
      language
    });

    res.json(analysis);
  } catch (error) {
    console.error('Error analyzing feedback:', error);
    res.status(500).json({ error: 'Failed to analyze feedback' });
  }
});

// ============ 导入外部反馈（CSV / JSON）============
router.post('/import', async (req, res) => {
  try {
    const { content, format = 'json' } = req.body;

    if (!content || typeof content !== 'string') {
      return res.status(400).json({ error: 'content is required' });
    }

    const items = parseFeedbackFile(content, format === 'csv' ? 'csv' : 'json');
    if (items.length === 0) {
      return res.status(400).json({ error: 'No valid feedback parsed' });
    }

    const analyses = await Promise.all(items.map(item => analyzeFeedback(item)));

    let created = 0;
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const analysis = analyses[i];
      try {
        await prisma.feedback.create({
          data: {
            title: item.title ?? null,
            content: item.content,
            source: item.source,
            sourceId: item.sourceId ?? null,
            url: item.url ?? null,
            rating: item.rating ?? null,
            productLine: item.productLine ?? null,
            language: item.language ?? null,
            authorName: item.authorName ?? null,
            publishedAt: item.publishedAt ?? null,
            sentiment: analysis.sentiment,
            topics: JSON.stringify(analysis.topics),
            urgency: analysis.urgency,
            urgencyReason: analysis.urgencyReason,
            aiSummary: analysis.aiSummary,
            confidence: analysis.confidence,
            isReviewed: analysis.confidence >= 0.7
          }
        });
        created += 1;
      } catch {
        // sourceId 重复，跳过
      }
    }

    res.json({ total: items.length, created });
  } catch (error) {
    console.error('Error importing feedback:', error);
    res.status(500).json({ error: 'Failed to import feedback' });
  }
});

// ============ 生成演示数据 ============
router.post('/generate-demo', async (req, res) => {
  try {
    const count = Math.min(Number(req.body?.count) || 40, 200);
    const items = generateDemoFeedback(count);
    const analyses = await Promise.all(items.map(item => analyzeFeedback(item)));

    let created = 0;
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const analysis = analyses[i];
      try {
        await prisma.feedback.create({
          data: {
            title: item.title ?? null,
            content: item.content,
            source: item.source,
            sourceId: item.sourceId ?? null,
            rating: item.rating ?? null,
            productLine: item.productLine ?? null,
            language: item.language ?? null,
            authorName: item.authorName ?? null,
            publishedAt: item.publishedAt ?? null,
            sentiment: analysis.sentiment,
            topics: JSON.stringify(analysis.topics),
            urgency: analysis.urgency,
            urgencyReason: analysis.urgencyReason,
            aiSummary: analysis.aiSummary,
            confidence: analysis.confidence,
            isReviewed: analysis.confidence >= 0.7
          }
        });
        created += 1;
      } catch {
        // 忽略重复
      }
    }

    res.json({ created });
  } catch (error) {
    console.error('Error generating demo data:', error);
    res.status(500).json({ error: 'Failed to generate demo data' });
  }
});

// ============ 人工复核（人机协同闭环）============
router.patch('/:id/review', async (req, res) => {
  try {
    const { sentiment, topics, urgency } = req.body;

    const feedback = await prisma.feedback.update({
      where: { id: req.params.id },
      data: {
        humanLabel: JSON.stringify({ sentiment, topics, urgency }),
        ...(sentiment && { sentiment }),
        ...(topics && { topics: JSON.stringify(topics) }),
        ...(urgency && { urgency }),
        isReviewed: true,
        confidence: 1 // 人工确认即为终态
      }
    });

    res.json(feedback);
  } catch (error: any) {
    if (error.code === 'P2025') {
      return res.status(404).json({ error: 'Feedback not found' });
    }
    console.error('Error reviewing feedback:', error);
    res.status(500).json({ error: 'Failed to review feedback' });
  }
});

// ============ 删除 ============
router.delete('/:id', async (req, res) => {
  try {
    await prisma.feedback.delete({ where: { id: req.params.id } });
    res.status(204).send();
  } catch (error: any) {
    if (error.code === 'P2025') {
      return res.status(404).json({ error: 'Feedback not found' });
    }
    console.error('Error deleting feedback:', error);
    res.status(500).json({ error: 'Failed to delete feedback' });
  }
});

export default router;
