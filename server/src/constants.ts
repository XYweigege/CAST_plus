// ============ 保险 VoC 业务字典 ============
// 主题体系是整个 AI 分析层的骨架：Prompt 约束、前端筛选、归因报告共用同一套枚举

/** 客户反馈主题标签（AI 只能从这些里面选，避免标签爆炸） */
export const TOPIC_TAGS = [
  '理赔时效',
  '赔付金额争议',
  '拒赔争议',
  '核保与投保',
  '客服响应',
  '服务态度',
  '条款清晰度',
  '销售误导',
  '续保与退保',
  'APP与网站体验',
  '价格与性价比',
  '理赔资料繁琐',
] as const;

/** 产品线 */
export const PRODUCT_LINES = [
  { value: 'travel', label: '旅行保险' },
  { value: 'medical', label: '医疗保险' },
  { value: 'accident', label: '个人意外险' },
  { value: 'home', label: '家居保险' },
  { value: 'motor', label: '汽车保险' },
  { value: 'pet', label: '宠物保险' },
] as const;

/** 反馈渠道 */
export const SOURCES = [
  { value: 'survey', label: '客户问卷' },
  { value: 'claim', label: '索赔反馈' },
  { value: 'service', label: '客服工单' },
  { value: 'social', label: '社媒公开' },
  { value: 'appstore', label: '应用商店' },
  { value: 'email', label: '客户邮件' },
] as const;

/** 语言 */
export const LANGUAGES = [
  { value: 'zh-HK', label: '繁体中文' },
  { value: 'en', label: 'English' },
  { value: 'mixed', label: '中英混排' },
] as const;

/** 情感倾向 */
export const SENTIMENTS = [
  { value: 'positive', label: '正面' },
  { value: 'neutral', label: '中性' },
  { value: 'negative', label: '负面' },
] as const;

/** 紧急度：数值越小越紧急，用于排序 */
export const URGENCY_ORDER: Record<string, number> = {
  critical: 0,
  action: 1,
  attention: 2,
  info: 3,
};

export const URGENCIES = [
  { value: 'critical', label: '紧急' },
  { value: 'action', label: '需处理' },
  { value: 'attention', label: '需关注' },
  { value: 'info', label: '一般' },
] as const;

/** 置信度低于该阈值的 AI 判定转人工复核 */
export const CONFIDENCE_THRESHOLD = 0.7;

/** 触发预警的紧急度 */
export const ALERT_URGENCIES = ['action', 'critical'];
