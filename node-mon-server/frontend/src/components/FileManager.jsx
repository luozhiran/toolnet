import { useEffect, useState } from 'react';
import { getFileList, getDownloadUrl, deleteFile } from '../api';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

// 根据扩展名返回图标和颜色
function fileMeta(filename) {
  const ext = (filename.split('.').pop() || '').toLowerCase();
  const map = {
    png:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    jpg:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    jpeg: { icon: '🖼️', color: '#f59e0b', label: '图片' },
    gif:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    svg:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    webp: { icon: '🖼️', color: '#f59e0b', label: '图片' },
    pdf:  { icon: '📕', color: '#ef4444', label: 'PDF' },
    zip:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    rar:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    '7z': { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    tar:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    gz:   { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    mp4:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    mov:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    avi:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    mp3:  { icon: '🎵', color: '#10b981', label: '音频' },
    wav:  { icon: '🎵', color: '#10b981', label: '音频' },
    json: { icon: '📋', color: '#6366f1', label: 'JSON' },
    xml:  { icon: '📋', color: '#6366f1', label: 'XML' },
    txt:  { icon: '📄', color: '#64748b', label: '文本' },
    log:  { icon: '📄', color: '#64748b', label: '日志' },
    md:   { icon: '📝', color: '#64748b', label: 'Markdown' },
    js:   { icon: '💛', color: '#eab308', label: 'JS' },
    ts:   { icon: '💙', color: '#3b82f6', label: 'TS' },
    html: { icon: '🌐', color: '#f97316', label: 'HTML' },
    css:  { icon: '🎨', color: '#06b6d4', label: 'CSS' },
  };
  return map[ext] || { icon: '📎', color: '#94a3b8', label: ext || '文件' };
}

// 智能文件大小格式化
function formatSize(bytes) {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

// 相对时间
function formatTime(dateStr) {
  const now = Date.now();
  const diff = now - new Date(dateStr).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return new Date(dateStr).toLocaleDateString('zh-CN');
}

export default function FileManager() {
  const [files, setFiles] = useState([]);
  const [downloadFilename, setDownloadFilename] = useState('');
  const [exampleKey, setExampleKey] = useState(null);
  const [copiedFile, setCopiedFile] = useState(null);

  const refreshFiles = async () => {
    const res = await getFileList();
    if (res.ok && res.data.files) setFiles(res.data.files);
    else setFiles([]);
  };

  useEffect(() => { refreshFiles(); }, []);

  const handleDirectDownload = () => {
    if (!downloadFilename) { alert('请输入文件名'); return; }
    window.open(getDownloadUrl(downloadFilename), '_blank');
  };

  const fullDownloadUrl = (filename) =>
    window.location.origin + getDownloadUrl(filename);

  const copyUrl = async (filename) => {
    try {
      await navigator.clipboard.writeText(fullDownloadUrl(filename));
      setCopiedFile(filename);
      setTimeout(() => setCopiedFile(null), 1500);
    } catch {
      // 回退：用传统方法
      const ta = document.createElement('textarea');
      ta.value = fullDownloadUrl(filename);
      ta.style.position = 'fixed'; ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      setCopiedFile(filename);
      setTimeout(() => setCopiedFile(null), 1500);
    }
  };

  const handleDelete = async (filename) => {
    if (!confirm(`确定删除 "${filename}" 吗？`)) return;
    const res = await deleteFile(filename);
    if (res.ok) {
      setFiles(prev => prev.filter(f => f.name !== filename));
    } else {
      alert(`删除失败: ${res.data?.message || res.data}`);
    }
  };

  const showModal = (key) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  return (
    <div className="card">
      <div className="card-header">
        <span>📁 文件管理</span>
        <span className="badge">/download/*</span>
        <span style={{ marginLeft: 'auto', fontSize: '0.8rem', color: '#64748b' }}>
          {files.length} 个文件
        </span>
        <button className="example-btn" onClick={() => showModal('download')}>📱 示例</button>
      </div>
      <div className="card-body">
        {/* 工具栏 */}
        <div className="file-toolbar">
          <button className="secondary" onClick={refreshFiles}>🔄 刷新列表</button>
          <div className="download-inline">
            <input
              type="text"
              placeholder="输入文件名直接下载..."
              value={downloadFilename}
              onChange={(e) => setDownloadFilename(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleDirectDownload()}
            />
            <button onClick={handleDirectDownload}>⬇ 下载</button>
          </div>
        </div>

        {/* 统计信息 */}
        {files.length > 0 && (
          <div className="file-stats">
            <div className="file-stat-item">
              <span className="file-stat-num">{files.length}</span>
              <span className="file-stat-label">个文件</span>
            </div>
            <div className="file-stat-divider" />
            <div className="file-stat-item">
              <span className="file-stat-num">{formatSize(files.reduce((s, f) => s + f.size, 0))}</span>
              <span className="file-stat-label">总大小</span>
            </div>
          </div>
        )}

        {/* 文件列表 */}
        {files.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📭</div>
            <div className="empty-title">暂无文件</div>
            <div className="empty-desc">上传文件后这里会显示可下载的文件列表</div>
          </div>
        ) : (
          <div className="file-grid">
            {files.map(file => {
              const meta = fileMeta(file.name);
              return (
                <div key={file.name} className="file-row">
                  <div className="file-icon-col" style={{ backgroundColor: meta.color + '18' }}>
                    <span className="file-icon-emoji">{meta.icon}</span>
                  </div>
                  <div className="file-info-col">
                    <div className="file-name-text" title={file.name}>{file.name}</div>
                    <div className="file-meta-row">
                      <span className="file-type-tag" style={{ backgroundColor: meta.color + '22', color: meta.color }}>{meta.label}</span>
                      <span>{formatSize(file.size)}</span>
                      <span>·</span>
                      <span>{formatTime(file.modified)}</span>
                    </div>
                    <div className="file-url-row">
                      <code className="file-url-text">{fullDownloadUrl(file.name)}</code>
                      <button
                        className="file-copy-btn"
                        onClick={(e) => { e.preventDefault(); copyUrl(file.name); }}
                      >
                        {copiedFile === file.name ? '✅ 已复制' : '📋 复制'}
                      </button>
                    </div>
                  </div>
                  <div className="file-actions">
                    <a
                      href={getDownloadUrl(file.name)}
                      className="file-dl-btn"
                      target="_blank"
                      rel="noreferrer"
                      title="下载文件"
                    >
                      ⬇
                    </a>
                    <button
                      className="file-del-btn"
                      onClick={() => handleDelete(file.name)}
                      title="删除文件"
                    >
                      🗑
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {exampleKey && (
        <ExampleModal
          title={examples[exampleKey]?.title || '示例'}
          code={examples[exampleKey]?.code || '示例代码未找到'}
          onClose={closeModal}
        />
      )}
    </div>
  );
}
