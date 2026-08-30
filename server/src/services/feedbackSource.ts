import type { FeedbackItem, FeedbackSource, Sentiment } from '../types.js';

// ============ 反馈数据源 ============
// 真实场景中，反馈来自问卷系统、索赔系统、客服工单系统的接口或文件导出。
// 本项目无法接入真实业务库，因此内置一套贴近香港保险客户表达习惯的语料，
// 用于生成演示数据与 AI 标注效果评测集。

interface CorpusEntry {
  text: string;
  topic: string;
  sentiment: Sentiment;
  productLine?: string;
}

const CORPUS: CorpusEntry[] = [
  // ===== 理赔时效 =====
  { text: '理賠拖咗三個星期都未批，打電話又無人聽，好失望', topic: '理赔时效', sentiment: 'negative', productLine: 'medical' },
  { text: 'Claim submitted 3 weeks ago, still no update. Very poor service.', topic: '理赔时效', sentiment: 'negative', productLine: 'travel' },
  { text: '單據交咗大半個月，系統仲顯示審核中，幾時先有結果？', topic: '理赔时效', sentiment: 'negative', productLine: 'medical' },
  { text: '理賠流程太慢，急住用錢都唔知等到幾時', topic: '理赔时效', sentiment: 'negative', productLine: 'accident' },
  { text: '理賠好快，三日就收到錢，效率一流', topic: '理赔时效', sentiment: 'positive', productLine: 'travel' },
  { text: 'Claim was settled within 3 days. Excellent service.', topic: '理赔时效', sentiment: 'positive', productLine: 'travel' },

  // ===== 赔付金额争议 =====
  { text: '賠得咁少，醫療費兩萬只賠三千，根本唔合理', topic: '赔付金额争议', sentiment: 'negative', productLine: 'medical' },
  { text: 'Settlement amount is way lower than expected and the deduction was never explained.', topic: '赔付金额争议', sentiment: 'negative', productLine: 'medical' },
  { text: '點解賠償金額同我預期差咁遠？完全無解釋', topic: '赔付金额争议', sentiment: 'negative', productLine: 'home' },

  // ===== 拒赔争议 =====
  { text: '明明買咗全保，最後話唔賠，搵笨！', topic: '拒赔争议', sentiment: 'negative', productLine: 'travel' },
  { text: 'Claim rejected without any clear explanation. I will escalate this to the Insurance Authority.', topic: '拒赔争议', sentiment: 'negative', productLine: 'medical' },
  { text: '拒賠理由牽強，我準備去保險投訴局投訴', topic: '拒赔争议', sentiment: 'negative', productLine: 'accident' },
  { text: '話我未如實申報，但投保時根本無人問過我呢個問題', topic: '拒赔争议', sentiment: 'negative', productLine: 'medical' },

  // ===== 核保与投保 =====
  { text: '投保時話乜都保，核保時問長問短，最後仲要加價', topic: '核保与投保', sentiment: 'negative', productLine: 'medical' },
  { text: 'Underwriting took forever and they kept asking for more documents.', topic: '核保与投保', sentiment: 'negative', productLine: 'medical' },
  { text: '網上投保流程好麻煩，填極都錯，搞咁耐', topic: '核保与投保', sentiment: 'negative', productLine: 'travel' },
  { text: '投保流程好順暢，幾分鐘就搞掂', topic: '核保与投保', sentiment: 'positive', productLine: 'travel' },

  // ===== 客服响应 =====
  { text: '打咗五次客服都無人接，等到火滾', topic: '客服响应', sentiment: 'negative', productLine: 'motor' },
  { text: 'Customer service never replies to my emails.', topic: '客服响应', sentiment: 'negative', productLine: 'travel' },
  { text: 'WhatsApp 客服已讀不回，服務態度極差', topic: '客服响应', sentiment: 'negative', productLine: 'home' },
  { text: '客服跟進到位，主動打電話 update 進度，值得一讚', topic: '客服响应', sentiment: 'positive', productLine: 'medical' },

  // ===== 服务态度 =====
  { text: '客服阿 May 解釋得好清楚，好有耐性，讚', topic: '服务态度', sentiment: 'positive', productLine: 'travel' },
  { text: 'The agent was very patient and explained everything clearly.', topic: '服务态度', sentiment: 'positive', productLine: 'medical' },
  { text: '職員態度冷淡，問多兩句就唔耐煩', topic: '服务态度', sentiment: 'negative', productLine: 'motor' },
  { text: 'The staff was rude and impatient when I asked about the exclusions.', topic: '服务态度', sentiment: 'negative', productLine: 'home' },

  // ===== 条款清晰度 =====
  { text: '條款寫到好含糊，完全睇唔明咩情況先賠', topic: '条款清晰度', sentiment: 'negative', productLine: 'travel' },
  { text: 'Terms and conditions are too vague. No one can understand what is actually covered.', topic: '条款清晰度', sentiment: 'negative', productLine: 'pet' },
  { text: '保單文字太多，重點唔突出，長者根本睇唔明', topic: '条款清晰度', sentiment: 'negative', productLine: 'medical' },

  // ===== 销售误导 =====
  { text: '當初 agent 話全保，原來一堆除外責任，講一套做一套', topic: '销售误导', sentiment: 'negative', productLine: 'medical' },
  { text: 'The agent promised full coverage but there are many exclusions. This is misleading.', topic: '销售误导', sentiment: 'negative', productLine: 'travel' },
  { text: 'sales 話呢份係儲蓄計劃，原來係保險，感覺被誤導，要求退保', topic: '销售误导', sentiment: 'negative', productLine: 'accident' },

  // ===== 续保与退保 =====
  { text: '續保保費加咗三成，事前完全無通知', topic: '续保与退保', sentiment: 'negative', productLine: 'motor' },
  { text: 'Premium increased 30% at renewal without any prior notice.', topic: '续保与退保', sentiment: 'negative', productLine: 'medical' },
  { text: '想退保，手續繁複，仲要扣一大筆錢', topic: '续保与退保', sentiment: 'negative', productLine: 'accident' },

  // ===== APP 与网站体验 =====
  { text: '個 app 好難用，upload document 次次 fail', topic: 'APP与网站体验', sentiment: 'negative', productLine: 'travel' },
  { text: 'The app keeps crashing when I upload documents. Very frustrating.', topic: 'APP与网站体验', sentiment: 'negative', productLine: 'medical' },
  { text: '網站表格填到一半就 timeout，要重新填過，浪費時間', topic: 'APP与网站体验', sentiment: 'negative', productLine: 'home' },
  { text: '新版 app 界面清晰，索償進度一目了然', topic: 'APP与网站体验', sentiment: 'positive', productLine: 'travel' },

  // ===== 价格与性价比 =====
  { text: '保費年年加，保障年年減，性價比低', topic: '价格与性价比', sentiment: 'negative', productLine: 'medical' },
  { text: 'Price is reasonable for the coverage provided, quite satisfied.', topic: '价格与性价比', sentiment: 'positive', productLine: 'travel' },
  { text: '同樣保障其他公司平一半，考慮轉會', topic: '价格与性价比', sentiment: 'negative', productLine: 'motor' },

  // ===== 理赔资料繁琐 =====
  { text: '索償要交十幾份文件，缺一份又打回頭，搞咁耐', topic: '理赔资料繁琐', sentiment: 'negative', productLine: 'medical' },
  { text: 'Too many documents required for such a simple claim.', topic: '理赔资料繁琐', sentiment: 'negative', productLine: 'travel' },
  { text: 'claim 一次要影印一堆單據，好麻煩', topic: '理赔资料繁琐', sentiment: 'negative', productLine: 'accident' },

  // ===== 中性咨询 =====
  { text: '想查詢一下索償需要準備什麼文件', topic: '理赔资料繁琐', sentiment: 'neutral', productLine: 'medical' },
  { text: 'May I know what documents are needed for a travel insurance claim?', topic: '理赔资料繁琐', sentiment: 'neutral', productLine: 'travel' },
  { text: '請問續保手續如何辦理？', topic: '续保与退保', sentiment: 'neutral', productLine: 'motor' },
  { text: '保單已收到，謝謝', topic: '核保与投保', sentiment: 'neutral', productLine: 'travel' },
  { text: '整體滿意，會推薦俾朋友', topic: '服务态度', sentiment: 'positive', productLine: 'pet' },
];

const SOURCE_POOL: FeedbackSource[] = ['survey', 'claim', 'service', 'social', 'appstore', 'email'];

const AUTHOR_POOL = ['陳小姐', '李先生', 'Wong Tai Man', '張太', 'K. Chan', '匿名客戶', 'Ho Siu Ming', '劉先生'];

const PRODUCT_POOL = ['travel', 'medical', 'accident', 'home', 'motor', 'pet'];

/** 简易语言识别：判断是否含中文字符 */
function detectLanguage(text: string): string {
  const hasCJK = /[\u4e00-\u9fff]/.test(text);
  const hasLatin = /[a-zA-Z]{3,}/.test(text);
  if (hasCJK && hasLatin) return 'mixed';
  if (hasCJK) return 'zh-HK';
  return 'en';
}

/** 按情感生成合理评分：负面 1-2，中性 3，正面 4-5 */
function ratingBySentiment(sentiment: Sentiment): number {
  if (sentiment === 'negative') return 1 + Math.floor(Math.random() * 2);
  if (sentiment === 'positive') return 4 + Math.floor(Math.random() * 2);
  return 3;
}

/**
 * 生成演示用反馈数据。
 * 语料本身带有人工标注（topic / sentiment），可直接作为 AI 标注效果的评测集。
 */
export function generateDemoFeedback(count = 60): FeedbackItem[] {
  const items: FeedbackItem[] = [];
  const ts = Date.now();

  for (let i = 0; i < count; i++) {
    const entry = CORPUS[Math.floor(Math.random() * CORPUS.length)];
    const source = SOURCE_POOL[Math.floor(Math.random() * SOURCE_POOL.length)];
    // 80% 概率用语料自带的产品线，20% 随机打散，更接近真实分布
    const productLine = Math.random() < 0.8 && entry.productLine
      ? entry.productLine
      : PRODUCT_POOL[Math.floor(Math.random() * PRODUCT_POOL.length)];

    // 发布时间：最近 30 天内随机
    const daysAgo = Math.floor(Math.random() * 30);
    const publishedAt = new Date(Date.now() - daysAgo * 24 * 3600 * 1000);

    items.push({
      title: entry.text.length > 24 ? entry.text.slice(0, 24) + '…' : entry.text,
      content: entry.text,
      source,
      sourceId: `demo-${ts}-${i}`,
      rating: ratingBySentiment(entry.sentiment),
      productLine,
      language: detectLanguage(entry.text),
      authorName: Math.random() < 0.7 ? AUTHOR_POOL[Math.floor(Math.random() * AUTHOR_POOL.length)] : undefined,
      publishedAt
    });
  }

  return items;
}

/**
 * 从外部文件导入反馈（CSV / JSON）。
 * 真实落地时对接业务系统导出的问卷与工单数据。
 */
export function parseFeedbackFile(raw: string, format: 'json' | 'csv'): FeedbackItem[] {
  if (format === 'json') {
    const parsed: unknown = JSON.parse(raw);
    const list: Record<string, unknown>[] = Array.isArray(parsed)
      ? (parsed as Record<string, unknown>[])
      : ((parsed as { data?: Record<string, unknown>[] })?.data ?? []);
    return list.map((row, i) => ({
      title: row.title ? String(row.title) : undefined,
      content: String(row.content ?? row.text ?? ''),
      source: (row.source as FeedbackSource) || 'survey',
      sourceId: row.sourceId ? String(row.sourceId) : `import-${Date.now()}-${i}`,
      rating: row.rating != null ? Number(row.rating) : undefined,
      productLine: row.productLine ? String(row.productLine) : undefined,
      language: row.language ? String(row.language) : detectLanguage(String(row.content ?? row.text ?? '')),
      authorName: row.authorName ? String(row.authorName) : undefined,
      publishedAt: row.publishedAt ? new Date(String(row.publishedAt)) : undefined
    })).filter(item => item.content.length > 0);
  }

  // CSV 解析：首行为表头，需包含 content 列
  const lines = raw.split(/\r?\n/).filter(l => l.trim().length > 0);
  if (lines.length < 2) return [];

  const headers = lines[0].split(',').map(h => h.trim());
  const contentIdx = headers.indexOf('content') >= 0 ? headers.indexOf('content') : headers.indexOf('text');
  if (contentIdx < 0) return [];

  return lines.slice(1).map((line, i) => {
    const cols = line.split(',');
    const content = (cols[contentIdx] ?? '').trim();
    const pick = (name: string) => {
      const idx = headers.indexOf(name);
      return idx >= 0 ? (cols[idx] ?? '').trim() : '';
    };
    return {
      title: pick('title') || undefined,
      content,
      source: (pick('source') as FeedbackSource) || 'survey',
      sourceId: pick('sourceId') || `import-${Date.now()}-${i}`,
      rating: pick('rating') ? Number(pick('rating')) : undefined,
      productLine: pick('productLine') || undefined,
      language: pick('language') || detectLanguage(content),
      authorName: pick('authorName') || undefined,
      publishedAt: pick('publishedAt') ? new Date(pick('publishedAt')) : undefined
    };
  }).filter(item => item.content.length > 0);
}
