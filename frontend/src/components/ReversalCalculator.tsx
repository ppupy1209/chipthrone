import type { Comparison } from '../lib/marketCap'
import { formatCap, formatPrice } from '../lib/marketCap'

const TEXT_COLOR = {
  blue: 'text-blue-600',
  red: 'text-red-600',
  emerald: 'text-emerald-600',
  violet: 'text-violet-600',
} as const

const BAR_COLOR = {
  blue: 'bg-blue-500',
  red: 'bg-red-500',
  emerald: 'bg-emerald-500',
  violet: 'bg-violet-500',
} as const

function particle(word: string, withFinal: string, withoutFinal: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  return code >= 0 && code <= 0x2ba3 && code % 28 !== 0 ? withFinal : withoutFinal
}

export function ReversalCalculator({ cmp }: { cmp: Comparison }) {
  const needPct = Math.max(cmp.reversalPct, 0)
  const needPrice = Math.round(cmp.reversalPrice - (cmp.challenger.regularClose ?? cmp.challenger.price))

  const challengerText = TEXT_COLOR[cmp.challenger.color]
  const leaderText = TEXT_COLOR[cmp.leader.color]
  const total = cmp.leaderCap + cmp.challengerCap
  // 두 종목 합계 대비 점유율. 50%가 역전선.
  const leaderShare = total > 0 ? (cmp.leaderCap / total) * 100 : 50

  return (
    <div data-testid="reversal-comparison" className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex items-baseline justify-between gap-3 text-[12px]">
        <span className={`font-medium ${leaderText}`}>{cmp.leader.name}</span>
        <span className="text-[10px] tracking-[0.14em] text-neutral-400">VS</span>
        <span className={`font-medium ${challengerText}`}>{cmp.challenger.name}</span>
      </div>

      <div
        data-testid="reversal-bar"
        role="img"
        aria-label={`${cmp.leader.name} ${leaderShare.toFixed(1)}%, ${cmp.challenger.name} ${(100 - leaderShare).toFixed(1)}%`}
        className="relative mt-2 flex h-3 overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800"
      >
        <div
          data-testid="reversal-bar-leader"
          className={`h-full ${BAR_COLOR[cmp.leader.color]}`}
          style={{ width: `${leaderShare}%` }}
        />
        <div className={`h-full flex-1 ${BAR_COLOR[cmp.challenger.color]}`} />
        <span className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-white/70 dark:bg-neutral-950/70" />
      </div>

      <div className="mt-1.5 flex items-baseline justify-between text-[11px] tabular-nums text-neutral-400">
        <span>{formatCap(cmp.leaderCap)} · {leaderShare.toFixed(1)}%</span>
        <span className="text-[9px] tracking-wider">역전선</span>
        <span>{formatCap(cmp.challengerCap)} · {(100 - leaderShare).toFixed(1)}%</span>
      </div>

      <p className="mt-3 border-t border-neutral-100 pt-3 text-sm leading-relaxed dark:border-neutral-800">
        <span className={`font-medium ${challengerText}`}>{cmp.challenger.name}</span>
        {particle(cmp.challenger.name, '이', '가')} <span className={`font-medium ${challengerText}`}>+{needPct.toFixed(1)}%</span>{' '}
        <span className="text-neutral-400">(약 +{formatPrice(needPrice)}원)</span> 오르면{' '}
        <span className={`font-medium ${leaderText}`}>{cmp.leader.name}</span>{particle(cmp.leader.name, '을', '를')} 제치고
        왕좌 교체
      </p>
    </div>
  )
}
