import { useState } from 'react'
import './App.css'

const MODELS = ['qwen2.5:1.5b', 'gpt-4o-mini', 'gpt-4o', 'claude-sonnet']

async function api(path, { method = 'GET', headers = {}, body } = {}) {
  const res = await fetch(path, {
    method,
    headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }
  if (!res.ok) {
    const msg = (data && (data.error || data.detail || data.message)) || `HTTP ${res.status}`
    const err = new Error(msg)
    err.status = res.status
    throw err
  }
  return { data, headers: res.headers }
}

const usd = (n) => '$' + Number(n ?? 0).toFixed(6)
const num = (n) => Number(n ?? 0).toLocaleString('en-IN')
const when = (iso) => (iso ? new Date(iso).toLocaleString('en-IN') : '-')

export default function App() {
  const [tab, setTab] = useState('team')
  return (
    <div className="app">
      <header className="header">
        <div className="brand">TokenGate <small>LLM cost gateway</small></div>
        <nav className="tabs">
          <button className={tab === 'team' ? 'active' : ''} onClick={() => setTab('team')}>Team</button>
          <button className={tab === 'admin' ? 'active' : ''} onClick={() => setTab('admin')}>Admin</button>
        </nav>
      </header>
      <div hidden={tab !== 'team'}><TeamView /></div>
      <div hidden={tab !== 'admin'}><AdminView /></div>
    </div>
  )
}

function TeamView() {
  const [apiKey, setApiKey] = useState('')
  const [usage, setUsage] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const authHeaders = { Authorization: `Bearer ${apiKey.trim()}` }

  async function loadUsage() {
    if (!apiKey.trim()) return
    setLoading(true)
    setError('')
    try {
      const { data } = await api('/v1/usage/today', { headers: authHeaders })
      setUsage(data)
    } catch (e) {
      setError(e.message)
      setUsage(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <section className="card">
        <h2>Team API key</h2>
        <div className="row">
          <input type="password" placeholder="tg_..." value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && loadUsage()} />
          <button onClick={loadUsage} disabled={loading}>{loading ? 'Loading...' : 'Load usage'}</button>
        </div>
        {error && <p className="error">{error}</p>}
      </section>
      {usage && <UsagePanel usage={usage} onRefresh={loadUsage} />}
      {usage && <Playground authHeaders={authHeaders} onDone={() => setTimeout(loadUsage, 1500)} />}
    </>
  )
}

function UsagePanel({ usage, onRefresh }) {
  const maxTokens = Math.max(1, ...usage.byModel.map((m) => m.totalTokens))
  return (
    <section className="card">
      <div className="card-head">
        <h2>{usage.team} &middot; today</h2>
        <button className="ghost small" onClick={onRefresh}>Refresh</button>
      </div>
      <div className="stats">
        <Stat label="Requests" value={num(usage.requests)} />
        <Stat label="Tokens" value={num(usage.totalTokens)} />
        <Stat label="Cost (USD)" value={usd(usage.totalCostUsd)} />
      </div>
      <h3>By model</h3>
      {usage.byModel.length === 0 ? (
        <p className="muted">No calls yet today. Try the playground below.</p>
      ) : (
        <div className="bars">
          {usage.byModel.map((m) => (
            <div key={m.model} className="bar-row">
              <span className="bar-label" title={m.model}>{m.model}</span>
              <div className="bar-track">
                <div className="bar-fill" style={{ width: `${(m.totalTokens / maxTokens) * 100}%` }} />
              </div>
              <span className="bar-meta">{num(m.totalTokens)} tokens &middot; {m.requests} req &middot; {usd(m.costUsd)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
    </div>
  )
}

function Playground({ authHeaders, onDone }) {
  const [model, setModel] = useState(MODELS[0])
  const [prompt, setPrompt] = useState('')
  const [stream, setStream] = useState(true)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function send() {
    if (!prompt.trim() || busy) return
    setBusy(true)
    setError('')
    setResult(null)
    const started = performance.now()
    const body = { model, messages: [{ role: 'user', content: prompt }] }
    try {
      if (stream) await sendStreaming(body, started)
      else await sendNormal(body, started)
      onDone()
    } catch (e) {
      setError(`${e.status ?? ''} ${e.message}`.trim())
    } finally {
      setBusy(false)
    }
  }

  async function sendNormal(body, started) {
    const { data, headers } = await api('/v1/chat/completions', { method: 'POST', headers: authHeaders, body })
    setResult({
      text: data.choices?.[0]?.message?.content ?? '',
      tokens: data.usage?.total_tokens ?? 0,
      provider: headers.get('X-TokenGate-Provider'),
      cache: headers.get('X-TokenGate-Cache'),
      fallback: headers.get('X-TokenGate-Fallback') === 'true',
      ms: Math.round(performance.now() - started),
      firstMs: null,
      streaming: false,
    })
  }

  // SSE: response body ko chunk-by-chunk padho aur har "data:" line se naya text jodo
  async function sendStreaming(body, started) {
    const res = await fetch('/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders },
      body: JSON.stringify({ ...body, stream: true }),
    })
    if (!res.ok) {
      let msg = `HTTP ${res.status}`
      try { const j = await res.json(); msg = j.error || j.detail || msg } catch { /* not json */ }
      const err = new Error(msg)
      err.status = res.status
      throw err
    }

    const meta = {
      provider: res.headers.get('X-TokenGate-Provider'),
      cache: res.headers.get('X-TokenGate-Cache'),
      fallback: res.headers.get('X-TokenGate-Fallback') === 'true',
    }
    let text = ''
    let tokens = 0
    let firstMs = null
    setResult({ ...meta, text: '', tokens: 0, ms: 0, firstMs: null, streaming: true })

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()
        if (!data || data === '[DONE]') continue
        let json
        try { json = JSON.parse(data) } catch { continue }
        if (json.error) throw new Error(json.error)
        const piece = json.choices?.[0]?.delta?.content
        if (piece) {
          if (firstMs === null) firstMs = Math.round(performance.now() - started)
          text += piece
        }
        if (json.usage) tokens = json.usage.total_tokens
        setResult({ ...meta, text, tokens, ms: Math.round(performance.now() - started), firstMs, streaming: true })
      }
    }
    setResult({ ...meta, text, tokens, ms: Math.round(performance.now() - started), firstMs, streaming: false })
  }

  return (
    <section className="card">
      <h2>Playground</h2>
      <div className="row">
        <select value={model} onChange={(e) => setModel(e.target.value)}>
          {MODELS.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
        <label className="toggle">
          <input type="checkbox" checked={stream} onChange={(e) => setStream(e.target.checked)} />
          Stream response
        </label>
      </div>
      <textarea placeholder="Ask anything... (Ctrl+Enter to send)" value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && e.ctrlKey && send()} />
      <button onClick={send} disabled={busy}>{busy ? (stream ? 'Streaming...' : 'Thinking...') : 'Send via TokenGate'}</button>
      {error && <p className="error">{error}</p>}
      {result && (
        <>
          <div className="badges">
            <span className={`badge ${result.provider === 'ollama' ? 'green' : ''}`}>provider: {result.provider}</span>
            <span className={`badge ${result.cache === 'HIT' ? 'blue' : ''}`}>cache: {result.cache}</span>
            {result.fallback && <span className="badge orange">fallback used</span>}
            {result.firstMs !== null && <span className="badge blue">first token {num(result.firstMs)} ms</span>}
            <span className="badge">{num(result.tokens)} tokens</span>
            <span className="badge">{num(result.ms)} ms total</span>
          </div>
          <div className="answer">
            {result.text}
            {result.streaming && <span className="cursor">{'\u258D'}</span>}
          </div>
        </>
      )}
    </section>
  )
}
function AdminView() {
  const [token, setToken] = useState('')
  const [teams, setTeams] = useState(null)
  const [alerts, setAlerts] = useState([])
  const [error, setError] = useState('')
  const [newName, setNewName] = useState('')
  const [created, setCreated] = useState(null)
  const [budgetInputs, setBudgetInputs] = useState({})
  const [promptTeamId, setPromptTeamId] = useState('')
  const [promptText, setPromptText] = useState('')

  async function savePrompt() {
    if (!promptTeamId) return
    setError('')
    try {
      await api(`/admin/teams/${promptTeamId}/system-prompt`, { method: 'PUT', headers, body: { systemPrompt: promptText.trim() || null } })
      load()
    } catch (e) {
      setError(`Could not save system prompt: ${e.message}`)
    }
  }

  const headers = { 'X-Admin-Token': token.trim() }

  async function load() {
    if (!token.trim()) return
    setError('')
    try {
      const [t, a] = await Promise.all([
        api('/admin/teams', { headers }),
        api('/admin/alerts', { headers }),
      ])
      setTeams(t.data)
      setAlerts(a.data)
    } catch (e) {
      setError(e.message)
      setTeams(null)
    }
  }

  async function createTeam() {
    if (!newName.trim()) return
    setError('')
    try {
      const { data } = await api('/admin/teams', { method: 'POST', headers, body: { name: newName.trim() } })
      setCreated(data)
      setNewName('')
      load()
    } catch (e) {
      setError(`Could not create team: ${e.message}`)
    }
  }

  async function saveBudget(id) {
    const raw = budgetInputs[id]
    const value = raw === undefined || raw === '' ? null : Number(raw)
    setError('')
    try {
      await api(`/admin/teams/${id}/budget`, { method: 'PUT', headers, body: { dailyBudgetUsd: value } })
      setBudgetInputs((prev) => ({ ...prev, [id]: '' }))
      load()
    } catch (e) {
      setError(`Could not set budget: ${e.message}`)
    }
  }

  return (
    <>
      <section className="card">
        <h2>Admin token</h2>
        <div className="row">
          <input type="password" placeholder="X-Admin-Token" value={token}
            onChange={(e) => setToken(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && load()} />
          <button onClick={load}>Load</button>
        </div>
        {error && <p className="error">{error}</p>}
      </section>

      {teams && (
        <>
          <section className="card">
            <h2>Create team</h2>
            <div className="row">
              <input placeholder="team-name (lowercase, numbers, hyphens)" value={newName}
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && createTeam()} />
              <button onClick={createTeam}>Create</button>
            </div>
            {created && (
              <div className="key-box">
                <strong>Team "{created.name}" created.</strong> Copy this key now. It will not be shown again.
                <code>{created.apiKey}</code>
                <button className="small" onClick={() => navigator.clipboard.writeText(created.apiKey)}>Copy key</button>{' '}
                <button className="small ghost" onClick={() => setCreated(null)}>Done</button>
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Teams ({teams.length})</h2>
              <button className="ghost small" onClick={load}>Refresh</button>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Name</th><th>Key</th><th>Daily budget</th><th>Set budget (USD)</th><th>Created</th></tr>
                </thead>
                <tbody>
                  {teams.map((t) => (
                    <tr key={t.id}>
                      <td><strong>{t.name}</strong></td>
                      <td><code>{t.apiKeyPrefix}...</code></td>
                      <td>{t.dailyBudgetUsd == null ? <span className="muted">No limit</span> : usd(t.dailyBudgetUsd)}</td>
                      <td>
                        <div className="budget-cell">
                          <input type="number" step="0.0001" min="0" placeholder="empty = none"
                            value={budgetInputs[t.id] ?? ''}
                            onChange={(e) => setBudgetInputs((prev) => ({ ...prev, [t.id]: e.target.value }))} />
                          <button className="small" onClick={() => saveBudget(t.id)}>Save</button>
                        </div>
                      </td>
                      <td className="muted">{when(t.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="card">
            <h2>Team system prompt</h2>
            <p className="muted">Instructions TokenGate adds to every request of the team (skipped if the request already has a system message).</p>
            <div className="row">
              <select value={promptTeamId} onChange={(e) => {
                const id = e.target.value
                setPromptTeamId(id)
                const selected = teams.find((x) => String(x.id) === id)
                setPromptText(selected?.systemPrompt ?? '')
              }}>
                <option value="">Select a team</option>
                {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
              </select>
            </div>
            <textarea placeholder="e.g. Always reply in English, in under 80 words." value={promptText}
              onChange={(e) => setPromptText(e.target.value)} />
            <button onClick={savePrompt} disabled={!promptTeamId}>Save system prompt</button>
          </section>

          <section className="card">
            <h2>Budget alerts ({alerts.length})</h2>
            {alerts.length === 0 ? (
              <p className="muted">No alerts yet.</p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>Team</th><th>Level</th><th>Spent</th><th>Budget</th><th>Date</th><th>At</th></tr>
                  </thead>
                  <tbody>
                    {alerts.map((a, i) => (
                      <tr key={i}>
                        <td><strong>{a.team}</strong></td>
                        <td><span className={`badge ${a.thresholdPercent >= 100 ? 'red' : 'orange'}`}>{a.thresholdPercent}%</span></td>
                        <td>{usd(a.spentUsd)}</td>
                        <td>{usd(a.budgetUsd)}</td>
                        <td>{a.date}</td>
                        <td className="muted">{when(a.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </>
  )
}