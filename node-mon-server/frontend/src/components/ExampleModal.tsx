import { useState } from 'react';
import { createPortal } from 'react-dom';

interface ExampleModalProps {
  title: string;
  code: string;
  onClose: () => void;
}

export default function ExampleModal({ title, code, onClose }: ExampleModalProps) {
  const [visible, setVisible] = useState(true);

  const handleClose = () => {
    setVisible(false);
    onClose?.();
  };

  if (!visible) return null;

  return createPortal(
    <div className="modal-overlay" onClick={handleClose}>
      <div className="modal-container" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{title}</h3>
          <span className="modal-close" onClick={handleClose}>&times;</span>
        </div>
        <div className="modal-body">
          <pre className="code-block">{code}</pre>
        </div>
        <div className="modal-footer">
          <p>💡 提示：将代码中的 IP 地址替换为你的服务器实际 IP，并确保手机与电脑在同一局域网。</p>
          <button onClick={handleClose}>关闭</button>
        </div>
      </div>
    </div>,
    document.body
  );
}
