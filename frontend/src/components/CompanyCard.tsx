import type { Company } from '../types'
import {
  formatCap,
  formatDateWithDay,
  formatPct,
  formatPrice,
  formatUsd,
  marketCap,
  officialMarketCap,
} from '../lib/marketCap'
import { AnimatedNumber } from './AnimatedNumber'

/** 괴리율이 어떤 두 값을 맞댄 것인지 보여주는 hover 설명. */
function divergenceHint(company: Company): string | undefined {
  if (company.officialCloseEstimate == null || company.regularClose == null) return undefined
  const date = company.regularCloseDate ?? ''
  return `${date} 15:30 추정 ${formatPrice(company.officialCloseEstimate)}원 vs 확정 종가 ${formatPrice(company.regularClose)}원`
}

const COLOR = {
  blue: { top: 'border-t-blue-500', cap: 'text-blue-600' },
  red: { top: 'border-t-red-500', cap: 'text-red-600' },
  emerald: { top: 'border-t-emerald-500', cap: 'text-emerald-600' },
  violet: { top: 'border-t-violet-500', cap: 'text-violet-600' },
} as const

function Divergence({ value, label, hint }: { value: number | null; label: string; hint?: string }) {
  if (value == null) return null
  const tone = value >= 0 ? 'text-red-600' : 'text-blue-600'
  return (
    <div className="mt-2 flex items-center justify-between text-[11px]" title={hint}>
      <span className="text-neutral-400">{label}</span>
      <span className={`font-medium tabular-nums ${tone}`}>{formatPct(value)}</span>
    </div>
  )
}

export function OfficialCompanyCard({ company, isLeader }: { company: Company; isLeader: boolean }) {
  const c = COLOR[company.color]
  const close = company.regularClose
  return (
    <div data-testid="official-company-card" className={`relative rounded-xl border border-t-[3px] border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-neutral-900 ${c.top}`}>
      {isLeader && (
        <span className="absolute -top-2.5 right-3.5 rounded-md bg-amber-100 px-2 py-1 text-[11px] font-medium text-amber-700">
          ♛ 현재 왕좌
        </span>
      )}
      <div className="flex items-baseline gap-2">
        <span className="text-[15px] font-medium">{company.name}</span>
        <span className="text-[11px] text-neutral-400">{company.code}</span>
      </div>
      {close != null ? (
        <>
          <div className="mt-3 flex items-baseline gap-1.5">
            <AnimatedNumber value={close} format={formatPrice} className="text-2xl font-semibold tabular-nums" />
            <span className="text-xs text-neutral-400">원</span>
          </div>
          <div className="mt-3 flex items-center justify-between border-t border-neutral-100 pt-2.5 text-[13px] dark:border-neutral-800">
            <span className="text-neutral-500">확정 시가총액</span>
            <AnimatedNumber value={officialMarketCap(company)} format={formatCap} className={`font-medium tabular-nums ${c.cap}`} />
          </div>
          <Divergence
            value={company.officialDivergencePct}
            label="확정 종가 시점 추정 괴리율"
            hint={divergenceHint(company)}
          />
        </>
      ) : (
        <p className="mt-5 text-[12px] text-neutral-400">공공데이터 연결 후 표시됩니다.</p>
      )}
    </div>
  )
}

export function EstimateCompanyCard({ company, fxRate }: { company: Company; fxRate: number }) {
  const c = COLOR[company.color]
  return (
    <div data-testid="estimate-company-card" className={`rounded-xl border border-t-[3px] border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-neutral-900 ${c.top}`}>
      <div className="flex items-baseline gap-2">
        <span className="text-[15px] font-medium">{company.name}</span>
        <span className="text-[11px] text-neutral-400">{company.code}</span>
        <span className="ml-auto inline-flex items-center gap-1 text-[10px] text-amber-600 dark:text-amber-400">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-amber-400" />
          해외 파생 추정
        </span>
      </div>
      <div className="mt-3 flex items-baseline gap-1.5">
        <AnimatedNumber value={company.price} format={formatPrice} className="text-2xl font-semibold tabular-nums" />
        <span className="text-xs text-neutral-400">원</span>
      </div>
      <div className="mt-1 text-[12px] tabular-nums text-neutral-400">≈ {formatUsd(company.price, fxRate)}</div>
      <Divergence
        value={company.officialDivergencePct}
        label="확정 종가 시점 추정 괴리율"
        hint={divergenceHint(company)}
      />
    </div>
  )
}

export function UsCompanyCard({ company }: { company: Company }) {
  return (
    <div data-testid="us-company-card" className="rounded-xl border border-t-2 border-neutral-200 border-t-emerald-500 bg-white p-3 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex items-baseline gap-1.5">
        <span className="truncate text-[13px] font-medium">{company.name}</span>
        <span className="text-[9px] text-neutral-400">{company.code}</span>
      </div>
      <div className="mt-2 text-xl font-semibold tabular-nums">
        ${company.priceUsd.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </div>
      <div className="mt-0.5 text-[10px] text-neutral-400">≈ {formatPrice(company.price)}원</div>
      <div className="mt-2 flex items-center justify-between border-t border-neutral-100 pt-2 text-[11px] dark:border-neutral-800">
        <span className="text-neutral-500">추정 시가총액</span>
        <span className="font-medium tabular-nums text-emerald-600">{formatCap(marketCap(company))}</span>
      </div>
      <Divergence value={company.changePct} label="24시간 등락률" />
    </div>
  )
}

export function OfficialDate({ date }: { date: string | null }) {
  return <>{date ? formatDateWithDay(date) : '데이터 연결 대기'}</>
}
