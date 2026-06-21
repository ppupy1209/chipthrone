import type { Comparison } from '../lib/marketCap'
import { formatPrice } from '../lib/marketCap'

export function ReversalCalculator({ cmp }: { cmp: Comparison }) {
  const needPct = Math.max(cmp.reversalPct, 0)
  const needPrice = Math.round(cmp.reversalPrice - cmp.challenger.price)
  // 근접도 = 도전자 시총이 1위 시총에 얼마나 근접했는가 (100%면 역전)
  const proximity = Math.min(100, (cmp.challengerCap / cmp.leaderCap) * 100)

  const challengerText =
    cmp.challenger.color === 'blue' ? 'text-blue-600' : 'text-red-600'
  const leaderText = cmp.leader.color === 'blue' ? 'text-blue-600' : 'text-red-600'
  const gaugeColor =
    cmp.challenger.color === 'blue' ? 'bg-blue-500' : 'bg-red-500'

  return (
    <div className="rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-5">
      <p className="mb-3 text-sm leading-relaxed">
        <span className={`font-medium ${challengerText}`}>{cmp.challenger.name}</span>
        가 <span className={`font-medium ${challengerText}`}>+{needPct.toFixed(1)}%</span>{' '}
        (약 +{formatPrice(needPrice)}원) 오르면{' '}
        <span className={`font-medium ${leaderText}`}>{cmp.leader.name}</span>를 제치고
        왕좌 교체 ⚔️
      </p>

      <div className="flex items-center gap-2.5">
        <span className="text-xs text-neutral-400">근접도</span>
        <div className="h-2.5 flex-1 overflow-hidden rounded-md bg-neutral-100 dark:bg-neutral-800">
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
