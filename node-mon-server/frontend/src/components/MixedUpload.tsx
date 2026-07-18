import { useState } from 'react';
import { uploadMixed } from '../api';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function MixedUpload() {
  const [files, setFiles] = useState<File[]>([]);
  const [content, setContent] = useState('');
  const [jsonData, setJsonData] = useState('');
  const [extraKey, setExtraKey] = useState('');
  const [extraValue, setExtraValue] = useState('');
  const [response, setResponse] = useState('');
  const [exampleKey, setExampleKey] = useState<string | null>(null);

  const showModal = (key: string) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  const handleUpload = async () => {
    if (!files.length) { alert('请至少选择一个文件'); return; }
    const extra: Record<string, string> = {};
    if (extraKey && extraValue) extra[extraKey] = extraValue;
    const res = await uploadMixed(files, content, jsonData, extra);
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `上传失败: ${JSON.stringify(res.data)}`);
  };

  return (
    <div className="card">
      <div className="card-header">
        🧬 混合上传 <span className="badge">/upload/mixed</span>
        <button className="example-btn" onClick={() => showModal('upload-mixed')}>📱 示例</button>
      </div>
      <div className="card-body">
        <div className="form-group">
          <label>选择多个文件</label>
          <input type="file" multiple onChange={(e) => setFiles(Array.from(e.target.files || []))} />
        </div>
        <div className="form-group">
          <label>文本内容 (content)</label>
          <input type="text" value={content} onChange={(e) => setContent(e.target.value)} placeholder="例如: Hello World" />
        </div>
        <div className="form-group">
          <label>JSON 数据 (jsonData)</label>
          <textarea rows={2} value={jsonData} onChange={(e) => setJsonData(e.target.value)} placeholder='{"userId": 123, "action": "upload"}' />
        </div>
        <div className="form-group">
          <label>普通表单字段 (任意 key/value)</label>
          <div className="inline-group">
            <input type="text" placeholder="字段名" value={extraKey} onChange={(e) => setExtraKey(e.target.value)} />
            <input type="text" placeholder="字段值" value={extraValue} onChange={(e) => setExtraValue(e.target.value)} />
          </div>
        </div>
        <button onClick={handleUpload}>⬆️ 混合上传</button>
        <div className="response-area"><pre>{response || '等待上传...'}</pre></div>
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