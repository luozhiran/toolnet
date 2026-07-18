import { useEffect, useState } from 'react';
import { getFileList, getDownloadUrl } from '../api';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function FileManager() {
  const [files, setFiles] = useState([]);
  const [downloadFilename, setDownloadFilename] = useState('');
  const [exampleKey, setExampleKey] = useState(null);

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

  const showModal = (key) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  return (
    <div className="card">
      <div className="card-header">
        📁 文件管理 <span className="badge">/download/*</span>
        <button className="example-btn" onClick={() => showModal('download')}>📱 示例</button>
      </div>
      <div className="card-body">
        <button className="secondary" onClick={refreshFiles}>🔄 刷新文件列表</button>
        <div className="file-list">
          {files.length === 0 ? (
            <div style={{ textAlign: 'center', color: '#64748b' }}>暂无文件，请先上传</div>
          ) : (
            files.map(file => (
              <div key={file.name} className="file-item">
                <div>
                  <div className="file-name">{file.name}</div>
                  <div className="file-size">{(file.size / 1024).toFixed(1)} KB · {new Date(file.modified).toLocaleString()}</div>
                </div>
                <a href={getDownloadUrl(file.name)} className="download-link" target="_blank" rel="noreferrer">下载</a>
              </div>
            ))
          )}
        </div>
        <hr />
        <div className="form-group">
          <label>直接下载 (输入文件名)</label>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input type="text" placeholder="例如: 1705314600000-123-test.png" value={downloadFilename} onChange={(e) => setDownloadFilename(e.target.value)} />
            <button onClick={handleDirectDownload}>下载</button>
          </div>
        </div>
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