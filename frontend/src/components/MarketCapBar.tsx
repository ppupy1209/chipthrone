import type { Comparison } from '../lib/marketCap'
import { formatCap } from '../lib/marketCap'

export function MarketCapBar({ cmp }: { cmp: Comparison }) {
  const total = cmp.leaderCap + cmp.challengerCap
  const leaderW = (cmp.leaderCap / total) * 100
  const leaderColor = cmp.leader.color === 'blue' ? 'bg-blue-500' : 'bg-red-500'
  const challengerColor =
    cmp.challenger.color === 'blue' ? 'bg-blue-500' : 'bg-red-500'
  const leaderText = cmp.leader.color === 'blue' ? 'text-blue-600' : 'text-red-600'
  const challengerText =
    cmp.challenger.color === 'blue' ? 'text-blue-600' : 'text-red-600'

  return (
    <div className="rounded-xl bg-neutral-50 dark:bg-neutral-900 p-5">
      <div className="mb-2 flex justify-between text-[13px]">
        <span className={`font-medium ${leaderText}`}>
          {cmp.leader.name} {formatCap(cmp.leaderCap)}
        </span>
        <span className="text-neutral-400">시총 비교</span>
        <span className={`font-medium ${challengerText}`}>
          {formatCap(cmp.challengerCap)} {cmp.challenger.name}
        </span>
      </div>
      <div className="flex h-3.5 overflow-hidden rounded-md">
        <div className={leaderColor} style={{ width: `${leaderW}%` }} />
        <div className={challengerColor} style={{ width: `${100 - leaderW}%` }} />
      </div>
      <div className="mt-2 text-center text-xs text-neutral-400">
        격차{' '}
        <span className="font-medium text-neutral-700 dark:text-neutral-200 tabular-nums">
          {formatCap(cmp.gap)} ({cmp.gapPct.toFixed(1)}%)
        </span>{' '}
        · 역전 초읽기
      </div>
    </div>
  )
}
