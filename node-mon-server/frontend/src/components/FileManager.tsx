import { useEffect, useState } from 'react';
import { getFileList, getDownloadUrl, deleteFile } from '../api';
import { fileTypeInfo, formatSize, formatTime, buildDownloadUrl, copyToClipboard } from '../utils/format';
import { useExampleModal } from '../hooks/useExampleModal';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function FileManager() {
  const [files, setFiles] = useState<{ name: string; size: number; modified: string }[]>([]);
  const [downloadFilename, setDownloadFilename] = useState('');
  const { exampleKey, showExample, closeExample } = useExampleModal();
  const [copiedFile, setCopiedFile] = useState<string | null>(null);

  const refreshFiles = async () => {
    const res = await getFileList();
    if (res.ok && (res.data as any).files) setFiles((res.data as any).files);
    else setFiles([]);
  };

  useEffect(() => { refreshFiles(); }, []);

  const handleDirectDownload = () => {
    if (!downloadFilename) { alert('请输入文件名'); return; }
    window.open(getDownloadUrl(downloadFilename), '_blank');
  };

  const handleCopy = async (filename: string) => {
    await copyToClipboard(buildDownloadUrl(filename));
    setCopiedFile(filename);
    setTimeout(() => setCopiedFile(null), 1500);
  };

  const handleDelete = async (filename: string) => {
    if (!confirm(`确定删除 "${filename}" 吗？`)) return;
    const res = await deleteFile(filename);
    if (res.ok) {
      setFiles(prev => prev.filter(f => f.name !== filename));
    } else {
      const d = res.data as any;
      alert(`删除失败: ${d?.message || String(d)}`);
    }
  };


  return (
    <div className="card">
      <div className="card-header">
        <span>📁 文件管理</span>
        <span className="badge">/download/*</span>
        <span style={{ marginLeft: 'auto', fontSize: '0.8rem', color: '#64748b' }}>
          {files.length} 个文件
        </span>
        <button className="example-btn" onClick={() => showExample('download')}>📱 示例</button>
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
              const meta = fileTypeInfo(file.name);
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
                      <code className="file-url-text">{buildDownloadUrl(file.name)}</code>
                      <button
                        className="file-copy-btn"
                        onClick={(e) => { e.preventDefault(); handleCopy(file.name); }}
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
          onClose={closeExample}
        />
      )}
    </div>
  );
}
