import { useEffect, useState } from 'react'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

type Health = {
  status: string
  service: string
  version: string
  serverTime: string
}

type Probe =
  | { state: 'loading' }
  | { state: 'ok'; data: Health }
  | { state: 'error'; message: string }

function App() {
  const [probe, setProbe] = useState<Probe>({ state: 'loading' })

  useEffect(() => {
    fetch(`${API_BASE}/api/health`)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<Health>
      })
      .then((data) => setProbe({ state: 'ok', data }))
      .catch((err) => setProbe({ state: 'error', message: String(err) }))
  }, [])

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-8 px-6 text-neutral-900 dark:text-neutral-100">
      <header className="text-center">
        <h1 className="text-3xl font-semibold tracking-tight">
          👑 chipthrone
        </h1>
        <p className="mt-2 text-neutral-500">국장 반도체 왕좌 대결</p>
        <p className="mt-1 text-sm">
          <span className="text-blue-600">삼성전자</span>
          <span className="mx-2 text-neutral-400">vs</span>
          <span className="text-red-600">SK하이닉스</span>
        </p>
      </header>

      <section className="w-full max-w-sm rounded-xl border border-neutral-200 dark:border-neutral-800 p-5">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-neutral-500">API 헬스체크</span>
          <StatusBadge probe={probe} />
        </div>
        <pre className="mt-3 overflow-x-auto rounded-lg bg-neutral-100 dark:bg-neutral-900 p-3 text-xs">
          {probe.state === 'loading' && '확인 중…'}
          {probe.state === 'ok' && JSON.stringify(probe.data, null, 2)}
          {probe.state === 'error' &&
            `백엔드에 연결할 수 없습니다.\n${probe.message}\n(API_BASE: ${API_BASE})`}
        </pre>
      </section>

      <footer className="text-xs text-neutral-400">
        워킹 스켈레톤 · 곧 실시간 대시보드로 확장됩니다
      </footer>
    </main>
  )
}

function StatusBadge({ probe }: { probe: Probe }) {
  const map = {
    loading: { label: '확인 중', cls: 'bg-neutral-200 text-neutral-700' },
    ok: { label: 'UP', cls: 'bg-green-100 text-green-700' },
    error: { label: 'DOWN', cls: 'bg-red-100 text-red-700' },
  } as const
  const { label, cls } = map[probe.state]
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${cls}`}>
      {label}
    </span>
  )
}

export default App
