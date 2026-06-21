import type { Company, MarketMode } from '../types'
import {
  formatCap,
  formatPct,
  formatPrice,
  formatUsd,
  marketCap,
} from '../lib/marketCap'

const COLOR = {
  blue: { top: 'border-t-blue-500', cap: 'text-blue-600' },
  red: { top: 'border-t-red-500', cap: 'text-red-600' },
} as const

export function CompanyCard({
  company,
  isLeader,
  mode,
  fxRate,
}: {
  company: Company
  isLeader: boolean
  mode: MarketMode
  fxRate: number
}) {
  const c = COLOR[company.color]
  const up = company.changePct >= 0
  const isEstimate = mode === 'ESTIMATE'

  return (
    <div
      className={`relative rounded-xl border border-neutral-200 dark:border-neutral-800 border-t-[3px] ${c.top} bg-white dark:bg-neutral-900 p-5`}
    >
      <span
        className={`absolute -top-2.5 right-3.5 rounded-md px-2 py-0.5 text-[11px] font-medium ${
          isLeader
            ? 'bg-amber-100 text-amber-700'
            : 'bg-neutral-100 text-neutral-500 dark:bg-neutral-800 dark:text-neutral-400'
        }`}
      >
        {isLeader ? '👑 현재 왕좌' : '도전자 · 2위'}
      </span>

      <div className="flex items-center gap-2">
        <img
          src={company.logo}
          alt=""
          className="h-7 w-7 shrink-0 rounded-full"
          draggable={false}
        />
        <span className="text-[15px] font-medium leading-tight">
          {company.name}
        </span>
        <span className="text-[11px] text-neutral-400">{company.code}</span>
      </div>

      <div className="mt-3 flex items-baseline gap-2">
        <span className="text-2xl font-semibold tabular-nums">
          {formatPrice(company.price)}
        </span>
        <span className="text-xs text-neutral-400">원</span>
        {isEstimate && (
          <span className="text-[11px] text-neutral-400 tabular-nums">
            ≈ {formatUsd(company.price, fxRate)}
          </span>
        )}
        <span
          className={`ml-auto text-sm font-medium tabular-nums ${
            up ? 'text-green-600' : 'text-red-600'
          }`}
        >
          {up ? '▲' : '▼'} {formatPct(company.changePct)}
        </span>
      </div>

      <div className="mt-3 flex items-center justify-between border-t border-neutral-100 dark:border-neutral-800 pt-2.5 text-[13px]">
        <span className="text-neutral-500">시가총액</span>
        <span className={`font-medium tabular-nums ${c.cap}`}>
          {formatCap(marketCap(company))}
        </span>
      </div>

      <div className="mt-2 space-y-0.5 text-right text-[11px] text-neutral-400 tabular-nums">
        <div>정규장 종가 {formatPrice(company.regularClose)}</div>
        <div>애프터마켓 종가 {formatPrice(company.nxtClose)}</div>
      </div>
    </div>
  )
}
