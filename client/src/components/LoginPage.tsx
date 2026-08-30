import { useState } from 'react';
import { Lock, User, Loader2 } from 'lucide-react';
import { authApi } from '../services/api';
import { saveAuth } from '../services/auth';

/**
 * 登录页。
 * 登录成功后保存 token 与用户信息，并回调父组件切换到主界面。
 */
export default function LoginPage({ onSuccess }: { onSuccess: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) return;
    setLoading(true);
    setError('');
    try {
      const res = await authApi.login(username.trim(), password);
      saveAuth(res.token, { username: res.username, role: res.role });
      onSuccess();
    } catch (err: any) {
      setError(err.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#f5f7fa] px-4">
      <div className="w-full max-w-[380px]">
        <div className="text-center mb-8">
          <h1 className="text-xl font-semibold text-slate-900">VoC Insight</h1>
          <p className="text-sm text-slate-500 mt-1.5">保险客户声音智能分析平台</p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-lg border border-[#e3e8ef] p-6 space-y-4"
        >
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">用户名</label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                autoComplete="username"
                className="w-full h-10 pl-9 pr-3 rounded-md border border-[#e3e8ef] text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">密码</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                autoComplete="current-password"
                className="w-full h-10 pl-9 pr-3 rounded-md border border-[#e3e8ef] text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20"
              />
            </div>
          </div>

          {error && <p className="text-xs text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={loading || !username.trim() || !password}
            className="w-full h-10 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-50 inline-flex items-center justify-center gap-2"
          >
            {loading && <Loader2 className="w-4 h-4 animate-spin" />}
            {loading ? '登录中…' : '登录'}
          </button>

          <p className="text-[11px] text-slate-400 text-center pt-1">
            默认账号：admin / admin123（管理员）· viewer / viewer123（只读）
          </p>
        </form>
      </div>
    </div>
  );
}
