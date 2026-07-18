import { useEffect, useState } from 'react';
import { clearRequestLogs, getRequestLogs } from '../api';

type RequestLog = {
  id: string;
  time: string;
  level: 'info' | 'error';
  method: string;
  url: string;
  path: string;
  status: number;
  durationMs?: number;
  clientIp?: string;
  userAgent?: string;
  contentType?: string;
  range?: string;
  query?: unknown;
  body?: unknown;
  response?: unknown;
  error?: unknown;
};

type LogsResponse = {
  code: number;
  data?: {
    total: number;
    logs: RequestLog[];
  };
};

const formatJson = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '无';
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
};

export default function RequestLogs() {
  const [logs, setLogs] = useState<RequestLog[]>([]);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [loading, setLoading] = useState(false);

  const refreshLogs = async () => {
    setLoading(true);
    const res = await getRequestLogs(120);
    if (res.ok) {
      const data = res.data as LogsResponse;
      setLogs(data.data?.logs || []);
    }
    setLoading(false);
  };

  const handleClear = async () => {
    await clearRequestLogs();
    setLogs([]);
  };

  useEffect(() => {
    refreshLogs();
  }, []);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(refreshLogs, 2000);
    return () => window.clearInterval(timer);
  }, [autoRefresh]);

  return (
    <div className="card">
      <div className="card-header">
        🧾 请求日志 <span className="badge">手机 / PC 访问记录</span>
      </div>
      <div className="card-body">
        <div className="log-toolbar">
          <button onClick={refreshLogs} disabled={loading}>{loading ? '刷新中...' : '刷新'}</button>
          <button className="secondary" onClick={handleClear}>清空日志</button>
          <label className="log-toggle">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
            />
            自动刷新
          </label>
        </div>

        <div className="log-list">
          {logs.length === 0 && (
            <div className="empty-state">
              <div className="empty-title">暂无请求日志</div>
              <div className="empty-desc">从手机或浏览器访问接口后，这里会显示请求与错误信息</div>
            </div>
          )}

          {logs.map((log) => (
            <details key={log.id} className={`log-item ${log.level === 'error' ? 'error' : ''}`}>
              <summary>
                <span className={`log-method method-${log.method.toLowerCase()}`}>{log.method}</span>
                <span className={`log-status ${log.status >= 400 ? 'error' : ''}`}>{log.status}</span>
                <span className="log-url">{log.url}</span>
                <span className="log-meta">{new Date(log.time).toLocaleTimeString()} · {log.durationMs ?? '-'}ms</span>
              </summary>

              <div className="log-detail-grid">
                <div>
                  <label>来源</label>
                  <code>{log.clientIp || '-'}</code>
                </div>
                <div>
                  <label>Content-Type</label>
                  <code>{log.contentType || '-'}</code>
                </div>
                <div>
                  <label>Range</label>
                  <code>{log.range || '-'}</code>
                </div>
                <div>
                  <label>User-Agent</label>
                  <code>{log.userAgent || '-'}</code>
                </div>
              </div>

              <div className="log-payload-grid">
                <div>
                  <label>Query</label>
                  <pre>{formatJson(log.query)}</pre>
                </div>
                <div>
                  <label>Body</label>
                  <pre>{formatJson(log.body)}</pre>
                </div>
                {(log.response !== undefined || log.error !== undefined) && (
                  <div>
                    <label>错误 / 响应</label>
                    <pre>{formatJson(log.error || log.response)}</pre>
                  </div>
                )}
              </div>
            </details>
          ))}
        </div>
      </div>
    </div>
  );
}
