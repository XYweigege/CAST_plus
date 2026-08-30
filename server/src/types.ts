// ============ 保险客户声音（VoC）领域类型定义 ============

/** 反馈来源渠道 */
export type FeedbackSource =
  | 'survey' // 客户调查问卷（NPS / CSAT）
  | 'claim' // 索赔反馈
  | 'service' // 客服工单
  | 'social' // 社媒公开内容
  | 'appstore' // 应用商店评论
  | 'email'; // 客户邮件

/** 情感倾向 */
export type Sentiment = 'positive' | 'neutral' | 'negative';

/** 紧急度：决定预警与推送策略 */
export type Urgency = 'info' | 'attention' | 'action' | 'critical';

/** 采集到的原始反馈条目 */
export interface FeedbackItem {
  title?: string;
  content: string;
  source: FeedbackSource;
  sourceId?: string;
  url?: string;
  rating?: number; // 1-5
  productLine?: string;
  language?: string;
  authorName?: string;
  publishedAt?: Date;
}

/** AI 结构化分析结果 */
export interface FeedbackAnalysis {
  sentiment: Sentiment;
  topics: string[];
  urgency: Urgency;
  urgencyReason: string;
  aiSummary: string;
  confidence: number; // 0-1
}

/** 带主题关联的完整反馈（返回给前端） */
export interface FeedbackWithTopic {
  id: string;
  title: string | null;
  content: string;
  source: string;
  sourceId: string | null;
  url: string | null;
  rating: number | null;
  productLine: string | null;
  language: string | null;
  authorName: string | null;
  publishedAt: Date | null;
  createdAt: Date;
  sentiment: string;
  topics: string | null;
  urgency: string;
  urgencyReason: string | null;
  aiSummary: string | null;
  confidence: number | null;
  humanLabel: string | null;
  isReviewed: boolean;
  topicId: string | null;
  topic: {
    id: string;
    text: string;
    category: string | null;
  } | null;
}
