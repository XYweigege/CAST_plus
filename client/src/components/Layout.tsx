import type { ReactNode } from 'react';
import { MessageSquare, Target, TrendingUp, Sparkles, Shield } from 'lucide-react';
import { cn } from '../lib/utils';

export type TabKey = 'feedbacks' | 'topics' | 'insight' | 'playground';

export const NAV_ITEMS: { key: TabKey; label: string; icon: typeof MessageSquare; desc: string }[] = [
  { key: 'feedbacks', label: '反馈洞察', icon: MessageSquare, desc: '多源客户反馈的统一视图，AI 自动标注情感、主题与紧急度' },
  { key: 'topics', label: '主题词管理', icon: Target, desc: '维护业务关注的问题主题，AI 可扩展为客户口语表达变体' },
  { key: 'insight', label: '评分归因', icon: TrendingUp, desc: '分析产品满意度构成，定位导致评分下滑的具体业务环节' },
  { key: 'playground', label: 'AI 试算', icon: Sparkles, desc: '对单条文本即时分析，用于验证与调试标注效果' }
];

interface LayoutProps {
  active: TabKey;
  onNavigate: (tab: TabKey) => void;
  actions?: ReactNode;
  children: ReactNode;
}

export default function Layout({ active, onNavigate, actions, children }: LayoutProps) {
  const current = NAV_ITEMS.find(i => i.key === active)!;

  return (
    <div className="min-h-screen flex bg-[#f4f6f9]">
      {/* ===== 侧边栏 ===== */}
      <aside className="w-[220px] shrink-0 bg-[#0f172a] flex flex-col fixed inset-y-0 left-0 z-30">
        {/* Logo */}
        <div className="h-16 flex items-center gap-2.5 px-5 border-b border-white/10">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center shrink-0">
            <Shield className="w-4.5 h-4.5 text-white" strokeWidth={2.5} />
          </div>
          <div className="min-w-0">
            <div className="text-[15px] font-semibold text-white leading-tight">VoC Insight</div>
            <div className="text-[11px] text-slate-400 leading-tight">客户声音分析平台</div>
          </div>
        </div>

        {/* 导航 */}
        <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
          {NAV_ITEMS.map(({ key, label, icon: Icon }) => {
            const isActive = key === active;
            return (
              <button
                key={key}
                onClick={() => onNavigate(key)}
                className={cn(
                  "w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-left",
                  isActive
                    ? "bg-blue-600 text-white"
                    : "text-slate-300 hover:bg-white/10 hover:text-white"
                )}
              >
                <Icon className="w-4.5 h-4.5 shrink-0" />
                {label}
              </button>
            );
          })}
        </nav>

        {/* 底部说明 */}
        <div className="px-5 py-4 border-t border-white/10">
          <div className="text-[11px] text-slate-500 leading-relaxed">
            AI 标注结果仅供参考
            <br />
            不构成业务或合规结论
          </div>
        </div>
      </aside>

      {/* ===== 主区域 ===== */}
      <div className="flex-1 ml-[220px] flex flex-col min-w-0">
        {/* 顶栏 */}
        <header className="h-16 bg-white border-b border-[#e3e8ef] flex items-center justify-between px-6 sticky top-0 z-20">
          <div className="min-w-0">
            <h1 className="text-[15px] font-semibold text-slate-900 leading-tight">{current.label}</h1>
            <p className="text-xs text-slate-500 leading-tight truncate">{current.desc}</p>
          </div>
          {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
        </header>

        {/* 内容 */}
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
