import type { Comparison } from '../lib/marketCap'
import { formatPrice } from '../lib/marketCap'

const TEXT_COLOR = {
  blue: 'text-blue-600',
  red: 'text-red-600',
  emerald: 'text-emerald-600',
  violet: 'text-violet-600',
} as const

const GAUGE_COLOR = {
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
  const needPrice = Math.round(cmp.reversalPrice - cmp.challenger.price)
  // 근접도 = 도전자 시총이 1위 시총에 얼마나 근접했는가 (100%면 역전)
  const proximity = Math.min(100, (cmp.challengerCap / cmp.leaderCap) * 100)

  const challengerText = TEXT_COLOR[cmp.challenger.color]
  const leaderText = TEXT_COLOR[cmp.leader.color]
  const gaugeColor = GAUGE_COLOR[cmp.challenger.color]

  return (
    <div data-testid="reversal-comparison" className="rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-5">
      <p className="text-sm leading-relaxed">
        <span className={`font-medium ${challengerText}`}>{cmp.challenger.name}</span>
        {particle(cmp.challenger.name, '이', '가')} <span className={`font-medium ${challengerText}`}>+{needPct.toFixed(1)}%</span>{' '}
        <span className="text-neutral-400">(약 +{formatPrice(needPrice)}원)</span> 오르면{' '}
        <span className={`font-medium ${leaderText}`}>{cmp.leader.name}</span>{particle(cmp.leader.name, '을', '를')} 제치고
        왕좌 교체
      </p>

      <div className="mt-3.5 flex items-center gap-2.5">
        <span className="text-[10px] tracking-[0.1em] text-neutral-400">역전까지</span>
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
          <div
            className={`h-full ${gaugeColor}`}
            style={{ width: `${proximity}%`, transition: 'width 0.5s ease-out' }}
          />
        </div>
        <span className={`text-[13px] font-medium tabular-nums ${challengerText}`}>
          {proximity.toFixed(1)}%
        </span>
      </div>
    </div>
  )
}
