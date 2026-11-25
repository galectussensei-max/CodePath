import Editor from '@monaco-editor/react';
import Spinner from './Spinner.jsx';

/**
 * Java code editor. The editor files are loaded by @monaco-editor/react from a CDN the first time it
 * mounts, so the browser needs internet access for the editor to appear.
 */
export default function MonacoWrapper({ value, onChange, readOnly = false }) {
  return (
    <Editor
      height="100%"
      language="java"
      theme="vs-dark"
      value={value}
      onChange={(next) => onChange?.(next ?? '')}
      loading={<Spinner label="Loading editor..." className="text-slate-300" />}
      options={{
        readOnly,
        minimap: { enabled: false },
        fontSize: 14,
        tabSize: 4,
        scrollBeyondLastLine: false,
        automaticLayout: true,
        padding: { top: 8 },
        wordWrap: 'off',
      }}
    />
  );
}
