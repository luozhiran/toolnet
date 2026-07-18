import { useState } from 'react';

export default function DynamicParams({ onChange, placeholderKey = "参数名", placeholderValue = "参数值", initial = [] }) {
  const [params, setParams] = useState(initial.length ? initial : [{ key: '', value: '' }]);

  const addParam = () => {
    const newParams = [...params, { key: '', value: '' }];
    setParams(newParams);
    onChange?.(newParams.filter(p => p.key && p.value));
  };

  const removeParam = (index) => {
    const newParams = params.filter((_, i) => i !== index);
    setParams(newParams);
    onChange?.(newParams.filter(p => p.key && p.value));
  };

  const updateParam = (index, field, value) => {
    const newParams = [...params];
    newParams[index][field] = value;
    setParams(newParams);
    onChange?.(newParams.filter(p => p.key && p.value));
  };

  return (
    <div>
      {params.map((param, idx) => (
        <div key={idx} className="param-row inline-group" style={{ marginBottom: '0.5rem' }}>
          <input
            type="text"
            placeholder={placeholderKey}
            style={{ width: '45%' }}
            value={param.key}
            onChange={(e) => updateParam(idx, 'key', e.target.value)}
          />
          <input
            type="text"
            placeholder={placeholderValue}
            style={{ width: '45%' }}
            value={param.value}
            onChange={(e) => updateParam(idx, 'value', e.target.value)}
          />
          <button type="button" className="small" style={{ background: '#ef4444', padding: '0.3rem 0.6rem' }} onClick={() => removeParam(idx)}>删除</button>
        </div>
      ))}
      <button type="button" className="secondary small" onClick={addParam} style={{ background: '#3b82f6', padding: '0.3rem 0.8rem' }}>➕ 添加参数</button>
    </div>
  );
}