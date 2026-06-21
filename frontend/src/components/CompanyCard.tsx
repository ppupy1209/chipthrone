import type { Company, MarketMode } from '../types'
import {
  formatCap,
  formatPct,
  formatPrice,
  formatUsd,
  marketCap,
} from '../lib/marketCap'
import { AnimatedNumber } from './AnimatedNumber'

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
  // 국장 관례: 상승=빨강, 하락=파랑
  const changeColor = up ? 'text-red-600' : 'text-blue-600'

  return (
    <div
      className={`relative rounded-xl border border-neutral-200 dark:border-neutral-800 border-t-[3px] ${c.top} bg-white dark:bg-neutral-900 p-5`}
    >
      {isLeader && (
        <span className="absolute -top-2.5 right-3.5 rounded-md bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-700">
          👑 현재 왕좌
        </span>
      )}

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
        <AnimatedNumber
          value={company.price}
          format={formatPrice}
          className="text-2xl font-semibold tabular-nums"
        />
        <span className="text-xs text-neutral-400">원</span>
        <span className="ml-auto flex flex-col items-end leading-tight">
          <span className="text-[10px] text-neutral-400">{company.changeBasis}</span>
          <span className={`text-sm font-medium tabular-nums ${changeColor}`}>
            {up ? '▲' : '▼'} {formatPct(company.changePct)}
          </span>
        </span>
      </div>

      {isEstimate && (
        <div className="mt-0.5 text-[12px] text-neutral-400 tabular-nums">
          ≈ {formatUsd(company.price, fxRate)}
        </div>
      )}

      <div className="mt-3 flex items-center justify-between border-t border-neutral-100 dark:border-neutral-800 pt-2.5 text-[13px]">
        <span className="text-neutral-500">시가총액</span>
        <AnimatedNumber
          value={marketCap(company)}
          format={formatCap}
          className={`font-medium tabular-nums ${c.cap}`}
        />
      </div>

      <div className="mt-2 space-y-0.5 text-right text-[11px] text-neutral-400 tabular-nums">
        <div>
          정규장 종가{' '}
          {company.regularClose != null ? formatPrice(company.regularClose) : '—'}
        </div>
        <div>
          애프터마켓 종가{' '}
          {company.nxtClose != null ? formatPrice(company.nxtClose) : '—'}
        </div>
      </div>
    </div>
  )
}
