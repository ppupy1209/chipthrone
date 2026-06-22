import type { MarketMode } from '../types'
import { ThemeToggle } from './ThemeToggle'

const MODE_LABEL: Record<MarketMode, { text: string; cls: string }> = {
  REGULAR: { text: '정규장', cls: 'bg-green-100 text-green-700' },
  NXT: { text: '애프터마켓', cls: 'bg-green-100 text-green-700' },
  PREMARKET: { text: '프리장', cls: 'bg-green-100 text-green-700' },
  ESTIMATE: { text: '추정 시세', cls: 'bg-amber-100 text-amber-700' },
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
        <span className={`rounded-md px-2.5 py-1 text-xs font-medium ${m.cls}`}>
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
