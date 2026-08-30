/**
 * 反馈排序工具（前端版本，与 server/src/utils/sortFeedbacks.ts 逻辑保持一致）
 */

export interface SortableFeedback {
  urgency: string;
  rating: number | null;
  confidence: number | null;
  publishedAt: Date | string | null;
  createdAt: Date | string;
}

export const URGENCY_ORDER: Record<string, number> = {
  critical: 0,
  action: 1,
  attention: 2,
  info: 3
};

function toTimestamp(d: Date | string | null): number {
  if (!d) return 0;
  return typeof d === 'string' ? new Date(d).getTime() : d.getTime();
}

export function compareUrgency(a: SortableFeedback, b: SortableFeedback): number {
  return (URGENCY_ORDER[a.urgency] ?? 4) - (URGENCY_ORDER[b.urgency] ?? 4);
}

export function sortFeedbacks<T extends SortableFeedback>(
  items: T[],
  sortBy: string,
  sortOrder: 'asc' | 'desc' = 'desc'
): T[] {
  const sorted = [...items];
  const desc = sortOrder === 'desc';

  sorted.sort((a, b) => {
    let result: number;

    switch (sortBy) {
      case 'urgency': {
        result = compareUrgency(a, b);
        if (result === 0) {
          result = toTimestamp(a.createdAt) - toTimestamp(b.createdAt);
          return desc ? -(result) : result;
        }
        return desc ? result : -result;
      }

      case 'rating':
        result = (a.rating ?? 0) - (b.rating ?? 0);
        break;

      case 'confidence':
        result = (a.confidence ?? 0) - (b.confidence ?? 0);
        break;

      case 'publishedAt': {
        result = toTimestamp(a.publishedAt) - toTimestamp(b.publishedAt);
        if (result === 0) {
          result = toTimestamp(a.createdAt) - toTimestamp(b.createdAt);
        }
        break;
      }

      default:
        result = toTimestamp(a.createdAt) - toTimestamp(b.createdAt);
        break;
    }

    return desc ? -(result) : result;
  });

  return sorted;
}
