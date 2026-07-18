import { useState } from 'react';
import { uploadSingle, uploadMultiple, getDownloadUrl } from '../api';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

// 文件类型图标
function fileIcon(ext: string): string {
  const map: Record<string, string> = {
    png:'🖼️', jpg:'🖼️', jpeg:'🖼️', gif:'🖼️', svg:'🖼️', webp:'🖼️',
    pdf:'📕', zip:'📦', rar:'📦', '7z':'📦', tar:'📦', gz:'📦',
    mp4:'🎬', mov:'🎬', avi:'🎬', mp3:'🎵', wav:'🎵',
    json:'📋', xml:'📋', txt:'📄', log:'📄', md:'📝',
    js:'💛', ts:'💙', html:'🌐', css:'🎨',
  };
  return map[(ext || '').toLowerCase()] || '📎';
}

function formatSize(bytes: number | undefined): string {
  if (!bytes) return '未知';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

function fullUrl(filename: string): string {
  return window.location.origin + getDownloadUrl(filename);
}

interface FileInfo {
  filename: string;
  originalName?: string;
  size?: number;
  mimetype?: string;
}

function FileResult({ file, onCopy }: { file: FileInfo; onCopy: (filename: string) => void }) {
  const ext = (file.originalName || file.filename || '').split('.').pop() || '';
  return (
    <div className="upload-result">
      <span className="upload-result-icon">{fileIcon(ext)}</span>
      <div className="upload-result-info">
        <div className="upload-result-name">{file.originalName || file.filename}</div>
        <div className="upload-result-meta">
          大小 {formatSize(file.size)} · {file.mimetype || ''}
        </div>
        <div className="upload-result-url">
          <code>{fullUrl(file.filename)}</code>
          <button className="file-copy-btn" onClick={() => onCopy(file.filename)}>📋 复制</button>
        </div>
      </div>
    </div>
  );
};

function copyUrl(filename: string): void {
  navigator.clipboard.writeText(fullUrl(filename)).catch(() => {
    const ta = document.createElement('textarea');
    ta.value = fullUrl(filename);
    ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    document.execCommand('copy'); document.body.removeChild(ta);
  });
}

interface SingleResult { file: FileInfo; ok: true }
interface MultiResult { files: FileInfo[]; ok: true }
interface ErrorResult { ok: false; message: string }

export default function FileUpload({ mode = 'both' }: { mode?: string }) {
  const showSingle = mode === 'single' || mode === 'both';
  const showMulti = mode === 'multi' || mode === 'both';
  const [singleFile, setSingleFile] = useState<File | null>(null);
  const [extraField, setExtraField] = useState('');
  const [singleResult, setSingleResult] = useState<SingleResult | ErrorResult | null>(null);
  const [singleRaw, setSingleRaw] = useState('');
  const [multiFiles, setMultiFiles] = useState<File[]>([]);
  const [multiResults, setMultiResults] = useState<MultiResult | ErrorResult | null>(null);
  const [multiRaw, setMultiRaw] = useState('');
  const [exampleKey, setExampleKey] = useState<string | null>(null);
  const [copiedFile, setCopiedFile] = useState<string | null>(null);

  const showModal = (key: string) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  const doCopy = (filename: string) => {
    copyUrl(filename);
    setCopiedFile(filename);
    setTimeout(() => setCopiedFile(null), 1500);
  };

  const handleSingleUpload = async () => {
    if (!singleFile) { alert('请选择文件'); return; }
    const extra: Record<string, string> = {};
    const extraRaw = extraField.trim();
    if (extraRaw) {
      if (extraRaw.includes('=')) {
        const [k, v] = extraRaw.split('=');
        extra[k] = v;
      } else {
        extra.description = extraRaw;
      }
    }
    const res = await uploadSingle(singleFile, extra);
    const resData = res.data as any;
    const raw = JSON.stringify(resData, null, 2);
    setSingleRaw(raw);
    if (res.ok) {
      setSingleResult({ file: resData.data, ok: true });
    } else {
      setSingleResult({ ok: false, message: resData?.message || String(resData) });
    }
  };

  const handleMultiUpload = async () => {
    if (!multiFiles.length) { alert('请选择文件'); return; }
    const res = await uploadMultiple(multiFiles);
    const resData = res.data as any;
    const raw = JSON.stringify(resData, null, 2);
    setMultiRaw(raw);
    if (res.ok) {
      setMultiResults({ files: resData.data?.files || [], ok: true });
    } else {
      setMultiResults({ ok: false, message: resData?.message || String(resData) });
    }
  };

  return (
    <>
      {showSingle && (
      <div className="card">
        <div className="card-header">
          📤 单文件上传 <span className="badge">/upload/single</span>
          <button className="example-btn" onClick={() => showModal('upload-single')}>📱 示例</button>
        </div>
        <div className="card-body">
          <div className="form-group">
            <label>选择文件</label>
            <input type="file" onChange={(e) => setSingleFile(e.target.files![0] || null)} />
            {singleFile && (
              <div className="selected-file">
                📎 {singleFile.name} ({(singleFile.size / 1024).toFixed(1)} KB)
                <button className="file-clear-btn" onClick={() => setSingleFile(null)}>✕</button>
              </div>
            )}
          </div>
          <div className="form-group">
            <label>额外字段 (可选)</label>
            <input type="text" placeholder="例如: description=测试文件" value={extraField} onChange={(e) => setExtraField(e.target.value)} />
          </div>
          <button onClick={handleSingleUpload} disabled={!singleFile}>⬆️ 上传文件</button>

          {singleResult?.ok && singleResult.file && (
            <div className="upload-results">
              <div className="upload-results-title">✅ 上传成功</div>
              <FileResult file={singleResult.file} onCopy={doCopy} />
              <div className="upload-results-tip">
                {copiedFile === singleResult.file.filename ? '✅ 已复制到剪贴板' : '点击 📋 复制下载地址'}
              </div>
            </div>
          )}
          {singleResult && !singleResult.ok && (
            <div className="response-area"><pre>❌ 上传失败: {singleResult.message}</pre></div>
          )}
          {singleRaw && (
            <details className="raw-json-details">
              <summary>查看原始响应</summary>
              <div className="response-area" style={{ marginTop: '0.5rem' }}><pre>{singleRaw}</pre></div>
            </details>
          )}
          {!singleResult && (
            <div className="response-area"><pre>等待上传...</pre></div>
          )}
        </div>
      </div>
      )}

      {showMulti && (
      <div className="card">
        <div className="card-header">
          📚 多文件上传 <span className="badge">/upload/multiple</span>
          <button className="example-btn" onClick={() => showModal('upload-multiple')}>📱 示例</button>
        </div>
        <div className="card-body">
          <div className="form-group">
            <label>选择多个文件</label>
            <input type="file" multiple onChange={(e) => setMultiFiles(Array.from(e.target.files || []))} />
            {multiFiles.length > 0 && (
              <div className="selected-file">
                📎 已选 {multiFiles.length} 个文件 ({(multiFiles.reduce((s, f) => s + f.size, 0) / 1024).toFixed(1)} KB)
                <button className="file-clear-btn" onClick={() => setMultiFiles([])}>✕</button>
              </div>
            )}
          </div>
          <button onClick={handleMultiUpload} disabled={multiFiles.length === 0}>⬆️ 上传多文件</button>

          {multiResults?.ok && (
            <div className="upload-results">
              <div className="upload-results-title">✅ 上传成功 · {multiResults.files.length} 个文件</div>
              {multiResults.files.map(f => (
                <FileResult key={f.filename} file={f} onCopy={doCopy} />
              ))}
              <div className="upload-results-tip">
                {copiedFile ? '✅ 已复制到剪贴板' : '点击 📋 复制下载地址'}
              </div>
            </div>
          )}
          {multiResults && !multiResults.ok && (
            <div className="response-area"><pre>❌ 上传失败: {multiResults.message}</pre></div>
          )}
          {multiRaw && (
            <details className="raw-json-details">
              <summary>查看原始响应</summary>
              <div className="response-area" style={{ marginTop: '0.5rem' }}><pre>{multiRaw}</pre></div>
            </details>
          )}
          {!multiResults && (
            <div className="response-area"><pre>等待上传...</pre></div>
          )}
        </div>
      </div>
      )}

      {exampleKey && (
        <ExampleModal
          title={examples[exampleKey]?.title || '示例'}
          code={examples[exampleKey]?.code || '示例代码未找到'}
          onClose={closeModal}
        />
      )}
    </>
  );
}
