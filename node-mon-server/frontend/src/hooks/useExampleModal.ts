import { useState, useCallback } from 'react';

/**
 * 管理 ExampleModal 的打开/关闭状态
 * 返回 showExample / closeExample / exampleKey / ExampleModal 渲染所需数据
 */
export function useExampleModal() {
  const [exampleKey, setExampleKey] = useState<string | null>(null);

  const showExample = useCallback((key: string) => setExampleKey(key), []);
  const closeExample = useCallback(() => setExampleKey(null), []);

  return { exampleKey, showExample, closeExample };
}
