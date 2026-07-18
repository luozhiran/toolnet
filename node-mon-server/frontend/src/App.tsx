import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import ApiTest from './components/ApiTest';
import FileUpload from './components/FileUpload';
import MixedUpload from './components/MixedUpload';
import FileManager from './components/FileManager';
import ChunkDownload from './components/ChunkDownload';

interface TabChild {
  key: string;
  icon: string;
  label: string;
  desc: string;
  component: string;
  mode: string;
}

interface Tab {
  key: string;
  icon: string;
  label: string;
  desc?: string;
  component?: string;
  children?: TabChild[];
}

const TABS: Tab[] = [
  {
    key: 'api', icon: '📡', label: 'API 测试',
    children: [
      { key: 'api-data',  icon: '📶', label: '获取数据',   desc: 'GET /api/data',       component: 'api', mode: 'data' },
      { key: 'api-echo',  icon: '🔄', label: 'Echo 回显',  desc: 'GET /api/echo',       component: 'api', mode: 'echo' },
      { key: 'api-user',  icon: '👤', label: '用户信息',   desc: 'GET /api/user/:id',   component: 'api', mode: 'user' },
      { key: 'api-json',  icon: '📩', label: '提交 JSON',  desc: 'POST /api/json',      component: 'api', mode: 'json' },
      { key: 'api-form',  icon: '📋', label: '提交表单',   desc: 'POST /api/form',      component: 'api', mode: 'form' },
      { key: 'api-custom', icon: '🛠️', label: '自定义请求', desc: 'GET/POST 自定义',     component: 'api', mode: 'custom' },
    ]
  },
  { key: 'upload-single', icon: '📤', label: '单文件上传',   desc: '/upload/single',      component: 'upload-single' },
  { key: 'upload-multi',  icon: '📚', label: '多文件上传',   desc: '/upload/multiple',    component: 'upload-multi' },
  { key: 'upload-mixed',  icon: '🧬', label: '混合上传',     desc: '/upload/mixed',       component: 'upload-mixed' },
  { key: 'files',         icon: '📁', label: '文件管理',     desc: '/download/*',         component: 'files' },
  { key: 'chunk',         icon: '🔁', label: '断点续传',     desc: '分片下载合并',         component: 'chunk' },
];

// 递归查找所有叶子节点的 key
function findDefaultTab(tabs: Tab[]): string {
  for (const tab of tabs) {
    if (tab.children && tab.children.length > 0) return tab.children[0].key;
    if (!tab.children) return tab.key;
  }
  return 'api-data';
}

function App() {
  const [activeTab, setActiveTab] = useState(() => {
    try { return localStorage.getItem('activeTab') || findDefaultTab(TABS); }
    catch { return findDefaultTab(TABS); }
  });

  // 持久化当前 tab
  const switchTab = (key: string) => {
    setActiveTab(key);
    try { localStorage.setItem('activeTab', key); } catch {}
  };
  const [popoverKey, setPopoverKey] = useState<string | null>(null);
  const [popoverPos, setPopoverPos] = useState({ top: 0 });
  const parentRefs = useRef<Record<string, HTMLDivElement | null>>({});

  // 关闭浮层
  const closePopover = useCallback(() => setPopoverKey(null), []);

  // 点击页面其他地方关闭浮层
  useEffect(() => {
    if (!popoverKey) return;
    const handleClick = (e: MouseEvent) => {
      // 浮层内部的点击不关闭
      if (e.target && (e.target as Element).closest('.sidebar-popover')) return;
      closePopover();
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [popoverKey, closePopover]);

  // 切换浮层
  const togglePopover = (key: string) => {
    if (popoverKey === key) {
      closePopover();
    } else {
      const el = parentRefs.current[key];
      if (el) {
        const rect = el.getBoundingClientRect();
        setPopoverPos({ top: rect.top });
      }
      setPopoverKey(key);
    }
  };

  // 点击子项
  const handleChildClick = (childKey: string) => {
    switchTab(childKey);
    closePopover();
  };

  // 根据 activeTab 找到对应的 tab 定义（可能是子项）
  const findTab = (key: string): Tab | TabChild | null => {
    for (const tab of TABS) {
      if (tab.key === key) return tab;
      if (tab.children) {
        const child = tab.children.find(c => c.key === key);
        if (child) return child;
      }
    }
    return null;
  };

  // 根据 activeTab 算出顶层组件 key（用于显示/隐藏）
  const activeComponentKey = (() => {
    const tab = findTab(activeTab);
    return tab ? (tab.component || tab.key) : 'api';
  })();

  // api 子项激活时传入正确的 mode
  const apiMode = (() => {
    const tab = findTab(activeTab);
    return tab?.component === 'api' ? (tab as TabChild).mode : undefined;
  })();

  const activeParentTab = TABS.find(t => t.children && t.children.some(c => c.key === activeTab));

  // 所有页面都渲染，用 CSS 控制显隐，保活组件状态
  const PAGES = [
    { key: 'api',           node: <ApiTest mode={activeComponentKey === 'api' ? apiMode : undefined} /> },
    { key: 'upload-single', node: <FileUpload mode="single" /> },
    { key: 'upload-multi',  node: <FileUpload mode="multi" /> },
    { key: 'upload-mixed',  node: <MixedUpload /> },
    { key: 'files',         node: <FileManager /> },
    { key: 'chunk',         node: <ChunkDownload /> },
  ];

  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="sidebar-header">🧪 测试服务器</div>
        {TABS.map(tab => {
          const hasChildren = tab.children && tab.children.length > 0;
          const isPopoverOpen = popoverKey === tab.key;
          const isParentActive = activeParentTab?.key === tab.key;

          const item = (
            <div
              key={tab.key}
              ref={(el) => { parentRefs.current[tab.key] = el; }}
              className={`sidebar-item${hasChildren ? ' sidebar-parent' : ''}${isPopoverOpen ? ' popover-open' : ''}${isParentActive && !isPopoverOpen ? ' active' : ''}${!hasChildren && activeTab === tab.key ? ' active' : ''}`}
              onClick={() => hasChildren ? togglePopover(tab.key) : switchTab(tab.key)}
            >
              <span className="sidebar-icon">{tab.icon}</span>
              <div className="sidebar-text">
                <div className="sidebar-label">{tab.label}</div>
                {!hasChildren && <div className="sidebar-desc">{tab.desc}</div>}
              </div>
              {hasChildren && <span className="sidebar-arrow">{isPopoverOpen ? '▼' : '▶'}</span>}
            </div>
          );

          return item;
        })}
        <div className="sidebar-footer">
          <div className="sidebar-tip">
            💡 所有响应均包含调试信息<br />
            🔗 支持跨域 (CORS)<br />
            📱 点击绿色按钮查看 OkHttp 示例
          </div>
        </div>
      </nav>

      {/* 浮层子菜单 — Portal 到 body，fixed 定位 */}
      {popoverKey && createPortal(
        <div
          className="sidebar-popover"
          style={{ top: popoverPos.top }}
          onClick={(e) => e.stopPropagation()}
        >
          {(TABS.find(t => t.key === popoverKey)?.children || []).map(child => (
            <div
              key={child.key}
              className={`sidebar-popover-item ${activeTab === child.key ? 'active' : ''}`}
              onClick={() => handleChildClick(child.key)}
            >
              <span className="sidebar-icon">{child.icon}</span>
              <div className="sidebar-text">
                <div className="sidebar-label">{child.label}</div>
                <div className="sidebar-desc">{child.desc}</div>
              </div>
            </div>
          ))}
        </div>,
        document.body!
      )}

      <main className="main-content">
        {PAGES.map(p => (
          <div key={p.key} className={`tab-page ${activeComponentKey === p.key ? 'active' : ''}`}>
            {p.node}
          </div>
        ))}
      </main>
    </div>
  );
}

export default App;
