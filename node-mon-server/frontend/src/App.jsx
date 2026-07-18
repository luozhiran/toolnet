import ApiTest from './components/ApiTest';
import FileUpload from './components/FileUpload';
import MixedUpload from './components/MixedUpload';
import FileManager from './components/FileManager';
import ChunkDownload from './components/ChunkDownload';

function App() {
  return (
    <div className="container">
      <h1>🧪 测试服务器控制台</h1>
      <div className="sub">支持 JSON API · 单/多文件上传 · 混合上传 · 文件列表与下载 · 断点续传演示</div>
      <div className="grid">
        <ApiTest />
        <div>
          <FileUpload />
          <MixedUpload />
        </div>
        <div>
          <FileManager />
          <ChunkDownload />
        </div>
      </div>
      <div className="card" style={{ marginTop: 0 }}>
        <div className="card-header">ℹ️ 使用说明</div>
        <div className="card-body">
          <ul style={{ margin: 0, paddingLeft: '1.2rem' }}>
            <li>静态文件测试：将 .zip/.png 放入 <code>public/</code> 目录，访问 <code>/download/static/文件名</code></li>
            <li>上传的文件保存在 <code>uploads/</code> 目录，可通过文件列表查看和下载。</li>
            <li>混合上传支持同时发送多个文件、文本内容、JSON 字符串和任意表单字段。</li>
            <li>断点续传演示：选择已上传的文件，点击“分片下载并合并”，前端自动分片下载并合并。</li>
            <li>所有响应均包含调试信息，可在控制台查看网络请求详情。</li>
            <li>服务器支持跨域 (CORS)，可从其他设备访问本页面进行测试。</li>
          </ul>
        </div>
      </div>
    </div>
  );
}

export default App;