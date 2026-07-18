import { useEffect, useState } from 'react';
import { customRequest, getApiData, getEcho, getNetworkInfo, getUser, postJson, postForm, saveMockConfig } from '../api';
import DynamicParams from './DynamicParams';
import ExampleModal from './ExampleModal';
import { examples } from '../examples';
import { useExampleModal } from '../hooks/useExampleModal';

type ParamRow = { key: string; value: string };
type BodyType = 'none' | 'json' | 'form' | 'raw';
type NetworkInfoResponse = {
  code: number;
  data?: {
    baseUrl?: string;
  };
};

export default function ApiTest({ mode }: { mode?: string }) {
  const show = (name: string) => mode === undefined || mode === name;
  const [response, setResponse] = useState('');
  const [echoParams, setEchoParams] = useState<ParamRow[]>([]);
  const [userId, setUserId] = useState('');
  const [userParams, setUserParams] = useState<ParamRow[]>([]);
  const [jsonText, setJsonText] = useState('{"message": "Hello Server", "timestamp": "now"}');
  const [formParams, setFormParams] = useState<ParamRow[]>([]);
  const [customMethod, setCustomMethod] = useState('GET');
  const [customPath, setCustomPath] = useState('/api/data');
  const [androidBaseUrl, setAndroidBaseUrl] = useState(() => {
    const hostname = window.location.hostname;
    if (hostname && hostname !== 'localhost' && hostname !== '127.0.0.1') {
      return `http://${hostname}:3000`;
    }
    return 'http://电脑局域网IP:3000';
  });
  const [customQueryParams, setCustomQueryParams] = useState<ParamRow[]>([]);
  const [customHeaders, setCustomHeaders] = useState<ParamRow[]>([]);
  const [customBodyType, setCustomBodyType] = useState<BodyType>('none');
  const [customJsonBody, setCustomJsonBody] = useState('{\n  "message": "Hello Server"\n}');
  const [customFormBody, setCustomFormBody] = useState<ParamRow[]>([]);
  const [customRawBody, setCustomRawBody] = useState('');
  const [mockStatus, setMockStatus] = useState('200');
  const [mockContentType, setMockContentType] = useState<'json' | 'text'>('json');
  const [mockResponseBody, setMockResponseBody] = useState('{\n  "code": 200,\n  "message": "mock success",\n  "data": {}\n}');
  const { exampleKey, showExample, closeExample } = useExampleModal();

  const formatResponse = (res: unknown) => JSON.stringify(res, null, 2);
  const customMethodValue = customMethod.toUpperCase();
  const customSupportsBody = customMethodValue !== 'GET' && customMethodValue !== 'HEAD';

  useEffect(() => {
    let cancelled = false;
    getNetworkInfo<NetworkInfoResponse>().then((res) => {
      const baseUrl = res.ok ? res.data.data?.baseUrl : undefined;
      if (!cancelled && baseUrl) {
        setAndroidBaseUrl((current) =>
          current === 'http://电脑局域网IP:3000' ? baseUrl : current
        );
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

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

  const buildCustomUrl = () => {
    let url = customPath.trim();

    const params = new URLSearchParams();
    customQueryParams.forEach((p) => {
      if (p.key) params.append(p.key, p.value);
    });
    const queryString = params.toString();
    if (queryString) {
      url += url.includes('?') ? `&${queryString}` : `?${queryString}`;
    }
    return url;
  };

  const buildAndroidUrl = () => {
    const url = buildCustomUrl();
    if (/^https?:\/\//i.test(url)) return url;
    const base = androidBaseUrl.trim().replace(/\/+$/, '');
    const path = url.startsWith('/') ? url : `/${url}`;
    return `${base}${path}`;
  };

  const buildCustomHeaderObject = () =>
    customHeaders.reduce<Record<string, string>>((headers, p) => {
      if (p.key) headers[p.key] = p.value;
      return headers;
    }, {});

  const buildParamObject = (params: ParamRow[]) =>
    params.reduce<Record<string, string>>((result, p) => {
      if (p.key) result[p.key] = p.value;
      return result;
    }, {});

  const buildCustomBodyPreview = () => {
    if (!customSupportsBody || customBodyType === 'none') return '';
    if (customBodyType === 'json') return customJsonBody;
    if (customBodyType === 'raw') return customRawBody;

    const params = new URLSearchParams();
    customFormBody.forEach((p) => {
      if (p.key) params.append(p.key, p.value);
    });
    return params.toString();
  };

  const buildUrlWithParams = (path: string, params: ParamRow[]) => {
    const query = new URLSearchParams();
    params.forEach((p) => {
      if (p.key) query.append(p.key, p.value);
    });
    const queryString = query.toString();
    return queryString ? `${path}?${queryString}` : path;
  };

  const buildMobileUrl = (url: string) => {
    if (/^https?:\/\//i.test(url)) return url;
    const base = androidBaseUrl.trim().replace(/\/+$/, '');
    const path = url.startsWith('/') ? url : `/${url}`;
    return `${base}${path}`;
  };

  const copyUrl = async (url: string, label = '请求地址') => {
    try {
      await navigator.clipboard.writeText(url);
      setResponse(`已复制${label}:\n${url}`);
    } catch {
      setResponse(`复制失败，请手动复制${label}:\n${url}`);
    }
  };

  const AddressPreview = ({ method, url }: { method: string; url: string }) => {
    const mobileUrl = buildMobileUrl(url);
    return (
      <div className="api-address-preview">
        <div className="api-address-header">
          <span>{method}</span>
          <span>可访问地址</span>
        </div>
        <div className="api-address-row">
          <label>PC</label>
          <code>{url}</code>
          <button type="button" className="secondary" onClick={() => copyUrl(url, 'PC 地址')}>复制</button>
        </div>
        <div className="api-address-row">
          <label>手机</label>
          <code>{mobileUrl}</code>
          <button type="button" className="secondary" onClick={() => copyUrl(mobileUrl, '手机地址')}>复制</button>
        </div>
      </div>
    );
  };

  const customPreview = {
    method: customMethodValue,
    path: customPath.trim(),
    queryParams: buildParamObject(customQueryParams),
    browserUrl: buildCustomUrl(),
    androidUrl: buildAndroidUrl(),
    headers: buildCustomHeaderObject(),
    bodyType: customBodyType,
    body: buildCustomBodyPreview(),
  };

  const getMockPathname = () => {
    try {
      return new URL(customPreview.browserUrl, window.location.origin).pathname;
    } catch {
      return customPath.trim().split('?')[0];
    }
  };

  const handleCopyAndroidUrl = async () => {
    await copyUrl(buildAndroidUrl(), '请求地址');
  };

  const handleCustomRequest = async () => {
    if (!customPath.trim()) {
      setResponse('请输入请求路径');
      return;
    }

    const method = customMethod.toUpperCase();
    const headers = new Headers();
    customHeaders.forEach((p) => {
      if (p.key) headers.set(p.key, p.value);
    });

    let body: BodyInit | undefined;
    if (method !== 'GET' && method !== 'HEAD') {
      if (customBodyType === 'json') {
        try {
          body = JSON.stringify(JSON.parse(customJsonBody));
          if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
        } catch {
          setResponse('无效的 JSON Body');
          return;
        }
      } else if (customBodyType === 'form') {
        const params = new URLSearchParams();
        customFormBody.forEach((p) => {
          if (p.key) params.append(p.key, p.value);
        });
        body = params.toString();
        if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/x-www-form-urlencoded');
      } else if (customBodyType === 'raw') {
        body = customRawBody;
      }
    }

    const url = buildCustomUrl();
    const res = await customRequest(url, { method, headers, body });
    setResponse(formatResponse({
      request: { method, url },
      response: {
        ok: res.ok,
        status: res.status,
        headers: res.headers,
        body: res.data,
      },
    }));
  };

  const handleSaveMockConfig = async () => {
    if (!customPath.trim()) {
      setResponse('请输入请求路径');
      return;
    }
    if (mockContentType === 'json') {
      try {
        JSON.parse(mockResponseBody);
      } catch {
        setResponse('返回内容不是有效 JSON');
        return;
      }
    }

    const res = await saveMockConfig({
      method: customPreview.method,
      path: getMockPathname(),
      query: customPreview.queryParams,
      headers: customPreview.headers,
      bodyType: customSupportsBody ? customPreview.bodyType : 'none',
      body: customSupportsBody ? customPreview.body : '',
      responseStatus: Number(mockStatus) || 200,
      responseContentType: mockContentType,
      responseBody: mockResponseBody,
    });

    setResponse(res.ok ? formatResponse(res.data) : `错误: ${formatResponse(res.data)}`);
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
             mode === 'form' ? 'POST /api/form' :
             mode === 'custom' ? 'CUSTOM' : 'REST'}
          </span>
        </div>
      <div className="card-body">
        {show('data') && (
        <div className="form-group">
          <label>GET /api/data（无参数）</label>
          <AddressPreview method="GET" url="/api/data" />
          <button onClick={handleGetData}>获取数据</button>
          <button className="example-btn" onClick={() => showExample('api-get')}>📱 示例</button>
        </div>
        )}

        {show('echo') && (
        <div className="sub-card">
          <label>GET /api/echo?（Query 参数，可多组）</label>
          <DynamicParams onChange={setEchoParams} placeholderKey="参数名" placeholderValue="参数值" />
          <AddressPreview method="GET" url={buildUrlWithParams('/api/echo', echoParams)} />
          <button onClick={handleEcho} style={{ marginTop: '0.5rem' }}>发送 Echo 请求</button>
          <button className="example-btn" onClick={() => showExample('api-echo')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        {show('user') && (
        <div className="sub-card">
          <label>GET /api/user/:id（路径参数 + Query）</label>
          <input type="text" placeholder="用户ID" value={userId} onChange={(e) => setUserId(e.target.value)} />
          <DynamicParams onChange={setUserParams} placeholderKey="Query参数名" placeholderValue="参数值" />
          <AddressPreview method="GET" url={buildUrlWithParams(`/api/user/${encodeURIComponent(userId || ':id')}`, userParams)} />
          <button onClick={handleGetUser} style={{ marginTop: '0.5rem' }}>获取用户信息</button>
          <button className="example-btn" onClick={() => showExample('api-user')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        {show('json') && (
        <div className="sub-card">
          <label>POST /api/json (发送 JSON)</label>
          <AddressPreview method="POST" url="/api/json" />
          <textarea rows={2} value={jsonText} onChange={(e) => setJsonText(e.target.value)} style={{ fontFamily: 'monospace' }} />
          <button onClick={handlePostJson}>提交 JSON</button>
          <button className="example-btn" onClick={() => showExample('api-json')}>📱 示例</button>
        </div>
        )}

        {show('form') && (
        <div className="sub-card">
          <label>POST /api/form (表单, x-www-form-urlencoded)</label>
          <AddressPreview method="POST" url="/api/form" />
          <DynamicParams onChange={setFormParams} placeholderKey="字段名" placeholderValue="字段值" />
          <button onClick={handlePostForm} style={{ marginTop: '0.5rem' }}>提交表单</button>
          <button className="example-btn" onClick={() => showExample('api-form')} style={{ marginLeft: '0.5rem' }}>📱 示例</button>
        </div>
        )}

        {show('custom') && (
        <div className="sub-card">
          <div className="custom-builder">
            <div className="custom-config">
              <label>自定义请求</label>
              <div className="custom-request-row">
                <select value={customMethod} onChange={(e) => setCustomMethod(e.target.value)}>
                  <option>GET</option>
                  <option>POST</option>
                  <option>PUT</option>
                  <option>PATCH</option>
                  <option>DELETE</option>
                  <option>HEAD</option>
                  <option>OPTIONS</option>
                </select>
                <input
                  type="text"
                  placeholder="/api/user/123"
                  value={customPath}
                  onChange={(e) => setCustomPath(e.target.value)}
                />
              </div>

              <label>Android Base URL</label>
              <input
                type="text"
                placeholder="http://192.168.1.100:3000"
                value={androidBaseUrl}
                onChange={(e) => setAndroidBaseUrl(e.target.value)}
              />

              <details className="custom-section">
                <summary>Query 参数</summary>
                <DynamicParams onChange={setCustomQueryParams} placeholderKey="name" placeholderValue="value" />
              </details>

              <details className="custom-section">
                <summary>Headers</summary>
                <DynamicParams onChange={setCustomHeaders} placeholderKey="Header 名" placeholderValue="Header 值" />
              </details>

              {customSupportsBody && (
              <details className="custom-section" open>
                <summary>Body</summary>
                <div className="custom-body-toolbar">
                  <label>Body 类型</label>
                  <select value={customBodyType} onChange={(e) => setCustomBodyType(e.target.value as BodyType)}>
                    <option value="none">无 Body</option>
                    <option value="json">JSON</option>
                    <option value="form">Form URL Encoded</option>
                    <option value="raw">Raw Text</option>
                  </select>
                </div>

                {customBodyType === 'json' && (
                <textarea
                  rows={6}
                  value={customJsonBody}
                  onChange={(e) => setCustomJsonBody(e.target.value)}
                  style={{ fontFamily: 'monospace' }}
                />
                )}
                {customBodyType === 'form' && (
                <DynamicParams onChange={setCustomFormBody} placeholderKey="字段名" placeholderValue="字段值" />
                )}
                {customBodyType === 'raw' && (
                <textarea
                  rows={5}
                  value={customRawBody}
                  onChange={(e) => setCustomRawBody(e.target.value)}
                  style={{ fontFamily: 'monospace' }}
                />
                )}
              </details>
              )}

              <details className="custom-section" open>
                <summary>返回内容</summary>
                <div className="custom-response-row">
                  <div>
                    <label>状态码</label>
                    <input
                      type="number"
                      min="100"
                      max="599"
                      value={mockStatus}
                      onChange={(e) => setMockStatus(e.target.value)}
                    />
                  </div>
                  <div>
                    <label>响应类型</label>
                    <select value={mockContentType} onChange={(e) => setMockContentType(e.target.value as 'json' | 'text')}>
                      <option value="json">JSON</option>
                      <option value="text">Text</option>
                    </select>
                  </div>
                </div>
                <textarea
                  rows={7}
                  value={mockResponseBody}
                  onChange={(e) => setMockResponseBody(e.target.value)}
                  style={{ fontFamily: 'monospace' }}
                />
              </details>

              <button onClick={handleCustomRequest} style={{ marginTop: '0.75rem' }}>发送自定义请求</button>
              <button className="secondary" onClick={handleSaveMockConfig} style={{ marginTop: '0.75rem' }}>保存 Mock 接口</button>
            </div>

            <div className="custom-url-preview">
              <div className="custom-url-meta">
                <span>{customPreview.method}</span>
                <span>请求预览</span>
              </div>
              <div className="custom-preview-block">
                <label>Path</label>
                <code>{customPreview.path || '-'}</code>
              </div>
              <div className="custom-preview-block">
                <label>Query 参数</label>
                <pre>{Object.keys(customPreview.queryParams).length ? formatResponse(customPreview.queryParams) : '无'}</pre>
              </div>
              <div className="custom-preview-block">
                <label>浏览器请求 URL</label>
                <code>{customPreview.browserUrl || '-'}</code>
              </div>
              <div className="custom-preview-block">
                <label>Android 请求 URL</label>
                <code>{customPreview.androidUrl || '-'}</code>
              </div>
              <div className="custom-preview-block">
                <label>Headers</label>
                <pre>{Object.keys(customPreview.headers).length ? formatResponse(customPreview.headers) : '无'}</pre>
              </div>
              {customSupportsBody && (
              <div className="custom-preview-block">
                <label>Body</label>
                <pre>{customPreview.body ? customPreview.body : '无'}</pre>
              </div>
              )}
              <div className="custom-preview-block">
                <label>命中后返回</label>
                <pre>{mockContentType === 'json' ? mockResponseBody : mockResponseBody || '无'}</pre>
              </div>
              <button type="button" className="secondary" onClick={handleCopyAndroidUrl}>复制地址</button>
            </div>
          </div>
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
          onClose={closeExample}
        />
      )}
    </div>
  );
}
