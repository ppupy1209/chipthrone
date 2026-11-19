import { ThemeToggle } from './ThemeToggle'
import type { StreamConnection } from '../types'

export function Header({ updatedAt, connection }: { updatedAt: string; connection: StreamConnection }) {
  const date = new Date(updatedAt)
  const time = Number.isNaN(date.getTime())
    ? null
    : date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
  return (
    <header className="dashboard-header">
      <a className="wordmark" href="#">CHIP THRONE</a>
      <div className="header-actions">
        <span className="stream-status" data-connection={connection}>
          {connection === 'reconnecting'
            ? <><span role="status">연결 재시도 중</span>{time && <time dateTime={updatedAt}> · {time} 기준</time>}</>
            : time ? <time dateTime={updatedAt}>{time} 갱신</time> : '시세를 받는 중'}
        </span>
        <ThemeToggle />
      </div>
    </header>
  )
}
