import { useState } from 'react';
import { uploadSingle, uploadMultiple } from '../api';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function FileUpload({ mode = 'both' }) {
  const showSingle = mode === 'single' || mode === 'both';
  const showMulti = mode === 'multi' || mode === 'both';
  const [singleFile, setSingleFile] = useState(null);
  const [extraField, setExtraField] = useState('');
  const [singleResp, setSingleResp] = useState('');
  const [multiFiles, setMultiFiles] = useState([]);
  const [multiResp, setMultiResp] = useState('');
  const [exampleKey, setExampleKey] = useState(null);

  const showModal = (key) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  const handleSingleUpload = async () => {
    if (!singleFile) { alert('请选择文件'); return; }
    let extra = {};
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
    setSingleResp(res.ok ? JSON.stringify(res.data, null, 2) : `上传失败: ${res.data}`);
  };

  const handleMultiUpload = async () => {
    if (!multiFiles.length) { alert('请选择文件'); return; }
    const res = await uploadMultiple(multiFiles);
    setMultiResp(res.ok ? JSON.stringify(res.data, null, 2) : `上传失败: ${res.data}`);
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
            <input type="file" onChange={(e) => setSingleFile(e.target.files[0])} />
          </div>
          <div className="form-group">
            <label>额外字段 (可选)</label>
            <input type="text" placeholder="例如: description=测试文件" value={extraField} onChange={(e) => setExtraField(e.target.value)} />
          </div>
          <button onClick={handleSingleUpload}>⬆️ 上传文件</button>
          <div className="response-area"><pre>{singleResp || '等待上传...'}</pre></div>
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
            <input type="file" multiple onChange={(e) => setMultiFiles(Array.from(e.target.files))} />
          </div>
          <button onClick={handleMultiUpload}>⬆️ 上传多文件</button>
          <div className="response-area"><pre>{multiResp || '等待上传...'}</pre></div>
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