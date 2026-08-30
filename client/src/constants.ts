// 保险 VoC 业务字典（与 server/src/constants.ts 保持一致）

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
  '理赔资料繁琐'
];

export const PRODUCT_LINES = [
  { value: 'travel', label: '旅行保险' },
  { value: 'medical', label: '医疗保险' },
  { value: 'accident', label: '个人意外险' },
  { value: 'home', label: '家居保险' },
  { value: 'motor', label: '汽车保险' },
  { value: 'pet', label: '宠物保险' }
];

export const SOURCES = [
  { value: 'survey', label: '客户问卷' },
  { value: 'claim', label: '索赔反馈' },
  { value: 'service', label: '客服工单' },
  { value: 'social', label: '社媒公开' },
  { value: 'appstore', label: '应用商店' },
  { value: 'email', label: '客户邮件' }
];

export const SENTIMENTS = [
  { value: 'positive', label: '正面' },
  { value: 'neutral', label: '中性' },
  { value: 'negative', label: '负面' }
];

export const URGENCIES = [
  { value: 'critical', label: '紧急' },
  { value: 'action', label: '需处理' },
  { value: 'attention', label: '需关注' },
  { value: 'info', label: '一般' }
];

/** 紧急度展示样式（浅色主题：浅底 + 深色字 + 彩色左边框） */
export const URGENCY_STYLE: Record<string, { label: string; badge: string; dot: string; bar: string }> = {
  critical: {
    label: '紧急',
    badge: 'bg-red-50 text-red-700 border-red-200',
    dot: 'bg-red-500',
    bar: 'bg-red-500'
  },
  action: {
    label: '需处理',
    badge: 'bg-orange-50 text-orange-700 border-orange-200',
    dot: 'bg-orange-500',
    bar: 'bg-orange-500'
  },
  attention: {
    label: '需关注',
    badge: 'bg-amber-50 text-amber-700 border-amber-200',
    dot: 'bg-amber-500',
    bar: 'bg-amber-500'
  },
  info: {
    label: '一般',
    badge: 'bg-slate-50 text-slate-600 border-slate-200',
    dot: 'bg-slate-400',
    bar: 'bg-slate-300'
  }
};

/** 情感展示样式 */
export const SENTIMENT_STYLE: Record<string, { label: string; badge: string }> = {
  positive: { label: '正面', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  neutral: { label: '中性', badge: 'bg-slate-50 text-slate-600 border-slate-200' },
  negative: { label: '负面', badge: 'bg-rose-50 text-rose-700 border-rose-200' }
};

export function productLabel(value: string | null): string {
  if (!value) return '未分类';
  return PRODUCT_LINES.find(p => p.value === value)?.label ?? value;
}

export function sourceLabel(value: string): string {
  return SOURCES.find(s => s.value === value)?.label ?? value;
}

export function urgencyLabel(value: string): string {
  return URGENCIES.find(u => u.value === value)?.label ?? value;
}

export function sentimentLabel(value: string): string {
  return SENTIMENTS.find(s => s.value === value)?.label ?? value;
}
