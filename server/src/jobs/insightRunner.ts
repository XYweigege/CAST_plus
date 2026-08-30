import { Server } from 'socket.io';
import { prisma } from '../db.js';
import { generateDemoFeedback } from '../services/feedbackSource.js';
import { analyzeFeedback, expandTopic, preMatchTopic } from '../services/ai.js';
import { sendAlertEmail } from '../services/email.js';
import { ALERT_URGENCIES, CONFIDENCE_THRESHOLD, TOPIC_TAGS } from '../constants.js';
import type { FeedbackItem } from '../types.js';

/** 单次采集的新增反馈数量（模拟业务系统按批次推送） */
const BATCH_SIZE = 12;

/** 突增阈值：某主题 24 小时内负面反馈达到该数量触发预警 */
const SURGE_THRESHOLD = 5;

/** 突增预警的静默期（小时），避免重复告警 */
const SURGE_COOLDOWN_HOURS = 12;

let batchSeq = 0;

/**
 * 采集一批新增反馈。
 * 真实落地时替换为业务系统接口 / 文件导出轮询：
 *   survey → 问卷系统 API
 *   claim  → 索赔系统工单表
 *   service→ 客服工单系统
 *   social → 社媒公开内容抓取
 */
async function fetchNewFeedback(): Promise<FeedbackItem[]> {
  batchSeq += 1;
  const items = generateDemoFeedback(BATCH_SIZE);
  // sourceId 必须全局唯一，否则会被去重逻辑丢弃
  return items.map(item => ({ ...item, sourceId: `b${batchSeq}-${item.sourceId}` }));
}

export async function runInsightCheck(io: Server): Promise<void> {
  console.log('🔍 Starting feedback insight check...');

  const topics = await prisma.topic.findMany({ where: { isActive: true } });

  if (topics.length === 0) {
    console.log('No active topics, skip check');
    return;
  }

  const rawItems = await fetchNewFeedback();
  console.log(`Fetched ${rawItems.length} new feedback items`);

  let newCount = 0;
  let alertCount = 0;

  for (const topic of topics) {
    console.log(`\n📎 Checking topic: "${topic.text}"`);

    try {
      // 1) 主题词扩展：把书面业务术语翻译成客户口语表达
      const expanded = await expandTopic(topic.text);

      let topicHit = 0;

      for (const item of rawItems) {
        try {
          // 2) 去重：同一来源的同一条记录只处理一次
          const existing = await prisma.feedback.findFirst({
            where: { source: item.source, sourceId: item.sourceId ?? null }
          });
          if (existing) continue;

          // 3) 预匹配：未命中扩展词的反馈直接跳过，节省 AI 调用
          const preMatch = preMatchTopic(item.content, expanded);

          // 4) AI 结构化分析
          const analysis = await analyzeFeedback(item, preMatch);

          // 5) 归属：命中扩展词或 AI 判定的主题与该主题词一致时，归入该主题
          const topicMatched =
            preMatch.matched ||
            analysis.topics.some(t => t === topic.text || topic.text.includes(t) || t.includes(topic.text));

          if (!topicMatched) continue;

          topicHit += 1;

          const feedback = await prisma.feedback.create({
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
              // 低置信度判定不写入终态，等待人工复核
              isReviewed: analysis.confidence >= CONFIDENCE_THRESHOLD,
              topicId: topic.id
            },
            include: { topic: { select: { id: true, text: true, category: true } } }
          });

          newCount += 1;
          console.log(`  ✅ [${analysis.urgency}] ${item.content.slice(0, 30)}...`);

          // 6) 更新主题命中次数（驱动关键词调优闭环）
          await prisma.topic.update({
            where: { id: topic.id },
            data: { hitCount: { increment: 1 } }
          });

          // 7) 实时推送到前端
          io.emit('feedback:new', feedback);

          // 8) 预警：达到处理级别才告警，避免告警疲劳
          if (ALERT_URGENCIES.includes(analysis.urgency)) {
            const alert = await prisma.alert.create({
              data: {
                type: 'negative',
                title: `${analysis.urgency === 'critical' ? '紧急' : '待处理'}：${analysis.topics[0] ?? '客户反馈'}`,
                content: `${feedback.aiSummary ?? item.content.slice(0, 80)}（产品线：${item.productLine ?? '未知'}）`,
                urgency: analysis.urgency,
                feedbackId: feedback.id
              }
            });

            alertCount += 1;
            io.emit('alert', {
              id: alert.id,
              title: alert.title,
              content: alert.content,
              urgency: alert.urgency,
              feedbackId: feedback.id
            });

            await sendAlertEmail({
              title: alert.title,
              content: alert.content,
              urgency: alert.urgency,
              topics: analysis.topics,
              productLine: item.productLine ?? null,
              rating: item.rating ?? null
            });
          }
        } catch (error) {
          console.error('  Error processing feedback:', error);
        }
      }

      console.log(`  Topic "${topic.text}" matched ${topicHit} items (hitCount now ${topic.hitCount + topicHit})`);
    } catch (error) {
      console.error(`Error checking topic "${topic.text}":`, error);
    }
  }

  // 9) 主题突增检测：某主题负面反馈在 24 小时内集中出现，往往是系统性问题的前兆
  await detectSurge(io);

  console.log(`\n✨ Insight check completed. ${newCount} new feedback, ${alertCount} alerts.`);
}

/**
 * 突增检测：统计 24 小时内各主题的负面反馈，
 * 超过阈值且不在静默期内则生成 surge 预警。
 */
async function detectSurge(io: Server): Promise<void> {
  const since = new Date(Date.now() - 24 * 3600 * 1000);

  const negatives = await prisma.feedback.findMany({
    where: { sentiment: 'negative', createdAt: { gte: since } },
    select: { topics: true }
  });

  const counter = new Map<string, number>();
  for (const row of negatives) {
    try {
      const list: string[] = row.topics ? JSON.parse(row.topics) : [];
      for (const t of list) counter.set(t, (counter.get(t) ?? 0) + 1);
    } catch {
      // 忽略解析失败的记录
    }
  }

  for (const [topicText, count] of counter.entries()) {
    if (count < SURGE_THRESHOLD) continue;
    if (!TOPIC_TAGS.includes(topicText as never)) continue;

    // 静默期检查
    const cooldownFrom = new Date(Date.now() - SURGE_COOLDOWN_HOURS * 3600 * 1000);
    const recent = await prisma.alert.findFirst({
      where: { type: 'surge', title: { contains: topicText }, createdAt: { gte: cooldownFrom } }
    });
    if (recent) continue;

    const alert = await prisma.alert.create({
      data: {
        type: 'surge',
        title: `主题突增：${topicText}`,
        content: `过去 24 小时内「${topicText}」相关负面反馈达 ${count} 条，超出常规水平，建议排查是否为系统性问题。`,
        urgency: 'action'
      }
    });

    io.emit('alert', {
      id: alert.id,
      title: alert.title,
      content: alert.content,
      urgency: alert.urgency
    });

    console.log(`  📈 Surge alert: ${topicText} (${count} negatives in 24h)`);
  }
}
