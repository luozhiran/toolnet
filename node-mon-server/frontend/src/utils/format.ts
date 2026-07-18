import { getDownloadUrl } from '../api';

/** 智能文件大小格式化 */
export function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

/** 相对时间 */
export function formatTime(dateStr: string): string {
  const now = Date.now();
  const diff = now - new Date(dateStr).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return new Date(dateStr).toLocaleDateString('zh-CN');
}

/** 根据扩展名返回文件类型信息 */
export function fileTypeInfo(filename: string): { icon: string; color: string; label: string } {
  const ext = (filename.split('.').pop() || '').toLowerCase();
  const map: Record<string, { icon: string; color: string; label: string }> = {
    png:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    jpg:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    jpeg: { icon: '🖼️', color: '#f59e0b', label: '图片' },
    gif:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    svg:  { icon: '🖼️', color: '#f59e0b', label: '图片' },
    webp: { icon: '🖼️', color: '#f59e0b', label: '图片' },
    pdf:  { icon: '📕', color: '#ef4444', label: 'PDF' },
    zip:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    rar:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    '7z': { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    tar:  { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    gz:   { icon: '📦', color: '#8b5cf6', label: '压缩包' },
    mp4:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    mov:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    avi:  { icon: '🎬', color: '#06b6d4', label: '视频' },
    mp3:  { icon: '🎵', color: '#10b981', label: '音频' },
    wav:  { icon: '🎵', color: '#10b981', label: '音频' },
    json: { icon: '📋', color: '#6366f1', label: 'JSON' },
    xml:  { icon: '📋', color: '#6366f1', label: 'XML' },
    txt:  { icon: '📄', color: '#64748b', label: '文本' },
    log:  { icon: '📄', color: '#64748b', label: '日志' },
    md:   { icon: '📝', color: '#64748b', label: 'Markdown' },
    js:   { icon: '💛', color: '#eab308', label: 'JS' },
    ts:   { icon: '💙', color: '#3b82f6', label: 'TS' },
    html: { icon: '🌐', color: '#f97316', label: 'HTML' },
    css:  { icon: '🎨', color: '#06b6d4', label: 'CSS' },
  };
  return map[ext] || { icon: '📎', color: '#94a3b8', label: ext || '文件' };
}

/** 构建完整下载 URL */
export function buildDownloadUrl(filename: string): string {
  return window.location.origin + getDownloadUrl(filename);
}

/** 复制文本到剪贴板 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    return true;
  }
}
