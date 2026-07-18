import { useState } from 'react';
import ApiTest from './components/ApiTest';
import FileUpload from './components/FileUpload';
import MixedUpload from './components/MixedUpload';
import FileManager from './components/FileManager';
import ChunkDownload from './components/ChunkDownload';

const TABS = [
  {
    key: 'api', icon: '📡', label: 'API 测试',
    children: [
      { key: 'api-data',  icon: '📶', label: '获取数据',   desc: 'GET /api/data',       component: 'api', mode: 'data' },
      { key: 'api-echo',  icon: '🔄', label: 'Echo 回显',  desc: 'GET /api/echo',       component: 'api', mode: 'echo' },
      { key: 'api-user',  icon: '👤', label: '用户信息',   desc: 'GET /api/user/:id',   component: 'api', mode: 'user' },
      { key: 'api-json',  icon: '📩', label: '提交 JSON',  desc: 'POST /api/json',      component: 'api', mode: 'json' },
      { key: 'api-form',  icon: '📋', label: '提交表单',   desc: 'POST /api/form',      component: 'api', mode: 'form' },
    ]
  },
  { key: 'upload-single', icon: '📤', label: '单文件上传',   desc: '/upload/single',      component: 'upload-single' },
  { key: 'upload-multi',  icon: '📚', label: '多文件上传',   desc: '/upload/multiple',    component: 'upload-multi' },
  { key: 'upload-mixed',  icon: '🧬', label: '混合上传',     desc: '/upload/mixed',       component: 'upload-mixed' },
  { key: 'files',         icon: '📁', label: '文件管理',     desc: '/download/*',         component: 'files' },
  { key: 'chunk',         icon: '🔁', label: '断点续传',     desc: '分片下载合并',         component: 'chunk' },
];

// 递归查找所有叶子节点的 key
function findDefaultTab(tabs) {
  for (const tab of tabs) {
    if (tab.children && tab.children.length > 0) return tab.children[0].key;
    if (!tab.children) return tab.key;
  }
  return 'api-data';
}

function App() {
  const [activeTab, setActiveTab] = useState(findDefaultTab(TABS));
  const [expanded, setExpanded] = useState(new Set(['api']));

  const toggleExpand = (key) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  // 根据 activeTab 找到对应的 tab 定义（可能是子项）
  const findTab = (key) => {
    for (const tab of TABS) {
      if (tab.key === key) return tab;
      if (tab.children) {
        const child = tab.children.find(c => c.key === key);
        if (child) return child;
      }
    }
    return null;
  };

  const renderContent = () => {
    const tab = findTab(activeTab);
    if (!tab) return <ApiTest mode="data" />;
    const comp = tab.component || tab.key;
    switch (comp) {
      case 'api':           return <ApiTest mode={tab.mode} />;
      case 'upload-single': return <FileUpload mode="single" />;
      case 'upload-multi':  return <FileUpload mode="multi" />;
      case 'upload-mixed':  return <MixedUpload />;
      case 'files':         return <FileManager />;
      case 'chunk':         return <ChunkDownload />;
      default:              return <ApiTest mode="data" />;
    }
  };

  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="sidebar-header">🧪 测试服务器</div>
        {TABS.map(tab => {
          const hasChildren = tab.children && tab.children.length > 0;
          const isExpanded = expanded.has(tab.key);
          if (hasChildren) {
            return (
              <div key={tab.key}>
                <div
                  className={`sidebar-item sidebar-parent ${isExpanded ? 'expanded' : ''}`}
                  onClick={() => toggleExpand(tab.key)}
                >
                  <span className="sidebar-icon">{tab.icon}</span>
                  <div className="sidebar-text">
                    <div className="sidebar-label">{tab.label}</div>
                  </div>
                  <span className="sidebar-arrow">{isExpanded ? '▼' : '▶'}</span>
                </div>
                {isExpanded && (
                  <div className="sidebar-children">
                    {tab.children.map(child => (
                      <div
                        key={child.key}
                        className={`sidebar-item sidebar-child ${activeTab === child.key ? 'active' : ''}`}
                        onClick={() => setActiveTab(child.key)}
                      >
                        <span className="sidebar-icon">{child.icon}</span>
                        <div className="sidebar-text">
                          <div className="sidebar-label">{child.label}</div>
                          <div className="sidebar-desc">{child.desc}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          }
          return (
            <div
              key={tab.key}
              className={`sidebar-item ${activeTab === tab.key ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <span className="sidebar-icon">{tab.icon}</span>
              <div className="sidebar-text">
                <div className="sidebar-label">{tab.label}</div>
                <div className="sidebar-desc">{tab.desc}</div>
              </div>
            </div>
          );
        })}
        <div className="sidebar-footer">
          <div className="sidebar-tip">
            💡 所有响应均包含调试信息<br />
            🔗 支持跨域 (CORS)<br />
            📱 点击绿色按钮查看 OkHttp 示例
          </div>
        </div>
      </nav>
      <main className="main-content">
        {renderContent()}
      </main>
    </div>
  );
}

export default App;
