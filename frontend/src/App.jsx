import { useEffect, useRef, useState } from 'react';

const App = () => {
  const [theme, setTheme] = useState('light');
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [status, setStatus] = useState('idle');
  const [file, setFile] = useState(null);
  const [summary, setSummary] = useState('');
  const [summaryStatus, setSummaryStatus] = useState('idle');
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef(null);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = () => setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));

  const askBackend = async () => {
    if (!question.trim()) return;
    setStatus('loading');
    setAnswer('');
    const apiBase = import.meta.env.VITE_API_URL || 'http://localhost:9200';
    try {
      const res = await fetch(`${apiBase}/api/agent/ask`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question }),
      });
      if (!res.ok) throw new Error('Request failed');
      const data = await res.json();
      setAnswer(data.answer || 'No answer returned.');
      setStatus('success');
    } catch (err) {
      setAnswer('Could not reach AI. Ensure backend is running.');
      setStatus('error');
    }
  };

  return (
    <div className="card">
      <div className="header">
        <h1>Simple RAG UI</h1>
        <button className="ghost" onClick={toggleTheme} aria-label="Toggle theme">
          {theme === 'light' ? '🌞 Light' : '🌙 Dark'}
        </button>
      </div>

      <p className="muted">Ask a question and the backend will respond via the AI model you configured.</p>

      <label className="field">
        <span>Your question</span>
        <textarea
          rows="3"
          placeholder="e.g., What are tonight's plans?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
        />
      </label>

      <div className="actions">
        <button onClick={askBackend} disabled={status === 'loading' || !question.trim()}>
          {status === 'loading' ? 'Asking…' : 'Ask'}
        </button>
        <button className="ghost" onClick={() => setQuestion('')} disabled={status === 'loading' && !question}>
          Clear
        </button>
      </div>

      <div className="preview">
        <p className="preview-title">Answer</p>
        <p className="preview-text">
          {status === 'idle' && 'No answer yet.'}
          {status === 'loading' && 'Thinking...'}
          {(status === 'success' || status === 'error') && answer}
        </p>
      </div>

      <div className="upload" onDragOver={(e) => e.preventDefault()}>
        <h2>Summarize a file</h2>
        <p className="muted">Drop a small text file or browse to upload, ChatGPT-style.</p>
        <div
          className={`chat-upload ${isDragging ? 'dragging' : ''}`}
          onDragOver={(e) => {
            e.preventDefault();
            setIsDragging(true);
          }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setIsDragging(false);
            const dropped = e.dataTransfer?.files?.[0];
            if (dropped) {
              setFile(dropped);
              setSummary('');
              setSummaryStatus('idle');
            }
          }}
        >
          <div className="upload-body">
            <div className="upload-icon">📎</div>
            <div className="upload-copy">
              <p className="upload-title">Attach a .txt file</p>
              <p className="upload-help">
                Drop it here or{' '}
                <button
                  type="button"
                  className="link-button"
                  onClick={() => fileInputRef.current?.click()}
                >
                  browse
                </button>
              </p>
              {file && <p className="upload-file">Selected: {file.name}</p>}
            </div>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".txt,text/plain"
            className="hidden-input"
            onChange={(e) => {
              const picked = e.target.files?.[0] || null;
              setFile(picked);
              setSummary('');
              setSummaryStatus('idle');
            }}
          />
        </div>
        <div className="actions">
          <button
            onClick={async () => {
              if (!file) return;
              setSummaryStatus('loading');
              setSummary('');
              const apiBase = import.meta.env.VITE_API_URL || 'http://localhost:9200';
              const formData = new FormData();
              formData.append('file', file);
              try {
                const res = await fetch(`${apiBase}/api/summarize-file`, {
                  method: 'POST',
                  body: formData,
                });
                if (!res.ok) throw new Error('Request failed');
                const data = await res.json();
                setSummary(data.answer || 'No summary returned.');
                setSummaryStatus('success');
              } catch (err) {
                setSummary('Could not summarize the file.');
                setSummaryStatus('error');
              }
            }}
            disabled={summaryStatus === 'loading' || !file}
          >
            {summaryStatus === 'loading' ? 'Summarizing…' : 'Upload & Summarize'}
          </button>
          <button
            className="ghost"
            onClick={() => {
              setFile(null);
              setSummary('');
              setSummaryStatus('idle');
            }}
            disabled={summaryStatus === 'loading'}
          >
            Reset
          </button>
        </div>
        <div className="preview">
          <p className="preview-title">Summary</p>
          <p className="preview-text">
            {summaryStatus === 'idle' && 'No file uploaded yet.'}
            {summaryStatus === 'loading' && 'Summarizing...'}
            {(summaryStatus === 'success' || summaryStatus === 'error') && summary}
          </p>
        </div>
      </div>
    </div>
  );
};

export default App;
