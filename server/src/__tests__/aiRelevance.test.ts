/**
 * AI 反馈标注准确度评估
 *
 * 验证 AI 对客户反馈的结构化标注质量：情感倾向、主题归类、紧急度定级。
 * 语料覆盖香港客户的真实表达习惯：繁体中文、粤语口语、英文、中英混排。
 *
 * 运行方式：
 *   npx vitest run src/__tests__/aiRelevance.test.ts
 *
 * 需要配置 OPENROUTER_API_KEY，未配置时 AI 相关用例会跳过。
 * 输出的准确率报告可直接作为模型选型与 Prompt 迭代的依据。
 */

import { describe, it, expect } from 'vitest';
import dotenv from 'dotenv';
import { analyzeFeedback, expandTopic, preMatchTopic } from '../services/ai.js';

dotenv.config();

// 只有配置了真实可用的 Key 才跑 AI 用例：占位符会被误判为已配置，
// 导致请求失败后走规则兜底，准确率统计失去意义。
const API_KEY = process.env.OPENROUTER_API_KEY ?? '';
const HAS_API_KEY = API_KEY.startsWith('sk-or-v1-');
const TIMEOUT = 60000;

interface AnnotationCase {
  name: string;
  content: string;
  productLine: string;
  rating: number;
  expectSentiment: 'positive' | 'neutral' | 'negative';
  expectTopics: string[]; // 至少命中其中一个
  expectUrgency: string; // 允许 ±1 级容差
}

const CASES: AnnotationCase[] = [
  {
    name: '粤语 - 理赔拖延且客服失联',
    content: '理賠拖咗三個星期都未批，打電話又無人聽，好失望',
    productLine: 'medical',
    rating: 1,
    expectSentiment: 'negative',
    expectTopics: ['理赔时效', '客服响应'],
    expectUrgency: 'attention'
  },
  {
    name: '英文 - 拒赔且扬言投诉监管（最高紧急度）',
    content: 'Claim rejected without any clear explanation. I will escalate this to the Insurance Authority.',
    productLine: 'medical',
    rating: 1,
    expectSentiment: 'negative',
    expectTopics: ['拒赔争议'],
    expectUrgency: 'critical'
  },
  {
    name: '繁中正面 - 客服服务态度',
    content: '客服阿 May 解釋得好清楚，好有耐性，讚',
    productLine: 'travel',
    rating: 5,
    expectSentiment: 'positive',
    expectTopics: ['服务态度'],
    expectUrgency: 'info'
  },
  {
    name: '中英混排 - APP 上传文件失败',
    content: '個 app 好難用，upload document 次次 fail',
    productLine: 'travel',
    rating: 2,
    expectSentiment: 'negative',
    expectTopics: ['APP与网站体验'],
    expectUrgency: 'attention'
  },
  {
    name: '粤语 - 销售误导（合规高风险）',
    content: '當初 agent 話全保，原來一堆除外責任，講一套做一套',
    productLine: 'medical',
    rating: 1,
    expectSentiment: 'negative',
    expectTopics: ['销售误导'],
    expectUrgency: 'action'
  },
  {
    name: '英文中性 - 咨询索赔文件',
    content: 'May I know what documents are needed for a travel insurance claim?',
    productLine: 'travel',
    rating: 3,
    expectSentiment: 'neutral',
    expectTopics: ['理赔资料繁琐', '核保与投保'],
    expectUrgency: 'info'
  },
  {
    name: '繁中 - 赔付金额争议',
    content: '賠得咁少，醫療費兩萬只賠三千，根本唔合理',
    productLine: 'medical',
    rating: 2,
    expectSentiment: 'negative',
    expectTopics: ['赔付金额争议'],
    expectUrgency: 'attention'
  },
  {
    name: '粤语 - 续保保费暴涨',
    content: '續保保費加咗三成，事前完全無通知',
    productLine: 'motor',
    rating: 2,
    expectSentiment: 'negative',
    expectTopics: ['续保与退保'],
    expectUrgency: 'attention'
  },
  {
    name: '英文 - 条款含糊不清',
    content: 'Terms and conditions are too vague. No one can understand what is actually covered.',
    productLine: 'pet',
    rating: 2,
    expectSentiment: 'negative',
    expectTopics: ['条款清晰度'],
    expectUrgency: 'attention'
  },
  {
    name: '繁中中性 - 确认收妥',
    content: '保單已收到，謝謝',
    productLine: 'travel',
    rating: 3,
    expectSentiment: 'neutral',
    expectTopics: ['核保与投保'],
    expectUrgency: 'info'
  },
  {
    name: '英文正面 - 理赔时效',
    content: 'Claim was settled within 3 days. Excellent service.',
    productLine: 'travel',
    rating: 5,
    expectSentiment: 'positive',
    expectTopics: ['理赔时效'],
    expectUrgency: 'info'
  },
  {
    name: '繁中 - 理赔资料繁琐',
    content: '索償要交十幾份文件，缺一份又打回頭，搞咁耐',
    productLine: 'medical',
    rating: 2,
    expectSentiment: 'negative',
    expectTopics: ['理赔资料繁琐'],
    expectUrgency: 'attention'
  }
];

// 紧急度相邻级别映射，用于容差判定
const URGENCY_LEVEL: Record<string, number> = { info: 0, attention: 1, action: 2, critical: 3 };

// ============ 主题词扩展测试（无需 API Key） ============

describe('主题词扩展（Topic Expansion）', () => {
  it('无 API Key 时仍返回原始词与核心词', async () => {
    const result = await expandTopic('理赔时效');
    expect(result).toContain('理赔时效');
    expect(result.length).toBeGreaterThanOrEqual(1);
  });

  it('预匹配能命中客户口语表达', () => {
    const result = preMatchTopic('理賠拖咗好耐都未賠', ['理赔时效', '拖咗好耐都未賠', 'claim delay']);
    expect(result.matched).toBe(true);
    expect(result.matchedTerms).toContain('拖咗好耐都未賠');
  });

  it('预匹配不命中无关内容', () => {
    const result = preMatchTopic('今日天氣好好', ['理赔时效', '拖咗好耐都未賠']);
    expect(result.matched).toBe(false);
  });

  it('预匹配不区分大小写', () => {
    const result = preMatchTopic('CLAIM DELAY again', ['claim delay']);
    expect(result.matched).toBe(true);
  });
});

// ============ AI 标注准确度评估（需要 API Key） ============

describe.skipIf(!HAS_API_KEY)('AI 结构化标注准确度评估', () => {
  for (const tc of CASES) {
    it(tc.name, async () => {
      const result = await analyzeFeedback({
        content: tc.content,
        source: 'survey',
        productLine: tc.productLine,
        rating: tc.rating
      });

      console.log(`\n  [${tc.name}]`);
      console.log(`    AI 结果: sentiment=${result.sentiment}, topics=${JSON.stringify(result.topics)}, urgency=${result.urgency}, confidence=${result.confidence}`);
      console.log(`    归因: ${result.aiSummary}`);

      expect(result.sentiment).toBe(tc.expectSentiment);
      expect(result.topics.some(t => tc.expectTopics.includes(t))).toBe(true);

      // 紧急度允许 ±1 级容差
      const diff = Math.abs(
        (URGENCY_LEVEL[result.urgency] ?? 0) - (URGENCY_LEVEL[tc.expectUrgency] ?? 0)
      );
      expect(diff).toBeLessThanOrEqual(1);
    }, TIMEOUT);
  }
});

// ============ 准确率汇总报告 ============

describe.skipIf(!HAS_API_KEY)('准确率汇总报告', () => {
  it('输出情感/主题/紧急度三项准确率', async () => {
    let sentimentOk = 0;
    let topicOk = 0;
    let urgencyOk = 0;
    let urgencyWithinTolerance = 0;
    const details: string[] = [];

    for (const tc of CASES) {
      const result = await analyzeFeedback({
        content: tc.content,
        source: 'survey',
        productLine: tc.productLine,
        rating: tc.rating
      });

      const sOk = result.sentiment === tc.expectSentiment;
      const tOk = result.topics.some(t => tc.expectTopics.includes(t));
      const uExact = result.urgency === tc.expectUrgency;
      const uTol =
        Math.abs((URGENCY_LEVEL[result.urgency] ?? 0) - (URGENCY_LEVEL[tc.expectUrgency] ?? 0)) <= 1;

      if (sOk) sentimentOk++;
      if (tOk) topicOk++;
      if (uExact) urgencyOk++;
      if (uTol) urgencyWithinTolerance++;

      details.push(
        `  ${sOk && tOk && uTol ? 'PASS' : 'FAIL'}  ${tc.name}` +
          `  [sentiment=${result.sentiment}/${tc.expectSentiment}]` +
          `  [topics=${JSON.stringify(result.topics)}]` +
          `  [urgency=${result.urgency}/${tc.expectUrgency}]`
      );
    }

    const total = CASES.length;
    const pct = (n: number) => ((n / total) * 100).toFixed(1);

    console.log('\n' + '='.repeat(64));
    console.log('AI 反馈标注准确率报告');
    console.log('='.repeat(64));
    console.log(`样本数: ${total}`);
    console.log(`情感倾向准确率: ${pct(sentimentOk)}%  (${sentimentOk}/${total})`);
    console.log(`主题归类命中率: ${pct(topicOk)}%  (${topicOk}/${total})`);
    console.log(`紧急度精确匹配: ${pct(urgencyOk)}%  (${urgencyOk}/${total})`);
    console.log(`紧急度±1容差 : ${pct(urgencyWithinTolerance)}%  (${urgencyWithinTolerance}/${total})`);
    console.log('-'.repeat(64));
    details.forEach(d => console.log(d));
    console.log('='.repeat(64));

    // 质量底线：情感与主题必须达标，否则 Prompt 需要迭代
    expect(sentimentOk / total).toBeGreaterThanOrEqual(0.8);
    expect(topicOk / total).toBeGreaterThanOrEqual(0.8);
  }, 300000);
});
