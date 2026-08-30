import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  AlertTriangle, Bell, Check, Trash2, RefreshCw, Sparkles,
  Star, Target, MessageSquare, TrendingDown, X, Plus
} from 'lucide-react';
import {
  topicsApi, feedbacksApi, alertsApi, triggerInsightCheck,
  type Topic, type Feedback, type Stats, type Alert,
  type AnalysisResult, type InsightReport, type FeedbackQuery
} from './services/api';
import { onNewFeedback, onAlert, subscribeToTopics } from './services/socket';
import { isLoggedIn } from './services/auth';
import { cn } from './lib/utils';
import Layout, { type TabKey } from './components/Layout';
import LoginPage from './components/LoginPage';
import FeedbackTable from './components/FeedbackTable';
import FilterSortBar, { defaultFilterState, type FilterState } from './components/FilterSortBar';
import { PRODUCT_LINES } from './constants';

/**
 * 路由守卫：未登录显示登录页；监听 401 触发的 auth:logout 事件自动退出。
 */
function App() {
  const [authed, setAuthed] = useState(isLoggedIn());

  useEffect(() => {
    const onLogout = () => setAuthed(false);
    window.addEventListener('auth:logout', onLogout);
    return () => window.removeEventListener('auth:logout', onLogout);
  }, []);

  if (!authed) {
    return <LoginPage onSuccess={() => setAuthed(true)} />;
  }
  return <MainApp />;
}

function MainApp() {
  const [activeTab, setActiveTab] = useState<TabKey>('feedbacks');

  const [topics, setTopics] = useState<Topic[]>([]);
  const [feedbacks, setFeedbacks] = useState<Feedback[]>([]);
  // topicId -> 主题词文本（Java 端反馈只带 topicId，用于表格展示归属主题词）
  const topicMap = useMemo(() => Object.fromEntries(topics.map(t => [t.id, t.text])), [topics]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const [filters, setFilters] = useState<FilterState>({ ...defaultFilterState });
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [showAlerts, setShowAlerts] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // 主题词
  const [newTopic, setNewTopic] = useState('');
  const [expandingId, setExpandingId] = useState<string | null>(null);

  // 归因报告
  const [insightProduct, setInsightProduct] = useState('');
  const [insight, setInsight] = useState<InsightReport | null>(null);
  const [insightLoading, setInsightLoading] = useState(false);

  // AI 试算
  const [tryText, setTryText] = useState('');
  const [tryProduct, setTryProduct] = useState('');
  const [tryResult, setTryResult] = useState<AnalysisResult | null>(null);
  const [tryLoading, setTryLoading] = useState(false);

  const showToast = (message: string, type: 'success' | 'error') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  // ===== 数据加载 =====
  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const params: Record<string, string | number> = { limit: 20, page: currentPage };
      if (filters.keyword) params.keyword = filters.keyword;
      if (filters.source) params.source = filters.source;
      if (filters.sentiment) params.sentiment = filters.sentiment;
      if (filters.urgency) params.urgency = filters.urgency;
      if (filters.productLine) params.productLine = filters.productLine;
      if (filters.topicId) params.topicId = filters.topicId;
      if (filters.timeRange) params.timeRange = filters.timeRange;
      if (filters.pendingReview) params.pendingReview = filters.pendingReview;
      if (filters.sortBy) params.sortBy = filters.sortBy;
      if (filters.sortOrder) params.sortOrder = filters.sortOrder;

      const [topicsData, feedbacksData, statsData, alertData] = await Promise.all([
        topicsApi.getAll(),
        feedbacksApi.getAll(params as unknown as FeedbackQuery),
        feedbacksApi.getStats(),
        alertsApi.getAll({ limit: 20 })
      ]);

      setTopics(topicsData);
      setFeedbacks(feedbacksData.data);
      setTotalPages(feedbacksData.pagination.totalPages);
      setStats(statsData);
      setAlerts(alertData.data);
      setUnreadCount(alertData.unreadCount);

      const active = topicsData.filter(t => t.isActive).map(t => t.text);
      if (active.length > 0) subscribeToTopics(active);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setIsLoading(false);
    }
  }, [filters, currentPage]);

  useEffect(() => {
    setCurrentPage(1);
  }, [filters]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    const unsubFeedback = onNewFeedback(() => {
      showToast('新增客户反馈', 'success');
      loadData();
    });
    const unsubAlert = onAlert((al) => {
      setUnreadCount(prev => prev + 1);
      showToast('新预警: ' + al.title, 'error');
      loadData();
    });
    return () => {
      unsubFeedback();
      unsubAlert();
    };
  }, [loadData]);

  // ===== 主题词 =====
  const handleAddTopic = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTopic.trim()) return;
    try {
      const topic = await topicsApi.create({ text: newTopic.trim() });
      setTopics(prev => [topic, ...prev]);
      setNewTopic('');
      showToast('主题词已添加', 'success');
    } catch (error: any) {
      showToast(error.message || '添加失败', 'error');
    }
  };

  const handleDeleteTopic = async (id: string) => {
    try {
      await topicsApi.delete(id);
      setTopics(prev => prev.filter(t => t.id !== id));
      showToast('已删除', 'success');
    } catch {
      showToast('删除失败', 'error');
    }
  };

  const handleToggleTopic = async (id: string) => {
    try {
      const updated = await topicsApi.toggle(id);
      setTopics(prev => prev.map(t => (t.id === id ? updated : t)));
    } catch {
      showToast('操作失败', 'error');
    }
  };

  const handleExpandTopic = async (id: string) => {
    setExpandingId(id);
    try {
      const { created } = await topicsApi.expand(id);
      setTopics(await topicsApi.getAll());
      showToast(`已生成 ${created.length} 个候选变体，待确认`, 'success');
    } catch {
      showToast('扩展失败', 'error');
    } finally {
      setExpandingId(null);
    }
  };

  const handleApproveTopic = async (id: string, approved: boolean) => {
    try {
      await topicsApi.approve(id, approved);
      setTopics(await topicsApi.getAll());
    } catch {
      showToast('操作失败', 'error');
    }
  };

  // ===== 预警 =====
  const handleMarkAllRead = async () => {
    try {
      await alertsApi.markAllAsRead();
      setUnreadCount(0);
      setAlerts(prev => prev.map(a => ({ ...a, isRead: true })));
    } catch {
      console.error('Failed to mark as read');
    }
  };

  const handleAlert = async (id: string) => {
    try {
      await alertsApi.handle(id);
      setAlerts(prev => prev.map(a => (a.id === id ? { ...a, handled: true, isRead: true } : a)));
      showToast('已标记处置', 'success');
    } catch {
      showToast('操作失败', 'error');
    }
  };

  // ===== 触发 =====
  const handleManualCheck = async () => {
    setIsChecking(true);
    try {
      const res = await triggerInsightCheck();
      // 异步分析：新数据会通过实时推送逐条到达，定时刷新仅作兜底
      showToast(
        res.queued != null ? `已投递 ${res.queued} 个分析任务，结果将陆续到达` : '分析任务已触发',
        'success'
      );
      setTimeout(loadData, 4000);
    } catch {
      showToast('触发失败', 'error');
    } finally {
      setIsChecking(false);
    }
  };

  const handleGenerateDemo = async () => {
    setIsChecking(true);
    try {
      const res = await feedbacksApi.generateDemo(60);
      showToast(`已生成 ${res.created} 条反馈`, 'success');
      loadData();
    } catch {
      showToast('生成失败', 'error');
    } finally {
      setIsChecking(false);
    }
  };

  const handleReview = async (id: string, sentiment: string) => {
    try {
      await feedbacksApi.review(id, { sentiment });
      setFeedbacks(prev => prev.map(f => (f.id === id ? { ...f, sentiment, isReviewed: true } : f)));
      showToast('已提交人工复核', 'success');
    } catch {
      showToast('复核失败', 'error');
    }
  };

  // ===== 归因报告 =====
  const loadInsight = useCallback(async () => {
    setInsightLoading(true);
    try {
      setInsight(await feedbacksApi.getInsight(insightProduct || undefined));
    } catch {
      showToast('报告生成失败', 'error');
    } finally {
      setInsightLoading(false);
    }
  }, [insightProduct]);

  useEffect(() => {
    if (activeTab === 'insight') loadInsight();
  }, [activeTab, loadInsight]);

  // ===== AI 试算 =====
  const handleTry = async () => {
    if (!tryText.trim()) return;
    setTryLoading(true);
    try {
      setTryResult(await feedbacksApi.analyze({
        content: tryText.trim(),
        productLine: tryProduct || undefined
      }));
    } catch {
      showToast('分析失败', 'error');
    } finally {
      setTryLoading(false);
    }
  };

  const maxTopicCount = useMemo(
    () => (insight && insight.topTopics.length ? insight.topTopics[0].count : 1),
    [insight]
  );

  // ===== 顶栏操作 =====
  const actions = (
    <>
      <button
        onClick={handleGenerateDemo}
        disabled={isChecking}
        className="h-8 px-3 rounded-md border border-[#e3e8ef] bg-white text-[13px] font-medium text-slate-600 hover:border-blue-400 hover:text-blue-600 transition-colors disabled:opacity-50"
      >
        生成演示数据
      </button>
      <button
        onClick={handleManualCheck}
        disabled={isChecking}
        className="h-8 px-3 rounded-md bg-blue-600 text-white text-[13px] font-medium hover:bg-blue-700 inline-flex items-center gap-1.5 transition-colors disabled:opacity-60"
      >
        <RefreshCw className={cn("w-3.5 h-3.5", isChecking && "animate-spin")} />
        {isChecking ? '分析中' : '立即分析'}
      </button>

      <div className="relative">
        <button
          onClick={() => setShowAlerts(!showAlerts)}
          className="relative w-8 h-8 rounded-md border border-[#e3e8ef] bg-white text-slate-500 hover:border-blue-400 hover:text-blue-600 inline-flex items-center justify-center transition-colors"
        >
          <Bell className="w-4 h-4" />
          {unreadCount > 0 && (
            <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 bg-red-500 rounded-full text-[10px] font-bold text-white flex items-center justify-center">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>

        {showAlerts && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setShowAlerts(false)} />
            <div className="absolute right-0 top-10 z-50 w-[380px] bg-white rounded-lg border border-[#e3e8ef] shadow-lg overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-[#eef2f7]">
                <span className="text-sm font-semibold text-slate-900">预警中心</span>
                {unreadCount > 0 && (
                  <button onClick={handleMarkAllRead} className="text-xs text-blue-600 hover:text-blue-700">
                    全部已读
                  </button>
                )}
              </div>
              <div className="max-h-[380px] overflow-y-auto">
                {alerts.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-10">暂无预警</p>
                ) : (
                  <div className="divide-y divide-[#eef2f7]">
                    {alerts.map(a => (
                      <div key={a.id} className={cn("px-4 py-3", a.isRead && "opacity-60")}>
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-[13px] font-medium text-slate-800">{a.title}</p>
                            <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{a.content}</p>
                          </div>
                          {!a.handled && (
                            <button
                              onClick={() => handleAlert(a.id)}
                              className="shrink-0 h-6 px-2 rounded border border-[#e3e8ef] text-[11px] text-slate-500 hover:border-blue-400 hover:text-blue-600 transition-colors"
                            >
                              处置
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );

  return (
    <>
      <Layout active={activeTab} onNavigate={setActiveTab} actions={actions}>
        {/* ============ 反馈洞察 ============ */}
        {activeTab === 'feedbacks' && (
          <div className="space-y-4">
            {stats && (
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard icon={MessageSquare} label="反馈总量" value={String(stats.total)} hint={`今日新增 ${stats.today}`} />
                <StatCard icon={TrendingDown} label="负面占比" value={`${(stats.negativeRatio * 100).toFixed(1)}%`} hint={`${stats.negative} 条负面`} tone="rose" />
                <StatCard icon={AlertTriangle} label="待处置预警" value={String(stats.pendingAlert)} tone="amber" />
                <StatCard icon={Star} label="平均评分" value={stats.avgRating != null ? String(stats.avgRating) : '—'} hint={`${stats.pendingReview} 条待复核`} tone="emerald" />
              </div>
            )}

            <FilterSortBar filters={filters} onChange={setFilters} topics={topics} />

            <FeedbackTable
              data={feedbacks}
              loading={isLoading}
              page={currentPage}
              totalPages={totalPages}
              onPageChange={setCurrentPage}
              onReview={handleReview}
              topicMap={topicMap}
            />
          </div>
        )}

        {/* ============ 主题词管理 ============ */}
        {activeTab === 'topics' && (
          <div className="space-y-4">
            <div className="bg-white rounded-lg border border-[#e3e8ef] p-4">
              <form onSubmit={handleAddTopic} className="flex gap-3">
                <input
                  value={newTopic}
                  onChange={(e) => setNewTopic(e.target.value)}
                  placeholder="输入业务关注的问题主题，如：理赔时效、销售误导、续保与退保"
                  className="flex-1 h-9 px-3 rounded-md border border-[#e3e8ef] text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
                />
                <button
                  type="submit"
                  className="h-9 px-4 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 inline-flex items-center gap-1.5 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  添加主题词
                </button>
              </form>
              <p className="text-xs text-slate-500 mt-2.5">
                主题词是书面业务术语，而客户实际表达往往是口语（如「拖咗好耐都未賠」）。添加后可点击「AI 扩展」生成口语变体，确认后参与匹配。
              </p>
            </div>

            <div className="bg-white rounded-lg border border-[#e3e8ef] overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#f8fafc] border-b border-[#e3e8ef]">
                    <th className="px-4 py-2.5 text-left text-xs font-semibold text-slate-600">主题词</th>
                    <th className="w-[90px] px-4 py-2.5 text-left text-xs font-semibold text-slate-600">来源</th>
                    <th className="w-[90px] px-4 py-2.5 text-left text-xs font-semibold text-slate-600">命中次数</th>
                    <th className="w-[90px] px-4 py-2.5 text-left text-xs font-semibold text-slate-600">状态</th>
                    <th className="w-[190px] px-4 py-2.5 text-right text-xs font-semibold text-slate-600">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#eef2f7]">
                  {topics.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-16 text-center">
                        <p className="text-sm text-slate-500">还没有主题词</p>
                        <p className="text-xs text-slate-400 mt-1">从「理赔时效」这类核心问题开始</p>
                      </td>
                    </tr>
                  ) : (
                    topics.map(topic => (
                      <tr key={topic.id} className="hover:bg-[#f8fafc] transition-colors">
                        <td className="px-4 py-3">
                          <span className={cn("font-medium", topic.isActive ? "text-slate-900" : "text-slate-400")}>
                            {topic.text}
                          </span>
                          {topic._count && topic._count.feedbacks > 0 && (
                            <span className="ml-2 text-xs text-slate-400">{topic._count.feedbacks} 条反馈</span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          {topic.autoGenerated ? (
                            <span className="inline-block px-2 py-0.5 rounded bg-purple-50 text-purple-700 text-[11px] border border-purple-200">
                              AI 生成
                            </span>
                          ) : (
                            <span className="text-xs text-slate-500">人工维护</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-slate-600">{topic.hitCount}</td>
                        <td className="px-4 py-3">
                          {!topic.approved ? (
                            <span className="inline-block px-2 py-0.5 rounded bg-amber-50 text-amber-700 text-[11px] border border-amber-200">
                              待确认
                            </span>
                          ) : (
                            <span className={cn(
                              "inline-block px-2 py-0.5 rounded text-[11px] border",
                              topic.isActive
                                ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                : "bg-slate-50 text-slate-500 border-slate-200"
                            )}>
                              {topic.isActive ? '已启用' : '已停用'}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-end gap-1.5">
                            {!topic.approved ? (
                              <>
                                <button
                                  onClick={() => handleApproveTopic(topic.id, true)}
                                  className="h-7 px-2.5 rounded border border-emerald-200 bg-emerald-50 text-emerald-700 text-xs font-medium hover:bg-emerald-100 transition-colors"
                                >
                                  确认
                                </button>
                                <button
                                  onClick={() => handleApproveTopic(topic.id, false)}
                                  className="h-7 px-2.5 rounded border border-[#e3e8ef] text-xs text-slate-500 hover:bg-slate-50 transition-colors"
                                >
                                  否决
                                </button>
                              </>
                            ) : (
                              <>
                                <button
                                  onClick={() => handleExpandTopic(topic.id)}
                                  disabled={expandingId === topic.id}
                                  className="h-7 px-2.5 rounded border border-[#e3e8ef] text-xs text-slate-600 hover:border-blue-400 hover:text-blue-600 inline-flex items-center gap-1 transition-colors disabled:opacity-50"
                                >
                                  <Sparkles className="w-3 h-3" />
                                  {expandingId === topic.id ? '扩展中' : 'AI 扩展'}
                                </button>
                                <button
                                  onClick={() => handleToggleTopic(topic.id)}
                                  className={cn(
                                    "h-7 px-2.5 rounded border text-xs transition-colors",
                                    topic.isActive
                                      ? "border-[#e3e8ef] text-slate-600 hover:bg-slate-50"
                                      : "border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100"
                                  )}
                                >
                                  {topic.isActive ? '停用' : '启用'}
                                </button>
                              </>
                            )}
                            <button
                              onClick={() => handleDeleteTopic(topic.id)}
                              className="h-7 w-7 rounded border border-[#e3e8ef] text-slate-400 hover:border-red-200 hover:text-red-600 hover:bg-red-50 inline-flex items-center justify-center transition-colors"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ============ 评分归因 ============ */}
        {activeTab === 'insight' && (
          <div className="space-y-4">
            <div className="bg-white rounded-lg border border-[#e3e8ef] p-4 flex flex-wrap items-center gap-3">
              <span className="text-sm text-slate-600">产品线</span>
              <select
                value={insightProduct}
                onChange={(e) => setInsightProduct(e.target.value)}
                className="h-8 pl-2.5 pr-7 rounded-md border border-[#e3e8ef] bg-white text-[13px] text-slate-700 focus:outline-none focus:border-blue-500 appearance-none bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2216%22 height=%2216%22 fill=%22none%22 stroke=%22%2394a3b8%22 stroke-width=%222%22%3E%3Cpath d=%22M4 6l4 4 4-4%22/%3E%3C/svg%3E')] bg-no-repeat bg-[right_0.4rem_center] cursor-pointer"
              >
                <option value="">全部产品线</option>
                {PRODUCT_LINES.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
              </select>
              <button
                onClick={loadInsight}
                disabled={insightLoading}
                className="h-8 px-3.5 rounded-md bg-blue-600 text-white text-[13px] font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
              >
                {insightLoading ? '生成中…' : '生成报告'}
              </button>
            </div>

            {insightLoading ? (
              <div className="bg-white rounded-lg border border-[#e3e8ef] py-20 flex justify-center">
                <div className="w-7 h-7 border-2 border-blue-100 border-t-blue-600 rounded-full animate-spin" />
              </div>
            ) : !insight ? (
              <div className="bg-white rounded-lg border border-[#e3e8ef] py-20 text-center">
                <p className="text-sm text-slate-500">选择产品线后生成归因报告</p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                  <StatCard icon={MessageSquare} label="反馈总数" value={String(insight.totalFeedback)} />
                  <StatCard icon={Star} label="平均评分" value={insight.avgRating != null ? String(insight.avgRating) : '—'} tone="emerald" />
                  <StatCard icon={TrendingDown} label="负面占比" value={`${(insight.negativeRatio * 100).toFixed(1)}%`} tone="rose" />
                  <StatCard icon={Target} label="问题主题数" value={String(insight.topTopics.length)} />
                </div>

                <div className="bg-white rounded-lg border border-[#e3e8ef] p-5">
                  <h3 className="text-sm font-semibold text-slate-900 mb-4">主题分布</h3>
                  {insight.topTopics.length === 0 ? (
                    <p className="text-sm text-slate-400">暂无主题数据（需配置 AI 服务后重新分析）</p>
                  ) : (
                    <div className="space-y-3">
                      {insight.topTopics.slice(0, 8).map(t => (
                        <div key={t.topic}>
                          <div className="flex items-center justify-between text-xs mb-1.5">
                            <span className="text-slate-700 font-medium">{t.topic}</span>
                            <span className="text-slate-400">{t.count} 条 · 负面 {t.negativeCount}</span>
                          </div>
                          <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                            <div
                              className="h-full rounded-full bg-blue-600"
                              style={{ width: `${Math.max(3, (t.count / maxTopicCount) * 100)}%` }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="bg-white rounded-lg border border-[#e3e8ef] p-5">
                  <h3 className="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
                    <Sparkles className="w-4 h-4 text-blue-600" />
                    AI 归因结论
                  </h3>
                  <p className="text-sm text-slate-700 leading-relaxed">{insight.summary}</p>
                </div>

                {insight.suggestions.length > 0 && (
                  <div className="bg-white rounded-lg border border-[#e3e8ef] p-5">
                    <h3 className="text-sm font-semibold text-slate-900 mb-3">改进建议</h3>
                    <ol className="space-y-2.5">
                      {insight.suggestions.map((s, i) => (
                        <li key={i} className="flex gap-2.5 text-sm text-slate-700 leading-relaxed">
                          <span className="shrink-0 w-5 h-5 rounded-full bg-blue-50 text-blue-700 text-[11px] font-semibold flex items-center justify-center mt-0.5">
                            {i + 1}
                          </span>
                          {s}
                        </li>
                      ))}
                    </ol>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* ============ AI 试算 ============ */}
        {activeTab === 'playground' && (
          <div className="space-y-4">
            <div className="bg-white rounded-lg border border-[#e3e8ef] p-5">
              <h3 className="text-sm font-semibold text-slate-900 mb-1">单条文本即时分析</h3>
              <p className="text-xs text-slate-500 mb-4">
                验证 AI 标注效果，不落库。支持繁体中文、粤语口语、英文与中英混排。
              </p>

              <textarea
                value={tryText}
                onChange={(e) => setTryText(e.target.value)}
                rows={4}
                placeholder="粘贴一段客户反馈…"
                className="w-full px-3 py-2.5 rounded-md border border-[#e3e8ef] text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 resize-none"
              />

              <div className="flex flex-wrap items-center gap-3 mt-3">
                <select
                  value={tryProduct}
                  onChange={(e) => setTryProduct(e.target.value)}
                  className="h-8 pl-2.5 pr-7 rounded-md border border-[#e3e8ef] bg-white text-[13px] text-slate-700 focus:outline-none focus:border-blue-500 appearance-none bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2216%22 height=%2216%22 fill=%22none%22 stroke=%22%2394a3b8%22 stroke-width=%222%22%3E%3Cpath d=%22M4 6l4 4 4-4%22/%3E%3C/svg%3E')] bg-no-repeat bg-[right_0.4rem_center] cursor-pointer"
                >
                  <option value="">不限产品线</option>
                  {PRODUCT_LINES.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
                </select>
                <button
                  onClick={handleTry}
                  disabled={tryLoading || !tryText.trim()}
                  className="h-8 px-4 rounded-md bg-blue-600 text-white text-[13px] font-medium hover:bg-blue-700 transition-colors disabled:opacity-50"
                >
                  {tryLoading ? '分析中…' : '分析'}
                </button>
              </div>

              <div className="mt-4 pt-4 border-t border-[#eef2f7]">
                <div className="text-xs text-slate-500 mb-2">示例文本</div>
                <div className="flex flex-wrap gap-2">
                  {SAMPLE_TEXTS.map(s => (
                    <button
                      key={s}
                      onClick={() => setTryText(s)}
                      className="px-2.5 py-1 rounded border border-[#e3e8ef] text-xs text-slate-500 hover:border-blue-400 hover:text-blue-600 transition-colors text-left"
                    >
                      {s.length > 20 ? s.slice(0, 20) + '…' : s}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {tryResult && (
              <div className="bg-white rounded-lg border border-[#e3e8ef] p-5">
                <h3 className="text-sm font-semibold text-slate-900 mb-4">分析结果</h3>
                <dl className="divide-y divide-[#eef2f7]">
                  <ResultRow label="情感倾向" value={SENTIMENT_LABELS[tryResult.sentiment] ?? tryResult.sentiment} />
                  <ResultRow label="紧急度" value={URGENCY_LABELS[tryResult.urgency] ?? tryResult.urgency} />
                  <ResultRow label="主题标签" value={tryResult.topics.length ? tryResult.topics.join('、') : '—'} />
                  <ResultRow label="AI 归因" value={tryResult.aiSummary} />
                  <ResultRow label="定级理由" value={tryResult.urgencyReason} />
                  <ResultRow label="置信度" value={`${(tryResult.confidence * 100).toFixed(0)}%`} />
                </dl>
              </div>
            )}
          </div>
        )}
      </Layout>

      {/* Toast */}
      {toast && (
        <div className="fixed top-4 right-4 z-[60] flex items-center gap-2 px-4 py-2.5 rounded-lg bg-white border border-[#e3e8ef] shadow-lg">
          {toast.type === 'success'
            ? <Check className="w-4 h-4 text-emerald-600" />
            : <X className="w-4 h-4 text-red-600" />}
          <span className="text-[13px] text-slate-700">{toast.message}</span>
        </div>
      )}
    </>
  );
}

const SENTIMENT_LABELS: Record<string, string> = {
  positive: '正面', neutral: '中性', negative: '负面'
};

const URGENCY_LABELS: Record<string, string> = {
  critical: '紧急', action: '需处理', attention: '需关注', info: '一般'
};

const TONE_ICON: Record<string, string> = {
  default: 'text-slate-400',
  blue: 'text-blue-600',
  rose: 'text-rose-600',
  amber: 'text-amber-600',
  emerald: 'text-emerald-600'
};

function StatCard({
  icon: Icon,
  label,
  value,
  hint,
  tone = 'default'
}: {
  icon: React.ElementType;
  label: string;
  value: string;
  hint?: string;
  tone?: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-[#e3e8ef] p-4">
      <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-2">
        <Icon className={cn("w-3.5 h-3.5", TONE_ICON[tone] ?? TONE_ICON.default)} />
        {label}
      </div>
      <div className="text-2xl font-semibold text-slate-900 leading-tight">{value}</div>
      {hint && <div className="text-[11px] text-slate-400 mt-1">{hint}</div>}
    </div>
  );
}

function ResultRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-4 py-2.5">
      <dt className="w-[80px] shrink-0 text-xs text-slate-500 pt-0.5">{label}</dt>
      <dd className="flex-1 text-sm text-slate-700 leading-relaxed">{value}</dd>
    </div>
  );
}

const SAMPLE_TEXTS = [
  '理賠拖咗三個星期都未批，打電話又無人聽，好失望',
  'Claim rejected without any clear explanation. I will escalate to the Insurance Authority.',
  '當初 agent 話全保，原來一堆除外責任，講一套做一套',
  '客服阿 May 解釋得好清楚，好有耐性，讚',
  '續保保費加咗三成，事前完全無通知'
];

export default App;
