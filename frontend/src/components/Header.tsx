import { useEffect, useState } from 'react'
import type { Company, MarketMode } from '../types'
import { ThemeToggle } from './ThemeToggle'
import { MODE_DOT } from '../lib/modeStyle'

// 점 색·깜빡임은 공유 MODE_DOT에서(카드 세션 태그와 통일). 여기선 라벨 문구만.
const MODE_LABEL: Record<MarketMode, string> = {
  REGULAR: '실시간 · 정규장',
  NXT: '실시간 · 애프터마켓',
  PREMARKET: '실시간 · 프리마켓',
  ESTIMATE: '장 마감 · 추정 시세',
}

export function Header({ mode, statuses }: { mode: MarketMode; statuses: Company['status'][] }) {
  const hasLive = statuses.includes('LIVE')
  const hasClosed = statuses.includes('CLOSED')
  const hasEstimate = statuses.includes('ESTIMATE')
  const label = hasLive
    ? (hasClosed || hasEstimate ? '실시간 · 추정 포함' : '실시간 시세')
    : hasClosed
      ? (hasEstimate ? '장 마감 · 추정 포함' : '장 마감 시세')
      : MODE_LABEL[mode]
  const d = hasLive ? MODE_DOT.REGULAR : MODE_DOT.ESTIMATE
  // 폴링과 무관하게 1초마다 흐르는 시계. 브라우저 시간대와 무관하게 항상 KST 표시.
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])
  const time = now.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Seoul',
  })

  return (
    <header className="flex items-center justify-between">
      <div className="flex items-center gap-2.5">
        <div>
          <div className="text-xl font-medium tracking-[0.22em] text-neutral-900 dark:text-neutral-100">
            CHIP<span className="mx-0.5 font-normal text-[#b8924a]">·</span>THRONE
          </div>
          <div className="mt-0.5 text-[10px] tracking-[0.18em] text-neutral-400">
            삼성전자 · SK하이닉스 시총 비교
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs tracking-[0.08em] text-neutral-500 dark:text-neutral-400">
          <span
            className={`h-1.5 w-1.5 rounded-full ${d.dot} ${
              d.pulse ? 'animate-pulse' : ''
            }`}
          />
          {label}
        </span>
        <span className="hidden sm:inline text-xs text-neutral-400 tabular-nums">
          {time} KST
        </span>
        <ThemeToggle />
      </div>
    </header>
  )
}
