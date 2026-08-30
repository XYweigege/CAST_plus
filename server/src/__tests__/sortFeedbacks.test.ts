import { describe, it, expect } from 'vitest';
import { sortFeedbacks, compareUrgency, URGENCY_ORDER } from '../utils/sortFeedbacks.js';
import type { SortableFeedback } from '../utils/sortFeedbacks.js';

function makeFeedback(overrides: Partial<SortableFeedback> = {}): SortableFeedback {
  return {
    urgency: 'info',
    rating: 3,
    confidence: 0.8,
    publishedAt: '2026-08-25T10:00:00Z',
    createdAt: '2026-08-25T12:00:00Z',
    ...overrides
  };
}

describe('URGENCY_ORDER 映射', () => {
  it('紧急度数值递增：critical < action < attention < info', () => {
    expect(URGENCY_ORDER['critical']).toBeLessThan(URGENCY_ORDER['action']);
    expect(URGENCY_ORDER['action']).toBeLessThan(URGENCY_ORDER['attention']);
    expect(URGENCY_ORDER['attention']).toBeLessThan(URGENCY_ORDER['info']);
  });

  it('compareUrgency：critical 排在 info 之前', () => {
    const a = makeFeedback({ urgency: 'critical' });
    const b = makeFeedback({ urgency: 'info' });
    expect(compareUrgency(a, b)).toBeLessThan(0);
  });

  it('未知紧急度 fallback 为 4', () => {
    const a = makeFeedback({ urgency: 'unknown' });
    const b = makeFeedback({ urgency: 'info' });
    expect(compareUrgency(a, b)).toBeGreaterThan(0);
  });
});

describe('sortFeedbacks', () => {
  describe('按紧急度排序', () => {
    const items = [
      makeFeedback({ urgency: 'info', createdAt: '2026-08-25T10:00:00Z' }),
      makeFeedback({ urgency: 'critical', createdAt: '2026-08-25T09:00:00Z' }),
      makeFeedback({ urgency: 'attention', createdAt: '2026-08-25T11:00:00Z' }),
      makeFeedback({ urgency: 'action', createdAt: '2026-08-25T08:00:00Z' }),
      makeFeedback({ urgency: 'critical', createdAt: '2026-08-25T12:00:00Z' })
    ];

    it('desc：最紧急在前', () => {
      const sorted = sortFeedbacks(items, 'urgency', 'desc');
      expect(sorted.map(i => i.urgency)).toEqual([
        'critical',
        'critical',
        'action',
        'attention',
        'info'
      ]);
    });

    it('asc：最不紧急在前', () => {
      const sorted = sortFeedbacks(items, 'urgency', 'asc');
      expect(sorted.map(i => i.urgency)).toEqual([
        'info',
        'attention',
        'action',
        'critical',
        'critical'
      ]);
    });

    it('相同紧急度按创建时间倒序', () => {
      const sorted = sortFeedbacks(items, 'urgency', 'desc');
      const criticals = sorted.filter(i => i.urgency === 'critical');
      expect(criticals[0].createdAt).toBe('2026-08-25T12:00:00Z');
      expect(criticals[1].createdAt).toBe('2026-08-25T09:00:00Z');
    });
  });

  describe('按评分排序', () => {
    it('desc：最高分在前', () => {
      const items = [
        makeFeedback({ rating: 3 }),
        makeFeedback({ rating: 5 }),
        makeFeedback({ rating: 1 })
      ];
      expect(sortFeedbacks(items, 'rating', 'desc').map(i => i.rating)).toEqual([5, 3, 1]);
    });

    it('rating 为 null 时按 0 处理', () => {
      const items = [makeFeedback({ rating: 4 }), makeFeedback({ rating: null })];
      const sorted = sortFeedbacks(items, 'rating', 'desc');
      expect(sorted[0].rating).toBe(4);
      expect(sorted[1].rating).toBeNull();
    });
  });

  describe('按置信度排序', () => {
    it('asc：低置信度在前，便于优先人工复核', () => {
      const items = [
        makeFeedback({ confidence: 0.95 }),
        makeFeedback({ confidence: 0.42 }),
        makeFeedback({ confidence: 0.7 })
      ];
      expect(sortFeedbacks(items, 'confidence', 'asc').map(i => i.confidence)).toEqual([
        0.42,
        0.7,
        0.95
      ]);
    });
  });

  describe('按创建时间排序', () => {
    it('desc：最新在前', () => {
      const items = [
        makeFeedback({ createdAt: '2026-08-25T08:00:00Z' }),
        makeFeedback({ createdAt: '2026-08-25T14:00:00Z' }),
        makeFeedback({ createdAt: '2026-08-25T11:00:00Z' })
      ];
      const sorted = sortFeedbacks(items, 'createdAt', 'desc');
      expect(sorted[0].createdAt).toBe('2026-08-25T14:00:00Z');
      expect(sorted[2].createdAt).toBe('2026-08-25T08:00:00Z');
    });

    it('未知排序字段降级为按创建时间', () => {
      const items = [
        makeFeedback({ createdAt: '2026-08-25T08:00:00Z' }),
        makeFeedback({ createdAt: '2026-08-25T14:00:00Z' })
      ];
      const sorted = sortFeedbacks(items, 'unknownField', 'desc');
      expect(sorted[0].createdAt).toBe('2026-08-25T14:00:00Z');
    });
  });

  describe('边界情况', () => {
    it('空数组返回空数组', () => {
      expect(sortFeedbacks([], 'urgency', 'desc')).toEqual([]);
    });

    it('不修改原数组', () => {
      const items = [
        makeFeedback({ createdAt: '2026-08-25T08:00:00Z' }),
        makeFeedback({ createdAt: '2026-08-25T14:00:00Z' })
      ];
      const snapshot = [...items];
      sortFeedbacks(items, 'createdAt', 'desc');
      expect(items).toEqual(snapshot);
    });

    it('支持 Date 对象与 ISO 字符串混合', () => {
      const items = [
        makeFeedback({ createdAt: new Date('2026-08-25T14:00:00Z') }),
        makeFeedback({ createdAt: '2026-08-25T10:00:00Z' })
      ];
      const sorted = sortFeedbacks(items, 'createdAt', 'desc');
      expect(new Date(sorted[0].createdAt).getTime()).toBeGreaterThan(
        new Date(sorted[1].createdAt).getTime()
      );
    });
  });
});
