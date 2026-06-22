import type { MarketMode } from '../types'
import { ThemeToggle } from './ThemeToggle'
import { MODE_DOT } from '../lib/modeStyle'

// 점 색·깜빡임은 공유 MODE_DOT에서(카드 세션 태그와 통일). 여기선 라벨 문구만.
const MODE_LABEL: Record<MarketMode, string> = {
  REGULAR: '정규장',
  NXT: '애프터마켓',
  PREMARKET: '프리마켓',
  ESTIMATE: '추정 시세',
}

export function Header({ mode, at }: { mode: MarketMode; at: string }) {
  const label = MODE_LABEL[mode]
  const d = MODE_DOT[mode]
  const time = new Date(at).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  return (
    <header className="flex items-center justify-between">
      <div className="flex items-center gap-2.5">
        <div>
          <div className="text-xl font-medium tracking-[0.22em] text-neutral-900 dark:text-neutral-100">
            CHIP<span className="mx-0.5 font-normal text-[#b8924a]">·</span>THRONE
          </div>
          <div className="mt-0.5 text-[10px] tracking-[0.18em] text-neutral-400">
            국장 반도체 왕좌 대결
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
