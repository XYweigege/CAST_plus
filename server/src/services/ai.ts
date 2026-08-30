import { OpenRouter } from '@openrouter/sdk';
import { TOPIC_TAGS } from '../constants.js';
import type { FeedbackAnalysis, FeedbackItem, Sentiment, Urgency } from '../types.js';

const openRouter = new OpenRouter({
  apiKey: process.env.OPENROUTER_API_KEY ?? ''
});

const MODEL = process.env.AI_MODEL || 'deepseek/deepseek-v3.2';

/**
 * 判断 AI 服务是否真的可用。
 * 只判断非空是不够的：.env.example 里的占位 Key 也是非空，
 * 会导致每次都发出必然失败的请求，再走兜底，白白增加延迟与错误日志。
 */
function isAIConfigured(): boolean {
  const key = process.env.OPENROUTER_API_KEY ?? '';
  return key.length > 0 && !key.startsWith('your_');
}

// ========== 主题词扩展（Query Expansion） ==========
/**
 * 把一个业务主题词扩展为多个口语化表达变体，用于文本预过滤。
 * 解决的核心问题：业务人员维护的词表是"书面语"（如"理赔时效"），
 * 而客户真实表达是口语（如"拖咗好耐都未賠"），直接匹配召回率极低。
 * 结果会被缓存，同一主题词不会重复调用 AI。
 */
const expansionCache = new Map<string, string[]>();

export async function expandTopic(topic: string): Promise<string[]> {
  if (expansionCache.has(topic)) {
    return expansionCache.get(topic)!;
  }

  const coreTerms = extractCoreTerms(topic);

  if (!isAIConfigured()) {
    const result = [topic, ...coreTerms];
    expansionCache.set(topic, result);
    return result;
  }

  try {
    const result = await openRouter.chat.send({
      model: MODEL,
      messages: [
        {
          role: 'system',
          content: `你是保险行业客户体验专家，擅长把书面业务术语翻译成客户的真实口语表达。

给定一个业务主题词，生成客户在问卷、投诉、社媒中可能使用的各种说法，用于文本匹配。

规则：
1. 必须覆盖香港客户的常用表达，包含繁体中文、粤语口语、英文及中英混排
2. 包含书面说法与缩写
3. 不要加入与主题无关的泛化词（如"保险""服务"这种任何主题都会出现的词）
4. 总数控制在 6-15 个

输出 JSON 数组，只输出 JSON，不要有其他内容。
示例输入："理赔时效"
示例输出：["理赔时效","理赔慢","拖咗好耐都未批","幾時先賠到","claim processing delay","slow claim","遲遲未收到賠款","等咗兩個星期"]`
        },
        {
          role: 'user',
          content: topic
        }
      ],
      temperature: 0.2,
      maxTokens: 400
    });

    const rawContent = result.choices[0]?.message?.content || '';
    const responseContent = typeof rawContent === 'string' ? rawContent : JSON.stringify(rawContent);
    const jsonMatch = responseContent.match(/\[[\s\S]*\]/);
    if (jsonMatch) {
      const parsed: string[] = JSON.parse(jsonMatch[0]);
      const expanded = [...new Set([topic, ...coreTerms, ...parsed.map(s => s.trim()).filter(Boolean)])];
      expansionCache.set(topic, expanded);
      console.log(`  🔍 Topic expansion for "${topic}": ${expanded.length} variants`);
      return expanded;
    }
  } catch (error) {
    console.error('Topic expansion failed:', error);
  }

  const fallback = [topic, ...coreTerms];
  expansionCache.set(topic, fallback);
  return fallback;
}

/** 从主题词中提取核心词（纯文本方式，不依赖 AI） */
function extractCoreTerms(topic: string): string[] {
  const terms: string[] = [];
  const parts = topic.split(/[\s\-_\/\\·、，,]+/).filter(p => p.length >= 2);
  if (parts.length > 1) {
    terms.push(...parts);
    for (let i = 0; i < parts.length - 1; i++) {
      terms.push(parts[i] + parts[i + 1]);
    }
  }
  return [...new Set(terms)].filter(t => t.toLowerCase() !== topic.toLowerCase());
}

/** 检查文本中是否包含任一扩展词（不区分大小写） */
export function preMatchTopic(
  text: string,
  expandedTopics: string[]
): { matched: boolean; matchedTerms: string[] } {
  const lowerText = text.toLowerCase();
  const matchedTerms: string[] = [];
  for (const kw of expandedTopics) {
    if (lowerText.includes(kw.toLowerCase())) {
      matchedTerms.push(kw);
    }
  }
  return { matched: matchedTerms.length > 0, matchedTerms };
}

// ========== AI 反馈分析 ==========

function buildAnalysisPrompt(item: FeedbackItem, preMatch?: { matched: boolean; matchedTerms: string[] }): string {
  const matchHint = preMatch?.matched
    ? `\n提示：文本预匹配命中了主题词变体：${preMatch.matchedTerms.join('、')}`
    : '';

  const metaLine = [
    item.productLine ? `产品线：${item.productLine}` : '',
    item.source ? `渠道：${item.source}` : '',
    item.rating != null ? `客户评分：${item.rating}/5` : '',
    item.language ? `语言：${item.language}` : ''
  ].filter(Boolean).join('  ');

  return `你是保险行业客户体验分析专家，负责把客户反馈结构化为可行动的分析结果。

【可选主题标签】${TOPIC_TAGS.join('、')}
${metaLine}${matchHint}

请分析以下客户反馈并输出：

1. sentiment：positive / neutral / negative
2. topics：从【可选主题标签】中选择 1-3 个，**不要自造标签**
3. urgency：四级定级
   - critical：涉及拒赔、销售误导、威胁投诉至监管机构（如 IA / 保险投诉局）、扬言退保并公开曝光
   - action：明确要求跟进、投诉、索赔受阻、多次催促未果
   - attention：有明确不满但无升级诉求
   - info：一般咨询、中性描述、或正面反馈
4. urgencyReason：一句话说明定级依据
5. aiSummary：一句话点出客户不满或满意的**具体环节**，不要复述原文
6. confidence：0-1 的置信度。表达模糊、语言混杂、语义不明时给低分

【语言注意】
客户可能使用繁体中文、粤语口语或中英混排。以下均为强负面表达：
"唔賠""搵笨""搞咁耐""拖咗好耐""極不負責任""講一套做一套""no response""waste of time"

【示例】
输入：「理賠拖咗兩個星期都未批，打去客服又無人聽，好失望」
输出：{"sentiment":"negative","topics":["理赔时效","客服响应"],"urgency":"attention","urgencyReason":"索赔审核超两周未出结果且电话渠道无人接听","aiSummary":"索赔审核周期过长且客服电话渠道无人接听","confidence":0.93}

输入：「Claim was rejected without any clear explanation. Very frustrated and will escalate to the Insurance Authority.」
输出：{"sentiment":"negative","topics":["拒赔争议","条款清晰度"],"urgency":"critical","urgencyReason":"拒赔未给出清晰解释且明确表示将投诉至监管机构","aiSummary":"拒赔决定缺乏解释，存在监管投诉升级风险","confidence":0.95}

输入：「客服阿 May 解釋得好清楚，成個流程好順，讚」
输出：{"sentiment":"positive","topics":["服务态度"],"urgency":"info","urgencyReason":"正面反馈，无风险信号","aiSummary":"客服人员解释清晰，流程体验顺畅","confidence":0.9}

只输出 JSON，不要有其他内容。
{"sentiment":"","topics":[],"urgency":"","urgencyReason":"","aiSummary":"","confidence":0}`;
}

function fallbackAnalysis(item: FeedbackItem, preMatch?: { matched: boolean; matchedTerms: string[] }): FeedbackAnalysis {
  // AI 不可用时的规则兜底：基于评分与关键词做粗判，保证流程不中断
  const text = item.content.toLowerCase();
  const negativeHints = ['唔賠', '搵笨', '失望', '投訴', '拖', '慢', '差', 'reject', 'delay', 'slow', 'bad', 'worst'];
  const hit = negativeHints.filter(h => text.includes(h));
  const isNegative = hit.length > 0 || (item.rating != null && item.rating <= 2);

  return {
    sentiment: isNegative ? 'negative' : 'neutral',
    topics: preMatch?.matched ? [] : [],
    urgency: isNegative ? 'attention' : 'info',
    urgencyReason: '未配置 AI 服务，使用规则兜底判定',
    aiSummary: item.content.slice(0, 60),
    confidence: 0.3
  };
}

export async function analyzeFeedback(
  item: FeedbackItem,
  preMatch?: { matched: boolean; matchedTerms: string[] }
): Promise<FeedbackAnalysis> {
  if (!isAIConfigured()) {
    return fallbackAnalysis(item, preMatch);
  }

  try {
    const prompt = buildAnalysisPrompt(item, preMatch);

    const result = await openRouter.chat.send({
      model: MODEL,
      messages: [
        { role: 'system', content: prompt },
        { role: 'user', content: item.content.slice(0, 2000) }
      ],
      temperature: 0.1, // 标注任务要求一致性，温度压低
      maxTokens: 500
    });

    const rawContent = result.choices[0]?.message?.content || '';
    const responseContent = typeof rawContent === 'string' ? rawContent : JSON.stringify(rawContent);

    const jsonMatch = responseContent.match(/\{[\s\S]*\}/);
    if (jsonMatch) {
      const parsed = JSON.parse(jsonMatch[0]);
      const topics: string[] = Array.isArray(parsed.topics)
        ? parsed.topics.filter((t: unknown) => typeof t === 'string' && TOPIC_TAGS.includes(t as never))
        : [];

      return {
        sentiment: (['positive', 'neutral', 'negative'].includes(parsed.sentiment) ? parsed.sentiment : 'neutral') as Sentiment,
        topics,
        urgency: (['info', 'attention', 'action', 'critical'].includes(parsed.urgency) ? parsed.urgency : 'info') as Urgency,
        urgencyReason: String(parsed.urgencyReason || '').slice(0, 200),
        aiSummary: String(parsed.aiSummary || '').slice(0, 200),
        confidence: Math.min(1, Math.max(0, Number(parsed.confidence) || 0.5))
      };
    }

    throw new Error('Failed to parse AI response');
  } catch (error) {
    console.error('AI analysis failed:', error);
    return fallbackAnalysis(item, preMatch);
  }
}

/** 批量分析，限制并发避免触发限流 */
export async function batchAnalyzeFeedback(
  items: FeedbackItem[],
  expandedTopics?: string[]
): Promise<FeedbackAnalysis[]> {
  const batchSize = 3;
  const results: FeedbackAnalysis[] = [];

  for (let i = 0; i < items.length; i += batchSize) {
    const batch = items.slice(i, i + batchSize);
    const batchResults = await Promise.all(
      batch.map(item => {
        const preMatch = expandedTopics ? preMatchTopic(item.content, expandedTopics) : undefined;
        return analyzeFeedback(item, preMatch);
      })
    );
    results.push(...batchResults);
  }

  return results;
}

// ========== 评分归因报告 ==========

export interface InsightReport {
  productLine: string;
  totalFeedback: number;
  avgRating: number | null;
  negativeRatio: number;
  topTopics: { topic: string; count: number; negativeCount: number }[];
  summary: string;
  suggestions: string[];
}

/**
 * 基于一批反馈生成产品评分归因报告。
 * 统计部分由代码完成（可复现），归纳与建议部分交给 LLM，
 * 避免让模型做它不擅长的计数任务。
 */
export async function generateInsightReport(
  productLine: string,
  rows: { content: string; rating: number | null; sentiment: string; topics: string | null; aiSummary: string | null }[]
): Promise<InsightReport> {
  // 1) 统计（确定性计算）
  const totalFeedback = rows.length;
  const rated = rows.filter(r => r.rating != null);
  const avgRating = rated.length
    ? Number((rated.reduce((s, r) => s + (r.rating ?? 0), 0) / rated.length).toFixed(2))
    : null;
  const negativeCount = rows.filter(r => r.sentiment === 'negative').length;
  const negativeRatio = totalFeedback ? Number((negativeCount / totalFeedback).toFixed(3)) : 0;

  const topicMap = new Map<string, { count: number; negativeCount: number }>();
  for (const r of rows) {
    let list: string[] = [];
    try {
      list = r.topics ? JSON.parse(r.topics) : [];
    } catch {
      list = [];
    }
    for (const t of list) {
      const cur = topicMap.get(t) || { count: 0, negativeCount: 0 };
      cur.count += 1;
      if (r.sentiment === 'negative') cur.negativeCount += 1;
      topicMap.set(t, cur);
    }
  }
  const topTopics = [...topicMap.entries()]
    .map(([topic, v]) => ({ topic, ...v }))
    .sort((a, b) => b.count - a.count);

  // 2) 归纳（LLM）
  let summary = `共 ${totalFeedback} 条反馈，负面占比 ${(negativeRatio * 100).toFixed(1)}%。`;
  let suggestions: string[] = [];

  if (!isAIConfigured() || totalFeedback === 0) {
    summary += topTopics.length ? `主要集中于：${topTopics.slice(0, 3).map(t => t.topic).join('、')}。` : '';
    return { productLine, totalFeedback, avgRating, negativeRatio, topTopics, summary, suggestions };
  }

  const sampleSummaries = rows
    .filter(r => r.sentiment === 'negative')
    .slice(0, 30)
    .map((r, i) => `${i + 1}. ${r.aiSummary || r.content.slice(0, 80)}`)
    .join('\n');

  try {
    const result = await openRouter.chat.send({
      model: MODEL,
      messages: [
        {
          role: 'system',
          content: `你是保险行业客户体验分析专家。基于一批客户反馈的 AI 摘要，输出评分归因报告。

要求：
1. summary：3 句话以内，说明该产品线客户体验的整体状况与主要失分点，要有观点不要罗列
2. suggestions：3 条具体的改进建议，每条要指向明确的业务环节，可执行，不要空话

只输出 JSON：{"summary":"...","suggestions":["...","...","..."]}`
        },
        {
          role: 'user',
          content: `产品线：${productLine}
反馈总数：${totalFeedback}，负面占比：${(negativeRatio * 100).toFixed(1)}%
平均评分：${avgRating ?? 'N/A'}
主题分布：${topTopics.slice(0, 8).map(t => `${t.topic}(${t.count})`).join('、')}

负面反馈摘要样本：
${sampleSummaries}`
        }
      ],
      temperature: 0.3,
      maxTokens: 600
    });

    const rawContent = result.choices[0]?.message?.content || '';
    const responseContent = typeof rawContent === 'string' ? rawContent : JSON.stringify(rawContent);
    const jsonMatch = responseContent.match(/\{[\s\S]*\}/);
    if (jsonMatch) {
      const parsed = JSON.parse(jsonMatch[0]);
      summary = String(parsed.summary || summary);
      suggestions = Array.isArray(parsed.suggestions) ? parsed.suggestions.map(String).slice(0, 5) : [];
    }
  } catch (error) {
    console.error('Insight report generation failed:', error);
    summary += topTopics.length ? `主要集中于：${topTopics.slice(0, 3).map(t => t.topic).join('、')}。` : '';
  }

  return { productLine, totalFeedback, avgRating, negativeRatio, topTopics, summary, suggestions };
}
