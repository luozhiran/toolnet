import { useState, useEffect } from 'react';
import { getFileList, getDownloadUrl } from '../api';
import { useExampleModal } from '../hooks/useExampleModal';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function ChunkDownload() {
  const [files, setFiles] = useState<{ name: string; size: number; modified: string }[]>([]);
  const [selectedFile, setSelectedFile] = useState('');
  const [chunkCount, setChunkCount] = useState(3);
  const [progress, setProgress] = useState('');
  const [fillWidth, setFillWidth] = useState(0);
  const [result, setResult] = useState('');
  const { exampleKey, showExample, closeExample } = useExampleModal();

  const refreshFiles = async () => {
    const res = await getFileList();
    if (res.ok && (res.data as any).files) setFiles((res.data as any).files);
  };

  useEffect(() => { refreshFiles(); }, []);

  const downloadChunk = async (url: string, start: number, end: number, index: number, total: number) => {
    const response = await fetch(url, { headers: { 'Range': `bytes=${start}-${end}` } });
    if (!response.ok && response.status !== 206) throw new Error(`分片 ${index} 下载失败: ${response.status}`);
    const arrayBuffer = await response.arrayBuffer();
    const percent = ((index + 1) / total) * 100;
    setFillWidth(percent);
    setProgress(`已下载 ${index + 1}/${total} 分片...`);
    return arrayBuffer;
  };

  const handleChunkDownload = async () => {
    if (!selectedFile) { alert('请选择一个文件'); return; }
    const fileInfo = files.find(f => f.name === selectedFile);
    if (!fileInfo) { alert('文件信息不存在，请刷新列表'); return; }
    const fileSize = fileInfo.size;
    const count = chunkCount;
    if (isNaN(count) || count < 1) { alert('分片数量必须大于0'); return; }
    const chunkSize = Math.ceil(fileSize / count);
    const promises = [];
    const chunks = new Array(count);
    setProgress('开始分片下载...');
    setFillWidth(0);
    setResult('下载中，请稍候...');
    try {
      for (let i = 0; i < count; i++) {
        const start = i * chunkSize;
        const end = (i === count - 1) ? fileSize - 1 : (start + chunkSize - 1);
        promises.push(downloadChunk(getDownloadUrl(selectedFile), start, end, i, count).then(buf => { chunks[i] = buf; }));
      }
      await Promise.all(promises);
      const totalLength = chunks.reduce((s, b) => s + b.byteLength, 0);
      const merged = new Uint8Array(totalLength);
      let offset = 0;
      for (const buf of chunks) { merged.set(new Uint8Array(buf), offset); offset += buf.byteLength; }
      const blob = new Blob([merged], { type: 'application/octet-stream' });
      const downloadUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl; a.download = `merged_${selectedFile}`;
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
      URL.revokeObjectURL(downloadUrl);
      setResult(`✅ 下载合并成功！文件大小: ${(totalLength / 1024).toFixed(2)} KB (原始大小: ${(fileSize / 1024).toFixed(2)} KB)`);
      setProgress('下载完成！');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setResult(`❌ 错误: ${msg}`);
      setProgress('下载失败');
      console.error(err);
    }
  };

  return (
    <div className="card">
      <div className="card-header">
        🔁 断点续传演示 <span className="badge">分片下载合并</span>
        <button className="example-btn" onClick={() => showExample('chunk-demo')}>📱 示例</button>
      </div>
      <div className="card-body">
        <div className="form-group">
          <label>选择要测试的文件</label>
          <select value={selectedFile} onChange={(e) => setSelectedFile(e.target.value)}>
            <option value="">-- 请先刷新文件列表 --</option>
            {files.map(f => <option key={f.name} value={f.name}>{f.name} ({(f.size / 1024).toFixed(1)} KB)</option>)}
          </select>
        </div>
        <div className="form-group">
          <label>分片数量（默认 3 片）</label>
          <input type="number" value={chunkCount} onChange={(e) => setChunkCount(Number(e.target.value))} min="1" max="10" />
        </div>
        <button onClick={handleChunkDownload}>📥 分片下载并合并</button>
        <div style={{ fontSize: '0.85rem', marginTop: '0.5rem' }}>{progress}</div>
        <div className="progress-bar"><div className="progress-fill" style={{ width: `${fillWidth}%` }}></div></div>
        <div className="response-area"><pre>{result || '等待操作...'}</pre></div>
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