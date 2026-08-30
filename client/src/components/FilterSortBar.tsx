import { useState, useEffect } from 'react';
import { Search, RotateCcw, Clock, AlertTriangle, Star, Shield } from 'lucide-react';
import { cn } from '../lib/utils';
import { PRODUCT_LINES, SENTIMENTS, SOURCES, URGENCIES } from '../constants';
import type { Topic } from '../services/api';

export interface FilterState {
  keyword: string;
  source: string;
  sentiment: string;
  urgency: string;
  productLine: string;
  topicId: string;
  timeRange: string;
  pendingReview: string;
  sortBy: string;
  sortOrder: string;
}

export const defaultFilterState: FilterState = {
  keyword: '',
  source: '',
  sentiment: '',
  urgency: '',
  productLine: '',
  topicId: '',
  timeRange: '',
  pendingReview: '',
  sortBy: 'createdAt',
  sortOrder: 'desc'
};

interface Props {
  filters: FilterState;
  onChange: (filters: FilterState) => void;
  topics: Topic[];
}

const SORT_OPTIONS = [
  { value: 'createdAt', label: '最新', icon: Clock },
  { value: 'urgency', label: '紧急度', icon: AlertTriangle },
  { value: 'rating', label: '评分', icon: Star },
  { value: 'confidence', label: '置信度', icon: Shield }
];

const TIME_OPTIONS = [
  { value: '', label: '全部时间' },
  { value: '24h', label: '最近 24 小时' },
  { value: 'today', label: '今天' },
  { value: '7d', label: '最近 7 天' },
  { value: '30d', label: '最近 30 天' }
];

const selectCls =
  "h-8 pl-2.5 pr-7 rounded-md border border-[#e3e8ef] bg-white text-[13px] text-slate-700 " +
  "focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 " +
  "appearance-none bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2216%22 height=%2216%22 fill=%22none%22 stroke=%22%2394a3b8%22 stroke-width=%222%22%3E%3Cpath d=%22M4 6l4 4 4-4%22/%3E%3C/svg%3E')] bg-no-repeat bg-[right_0.4rem_center] cursor-pointer";

export default function FilterSortBar({ filters, onChange, topics }: Props) {
  const [keyword, setKeyword] = useState(filters.keyword);

  // 关键词输入防抖，避免每敲一个字就请求
  useEffect(() => {
    const timer = setTimeout(() => {
      if (keyword !== filters.keyword) {
        onChange({ ...filters, keyword });
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [keyword]); // eslint-disable-line react-hooks/exhaustive-deps

  const update = (key: keyof FilterState, value: string) => onChange({ ...filters, [key]: value });

  const activeCount = [
    filters.keyword,
    filters.source,
    filters.sentiment,
    filters.urgency,
    filters.productLine,
    filters.topicId,
    filters.timeRange,
    filters.pendingReview
  ].filter(Boolean).length;

  const reset = () => {
    setKeyword('');
    onChange({ ...defaultFilterState });
  };

  return (
    <div className="bg-white rounded-lg border border-[#e3e8ef] p-3">
      <div className="flex flex-wrap items-center gap-2">
        {/* 关键词搜索 */}
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索反馈内容或 AI 归因"
            className="h-8 w-[220px] pl-8 pr-3 rounded-md border border-[#e3e8ef] bg-white text-[13px] text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
          />
        </div>

        <Divider />

        <select className={selectCls} value={filters.sentiment} onChange={e => update('sentiment', e.target.value)}>
          <option value="">全部情感</option>
          {SENTIMENTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>

        <select className={selectCls} value={filters.urgency} onChange={e => update('urgency', e.target.value)}>
          <option value="">全部紧急度</option>
          {URGENCIES.map(u => <option key={u.value} value={u.value}>{u.label}</option>)}
        </select>

        <select className={selectCls} value={filters.productLine} onChange={e => update('productLine', e.target.value)}>
          <option value="">全部产品线</option>
          {PRODUCT_LINES.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>

        <select className={selectCls} value={filters.source} onChange={e => update('source', e.target.value)}>
          <option value="">全部渠道</option>
          {SOURCES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>

        <select className={selectCls} value={filters.topicId} onChange={e => update('topicId', e.target.value)}>
          <option value="">全部主题词</option>
          {topics.filter(t => t.isActive).map(t => <option key={t.id} value={t.id}>{t.text}</option>)}
        </select>

        <select className={selectCls} value={filters.timeRange} onChange={e => update('timeRange', e.target.value)}>
          {TIME_OPTIONS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>

        <select className={selectCls} value={filters.pendingReview} onChange={e => update('pendingReview', e.target.value)}>
          <option value="">全部状态</option>
          <option value="true">仅待复核</option>
        </select>

        {activeCount > 0 && (
          <button
            onClick={reset}
            className="h-8 px-2.5 rounded-md text-[13px] text-slate-500 hover:text-blue-600 hover:bg-blue-50 inline-flex items-center gap-1 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            重置
          </button>
        )}
      </div>

      {/* 排序 */}
      <div className="flex items-center gap-2 mt-3 pt-3 border-t border-[#eef2f7]">
        <span className="text-xs text-slate-500">排序</span>
        <div className="flex items-center gap-1">
          {SORT_OPTIONS.map(opt => {
            const Icon = opt.icon;
            return (
              <button
                key={opt.value}
                onClick={() => update('sortBy', opt.value)}
                className={cn(
                  "h-7 px-2.5 rounded-md text-xs font-medium inline-flex items-center gap-1 transition-colors",
                  filters.sortBy === opt.value
                    ? "bg-blue-50 text-blue-700 border border-blue-200"
                    : "text-slate-500 hover:bg-slate-50 border border-transparent"
                )}
              >
                <Icon className="w-3.5 h-3.5" />
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function Divider() {
  return <span className="w-px h-5 bg-[#e3e8ef]" />;
}
