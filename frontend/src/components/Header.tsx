import type { MarketMode } from '../types'
import { ThemeToggle } from './ThemeToggle'

// 라이브(정규장/애프터마켓/프리마켓)=녹색 점, 추정=앰버 점. 점만 색을 갖고 라벨은 muted.
const MODE_LABEL: Record<MarketMode, { text: string; dot: string }> = {
  REGULAR: { text: '정규장', dot: 'bg-[#1d9e75]' },
  NXT: { text: '애프터마켓', dot: 'bg-[#1d9e75]' },
  PREMARKET: { text: '프리마켓', dot: 'bg-[#1d9e75]' },
  ESTIMATE: { text: '추정 시세', dot: 'bg-[#c2912e]' },
}

export function Header({ mode, at }: { mode: MarketMode; at: string }) {
  const m = MODE_LABEL[mode]
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
          <span className={`h-1.5 w-1.5 rounded-full ${m.dot}`} />
          {m.text}
        </span>
        <span className="hidden sm:inline text-xs text-neutral-400 tabular-nums">
          {time} KST
        </span>
        <ThemeToggle />
      </div>
    </header>
  )
}
