import { useState, Fragment } from 'react';
import { ChevronDown, ChevronRight, ChevronLeft, Star, Clock, ShieldAlert } from 'lucide-react';
import { cn } from '../lib/utils';
import {
  SENTIMENT_STYLE, URGENCY_STYLE, productLabel, sourceLabel, sentimentLabel, TOPIC_TAGS
} from '../constants';
import { relativeTime, formatDateTime } from '../utils/relativeTime';
import type { Feedback } from '../services/api';

interface FeedbackTableProps {
  data: Feedback[];
  loading: boolean;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onReview: (id: string, sentiment: string) => void;
}

function parseTopics(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(t => TOPIC_TAGS.includes(t)) : [];
  } catch {
    return [];
  }
}

export default function FeedbackTable({
  data,
  loading,
  page,
  totalPages,
  onPageChange,
  onReview
}: FeedbackTableProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const toggle = (id: string) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  if (loading) {
    return (
      <div className="bg-white rounded-lg border border-[#e3e8ef] py-20">
        <div className="flex flex-col items-center gap-3">
          <div className="w-7 h-7 border-2 border-blue-100 border-t-blue-600 rounded-full animate-spin" />
          <span className="text-sm text-slate-400">加载中</span>
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="bg-white rounded-lg border border-[#e3e8ef] py-20">
        <div className="flex flex-col items-center gap-2">
          <p className="text-sm text-slate-500">暂无数据</p>
          <p className="text-xs text-slate-400">调整筛选条件，或点击「生成演示数据」</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg border border-[#e3e8ef] overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-[#f8fafc] border-b border-[#e3e8ef]">
              <th className="w-10 px-3 py-3" />
              <th className="w-[84px] px-3 py-3 text-left text-xs font-semibold text-slate-600">紧急度</th>
              <th className="w-[68px] px-3 py-3 text-left text-xs font-semibold text-slate-600">情感</th>
              <th className="px-3 py-3 text-left text-xs font-semibold text-slate-600">反馈内容</th>
              <th className="w-[150px] px-3 py-3 text-left text-xs font-semibold text-slate-600">主题</th>
              <th className="w-[92px] px-3 py-3 text-left text-xs font-semibold text-slate-600">产品线</th>
              <th className="w-[84px] px-3 py-3 text-left text-xs font-semibold text-slate-600">渠道</th>
              <th className="w-[58px] px-3 py-3 text-left text-xs font-semibold text-slate-600">评分</th>
              <th className="w-[68px] px-3 py-3 text-left text-xs font-semibold text-slate-600">置信度</th>
              <th className="w-[80px] px-3 py-3 text-left text-xs font-semibold text-slate-600">时间</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#eef2f7]">
            {data.map(row => {
              const isOpen = expanded.has(row.id);
              const topics = parseTopics(row.topics);
              const urgency = URGENCY_STYLE[row.urgency];
              const sentiment = SENTIMENT_STYLE[row.sentiment];

              return (
                <Fragment key={row.id}>
                  <tr
                    onClick={() => toggle(row.id)}
                    className={cn(
                      "cursor-pointer transition-colors",
                      isOpen ? "bg-blue-50/40" : "hover:bg-[#f8fafc]"
                    )}
                  >
                    {/* 展开箭头 */}
                    <td className="px-3 py-3 align-top">
                      <span className="text-slate-400 inline-flex mt-0.5">
                        {isOpen ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </span>
                    </td>

                    {/* 紧急度 */}
                    <td className="px-3 py-3 align-top">
                      {urgency ? (
                        <span className={cn("inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-medium border whitespace-nowrap", urgency.badge)}>
                          <span className={cn("w-1.5 h-1.5 rounded-full", urgency.dot)} />
                          {urgency.label}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-400">{row.urgency}</span>
                      )}
                    </td>

                    {/* 情感 */}
                    <td className="px-3 py-3 align-top">
                      <span className={cn("inline-block px-2 py-0.5 rounded text-xs font-medium border whitespace-nowrap", sentiment?.badge ?? 'bg-slate-50 text-slate-600 border-slate-200')}>
                        {sentiment?.label ?? row.sentiment}
                      </span>
                    </td>

                    {/* 内容 */}
                    <td className="px-3 py-3 align-top">
                      <p className={cn("text-slate-800 leading-relaxed", !isOpen && "line-clamp-2")}>
                        {row.content}
                      </p>
                      {row.aiSummary && !isOpen && (
                        <p className="text-xs text-slate-500 mt-1 line-clamp-1">
                          <span className="text-blue-600/70 font-medium">AI 归因 · </span>
                          {row.aiSummary}
                        </p>
                      )}
                      {!row.isReviewed && (
                        <span className="inline-flex items-center gap-1 mt-1.5 px-1.5 py-0.5 rounded bg-orange-50 text-orange-600 text-[11px] border border-orange-200">
                          <ShieldAlert className="w-3 h-3" />
                          待复核
                        </span>
                      )}
                    </td>

                    {/* 主题 */}
                    <td className="px-3 py-3 align-top">
                      {topics.length === 0 ? (
                        <span className="text-xs text-slate-300">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {topics.map(t => (
                            <span key={t} className="inline-block px-1.5 py-0.5 rounded bg-blue-50 text-blue-700 text-[11px] border border-blue-100 whitespace-nowrap">
                              {t}
                            </span>
                          ))}
                        </div>
                      )}
                    </td>

                    {/* 产品线 */}
                    <td className="px-3 py-3 align-top text-xs text-slate-600 whitespace-nowrap">
                      {productLabel(row.productLine)}
                    </td>

                    {/* 渠道 */}
                    <td className="px-3 py-3 align-top text-xs text-slate-600 whitespace-nowrap">
                      {sourceLabel(row.source)}
                    </td>

                    {/* 评分 */}
                    <td className="px-3 py-3 align-top">
                      {row.rating != null ? (
                        <span className="inline-flex items-center gap-0.5 text-xs text-amber-600 font-medium">
                          <Star className="w-3 h-3 fill-current" />
                          {row.rating}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-300">—</span>
                      )}
                    </td>

                    {/* 置信度 */}
                    <td className="px-3 py-3 align-top">
                      {row.confidence != null ? (
                        <span
                          className={cn(
                            "text-xs font-medium",
                            row.confidence >= 0.7 ? "text-slate-600" : "text-orange-600"
                          )}
                        >
                          {(row.confidence * 100).toFixed(0)}%
                        </span>
                      ) : (
                        <span className="text-xs text-slate-300">—</span>
                      )}
                    </td>

                    {/* 时间 */}
                    <td className="px-3 py-3 align-top">
                      <span
                        className="inline-flex items-center gap-1 text-xs text-slate-400 whitespace-nowrap"
                        title={formatDateTime(row.createdAt)}
                      >
                        <Clock className="w-3 h-3" />
                        {relativeTime(row.createdAt)}
                      </span>
                    </td>
                  </tr>

                  {/* ===== 展开详情 ===== */}
                  {isOpen && (
                    <tr className="bg-blue-50/30">
                      <td colSpan={10} className="px-6 py-5">
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                          {/* 原文 */}
                          <div className="lg:col-span-2 space-y-4">
                            <div>
                              <div className="text-xs font-semibold text-slate-500 mb-1.5">客户原话</div>
                              <div className="bg-white rounded-md border border-[#e3e8ef] p-3 text-sm text-slate-700 leading-relaxed whitespace-pre-wrap">
                                {row.content}
                              </div>
                            </div>

                            {row.urgencyReason && (
                              <div>
                                <div className="text-xs font-semibold text-slate-500 mb-1.5">AI 定级理由</div>
                                <div className="bg-white rounded-md border border-[#e3e8ef] p-3 text-sm text-slate-600 leading-relaxed">
                                  {row.urgencyReason}
                                </div>
                              </div>
                            )}
                          </div>

                          {/* 元信息 + 复核 */}
                          <div className="space-y-4">
                            <div>
                              <div className="text-xs font-semibold text-slate-500 mb-2">分析详情</div>
                              <dl className="bg-white rounded-md border border-[#e3e8ef] divide-y divide-[#eef2f7] text-sm">
                                <MetaRow label="情感倾向" value={sentimentLabel(row.sentiment)} />
                                <MetaRow label="紧急度" value={URGENCY_STYLE[row.urgency]?.label ?? row.urgency} />
                                <MetaRow label="产品线" value={productLabel(row.productLine)} />
                                <MetaRow label="渠道" value={sourceLabel(row.source)} />
                                <MetaRow label="AI 归因" value={row.aiSummary ?? '—'} />
                                <MetaRow
                                  label="置信度"
                                  value={row.confidence != null ? `${(row.confidence * 100).toFixed(0)}%` : '—'}
                                />
                                <MetaRow label="主题标签" value={topics.length ? topics.join('、') : '—'} />
                                {row.topic && <MetaRow label="归属主题词" value={row.topic.text} />}
                                {row.authorName && <MetaRow label="客户" value={row.authorName} />}
                                {row.publishedAt && (
                                  <MetaRow label="反馈时间" value={formatDateTime(row.publishedAt)} />
                                )}
                              </dl>
                            </div>

                            {!row.isReviewed && (
                              <div>
                                <div className="text-xs font-semibold text-slate-500 mb-2">人工复核</div>
                                <div className="flex gap-2">
                                  {(['negative', 'neutral', 'positive'] as const).map(s => (
                                    <button
                                      key={s}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        onReview(row.id, s);
                                      }}
                                      className="flex-1 px-3 py-1.5 rounded-md border border-[#e3e8ef] bg-white text-xs font-medium text-slate-600 hover:border-blue-400 hover:text-blue-600 transition-colors"
                                    >
                                      {sentimentLabel(s)}
                                    </button>
                                  ))}
                                </div>
                                <p className="text-[11px] text-slate-400 mt-2">
                                  该条置信度较低，未作为终态，校正后即生效
                                </p>
                              </div>
                            )}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* 分页 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-[#e3e8ef] bg-white">
          <span className="text-xs text-slate-500">
            第 {page} / {totalPages} 页
          </span>
          <div className="flex items-center gap-1">
            <PageButton disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
              <ChevronLeft className="w-4 h-4" />
            </PageButton>
            {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
              let p: number;
              if (totalPages <= 7) p = i + 1;
              else if (page <= 4) p = i + 1;
              else if (page >= totalPages - 3) p = totalPages - 6 + i;
              else p = page - 3 + i;
              return (
                <button
                  key={p}
                  onClick={() => onPageChange(p)}
                  className={cn(
                    "min-w-[30px] h-[30px] rounded-md text-xs font-medium transition-colors",
                    page === p
                      ? "bg-blue-600 text-white"
                      : "text-slate-600 hover:bg-[#f1f5f9]"
                  )}
                >
                  {p}
                </button>
              );
            })}
            <PageButton disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
              <ChevronRight className="w-4 h-4" />
            </PageButton>
          </div>
        </div>
      )}
    </div>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3 px-3 py-2">
      <dt className="w-[68px] shrink-0 text-xs text-slate-500">{label}</dt>
      <dd className="flex-1 text-xs text-slate-700 leading-relaxed break-words">{value}</dd>
    </div>
  );
}

function PageButton({
  children,
  disabled,
  onClick
}: {
  children: React.ReactNode;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "w-[30px] h-[30px] rounded-md flex items-center justify-center transition-colors",
        disabled
          ? "text-slate-300 cursor-not-allowed"
          : "text-slate-600 hover:bg-[#f1f5f9]"
      )}
    >
      {children}
    </button>
  );
}
