import { useState } from 'react';
import { getApiData, getEcho, getUser, postJson, postForm } from '../api';
import DynamicParams from './DynamicParams';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';

export default function ApiTest({ mode }) {
  const show = (name) => mode === undefined || mode === name;
  const [response, setResponse] = useState('');
  const [echoParams, setEchoParams] = useState([]);
  const [userId, setUserId] = useState('');
  const [userParams, setUserParams] = useState([]);
  const [jsonText, setJsonText] = useState('{"message": "Hello Server", "timestamp": "now"}');
  const [formParams, setFormParams] = useState([]);
  const [exampleKey, setExampleKey] = useState(null);

  const showModal = (key) => setExampleKey(key);
  const closeModal = () => setExampleKey(null);

  const handleGetData = async () => {
    const res = await getApiData();
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `错误: ${res.data}`);
  };

  const handleEcho = async () => {
    const params = new URLSearchParams();
    echoParams.forEach(p => { if (p.key && p.value) params.append(p.key, p.value); });
    if (params.toString() === '') { setResponse('请至少添加一对参数'); return; }
    const res = await getEcho(params);
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `错误: ${res.data}`);
  };

  const handleGetUser = async () => {
    if (!userId) { setResponse('请输入用户ID'); return; }
    const params = new URLSearchParams();
    userParams.forEach(p => { if (p.key && p.value) params.append(p.key, p.value); });
    const res = await getUser(userId, params);
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `错误: ${res.data}`);
  };

  const handlePostJson = async () => {
    let json;
    try { json = JSON.parse(jsonText); } catch(e) { setResponse('无效的 JSON 格式'); return; }
    const res = await postJson(json);
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `错误: ${res.data}`);
  };

  const handlePostForm = async () => {
    const params = new URLSearchParams();
    formParams.forEach(p => { if (p.key && p.value) params.append(p.key, p.value); });
    if (params.toString() === '') { setResponse('请至少添加一对表单字段'); return; }
    const res = await postForm(params);
    setResponse(res.ok ? JSON.stringify(res.data, null, 2) : `错误: ${res.data}`);
  };

  return (
    <div className="card">
      <div className="card-header">
          📡 API 测试{' '}
          <span className="badge">
            {mode === 'data' ? 'GET /api/data' :
             mode === 'echo' ? 'GET /api/echo' :
             mode === 'user' ? 'GET /api/user/:id' :
             mode === 'json' ? 'POST /api/json' :
             mode === 'form' ? 'POST /api/form' : 'REST'}
          </span>
        </div>
      <div className="card-body">
        {show('data') && (
        <div className="form-group">
          <label>GET /api/data（无参数）</label>
          <button onClick={handleGetData}>获取数据</button>
          <button className="example-btn" onClick={() => showModal('api-get')}>📱 示例</button>
        </div>
        )}

        {show('echo') && (
        <div className="sub-card">
          <label>GET /api/echo?（Query 参数，可多组）</label>
          <DynamicParams onChange={setEchoParams} placeholderKey="参数名" placeholderValue="参数值" />
          <button onClick={handleEcho} style={{ marginTop: '0.5rem' }}>发送 Echo 请求</button>
          <button className="example-btn" onClick={() => showModal('api-echo')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        {show('user') && (
        <div className="sub-card">
          <label>GET /api/user/:id（路径参数 + Query）</label>
          <input type="text" placeholder="用户ID" value={userId} onChange={(e) => setUserId(e.target.value)} />
          <DynamicParams onChange={setUserParams} placeholderKey="Query参数名" placeholderValue="参数值" />
          <button onClick={handleGetUser} style={{ marginTop: '0.5rem' }}>获取用户信息</button>
          <button className="example-btn" onClick={() => showModal('api-user')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        {show('json') && (
        <div className="sub-card">
          <label>POST /api/json (发送 JSON)</label>
          <textarea rows="2" value={jsonText} onChange={(e) => setJsonText(e.target.value)} style={{ fontFamily: 'monospace' }} />
          <button onClick={handlePostJson}>提交 JSON</button>
          <button className="example-btn" onClick={() => showModal('api-json')}>📱 示例</button>
        </div>
        )}

        {show('form') && (
        <div className="sub-card">
          <label>POST /api/form (表单, x-www-form-urlencoded)</label>
          <DynamicParams onChange={setFormParams} placeholderKey="字段名" placeholderValue="字段值" />
          <button onClick={handlePostForm} style={{ marginTop: '0.5rem' }}>提交表单</button>
          <button className="example-btn" onClick={() => showModal('api-form')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        <div className="response-area">
          <pre>{response || '等待请求...'}</pre>
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