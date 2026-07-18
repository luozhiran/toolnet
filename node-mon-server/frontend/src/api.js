const API_BASE = '';

export async function request(url, options = {}) {
  try {
    const response = await fetch(url, options);
    let data;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = await response.text();
    }
    return { ok: response.ok, status: response.status, data };
  } catch (err) {
    return { ok: false, status: 0, data: err.message };
  }
}

// GET /api/data
export const getApiData = () => request('/api/data');

// GET /api/echo (query params)
export const getEcho = (params) => request(`/api/echo?${params.toString()}`);

// GET /api/user/:id
export const getUser = (id, queryParams) => {
  let url = `/api/user/${encodeURIComponent(id)}`;
  const qs = queryParams.toString();
  if (qs) url += `?${qs}`;
  return request(url);
};

// POST /api/json
export const postJson = (json) => request('/api/json', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(json)
});

// POST /api/form
export const postForm = (formData) => request('/api/form', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: formData.toString()
});

// 单文件上传
export const uploadSingle = (file, extraFields = {}) => {
  const fd = new FormData();
  fd.append('file', file);
  Object.entries(extraFields).forEach(([k, v]) => fd.append(k, v));
  return request('/upload/single', { method: 'POST', body: fd });
};

// 多文件上传
export const uploadMultiple = (files) => {
  const fd = new FormData();
  files.forEach(f => fd.append('files', f));
  return request('/upload/multiple', { method: 'POST', body: fd });
};

// 混合上传
export const uploadMixed = (files, content, jsonData, extraFields = {}) => {
  const fd = new FormData();
  files.forEach(f => fd.append('files', f));
  if (content) fd.append('content', content);
  if (jsonData) fd.append('jsonData', jsonData);
  Object.entries(extraFields).forEach(([k, v]) => fd.append(k, v));
  return request('/upload/mixed', { method: 'POST', body: fd });
};

// 获取文件列表
export const getFileList = () => request('/download/files');

// 下载文件的URL（直接使用）
export const getDownloadUrl = (filename) => `/download/${encodeURIComponent(filename)}`;