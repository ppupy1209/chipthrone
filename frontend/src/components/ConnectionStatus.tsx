import type { ConnectionState } from '../types'

const LABEL: Record<ConnectionState, string> = {
  connecting: '연결 중',
  connected: 'SSE 연결됨',
  reconnecting: '재연결 중',
}

export function ConnectionStatus({
  state,
  updatedAt,
  hasReceived,
}: {
  state: ConnectionState
  updatedAt: string
  hasReceived: boolean
}) {
  const time = hasReceived
    ? new Date(updatedAt).toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZone: 'Asia/Seoul',
      })
    : '수신 대기'

  return (
    <div data-testid="connection-status" className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-neutral-400">
      <span className="inline-flex items-center gap-1.5">
        <span
          className={`h-1.5 w-1.5 rounded-full ${
            state === 'connected' ? 'bg-emerald-500' : state === 'reconnecting' ? 'bg-amber-500 animate-pulse' : 'bg-neutral-400 animate-pulse'
          }`}
        />
        {LABEL[state]}
      </span>
      <span>마지막 갱신 {time}{hasReceived ? ' KST' : ''}</span>
    </div>
  )
}
