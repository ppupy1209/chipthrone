import type { Company } from '../types'
import { formatCap, marketCap } from '../lib/marketCap'
import { AnimatedNumber } from './AnimatedNumber'

const BAR: Record<Company['color'], string> = {
  blue: 'bg-blue-500',
  red: 'bg-red-500',
  emerald: 'bg-emerald-500',
  violet: 'bg-violet-500',
}

export function MarketCapBar({ companies }: { companies: Company[] }) {
  const ranked = [...companies].sort((a, b) => marketCap(b) - marketCap(a))
  const max = ranked.length ? marketCap(ranked[0]) : 1
  const gap = ranked.length > 1 ? marketCap(ranked[0]) - marketCap(ranked[1]) : 0
  const gapPct = ranked.length > 1 ? (gap / marketCap(ranked[1])) * 100 : 0

  return (
    <div data-testid="market-cap-comparison" className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="mb-3 text-center text-[10px] tracking-[0.14em] text-neutral-400">시총 비교</div>
      <div className="space-y-2.5">
        {ranked.map((company) => {
          const cap = marketCap(company)
          return (
            <div key={company.code}>
              <div className="mb-1 flex items-baseline justify-between gap-3 text-[12px]">
                <span className="truncate font-medium">{company.name}</span>
                <AnimatedNumber value={cap} format={formatCap} className="tabular-nums text-neutral-500" />
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
                <div className={`h-full ${BAR[company.color]}`} style={{ width: `${(cap / max) * 100}%` }} />
              </div>
            </div>
          )
        })}
      </div>
      {ranked.length > 1 && (
        <div className="mt-3 text-center text-[11px] text-neutral-400 tabular-nums">
          1·2위 격차 <AnimatedNumber value={gap} format={formatCap} className="font-medium text-neutral-600 dark:text-neutral-300" /> · {gapPct.toFixed(1)}%
        </div>
      )}
    </div>
  )
}
