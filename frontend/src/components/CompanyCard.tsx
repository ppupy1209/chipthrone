import type { Company, MarketMode } from '../types'
import {
  computeChange,
  formatCap,
  formatChangeAmount,
  formatDateWithDay,
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

// 장 상황별 세션 표시(작은 원 + 멘트). 야간(추정)은 녹색 점이 깜빡이며 '실시간' 느낌.
const SESSION: Record<MarketMode, { dot: string; text: string; pulse?: boolean }> = {
  REGULAR: { dot: 'bg-emerald-500', text: '정규장' },
  NXT: { dot: 'bg-sky-500', text: '애프터마켓' },
  PREMARKET: { dot: 'bg-amber-500', text: '프리장', pulse: true },
  ESTIMATE: { dot: 'bg-emerald-500', text: '해외 실시간 추정가', pulse: true },
}

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
  // 야간(추정)·프리장은 해외 파생 기반 추정가 → 달러 환산 노출
  const showUsd = mode === 'ESTIMATE' || mode === 'PREMARKET'
  const change = computeChange(company, mode)
  const up = change.pct >= 0
  // 국장 관례: 상승=빨강, 하락=파랑
  const changeColor = up ? 'text-red-600' : 'text-blue-600'
  const session = SESSION[mode]
  // 표시 종가: 정규장 종가(KIS 기준 '종가') 우선, 없으면 애프터마켓 종가
  const closeValue = company.regularClose ?? company.nxtClose
  const closeDate =
    company.regularClose != null ? company.regularCloseDate : company.nxtCloseDate

  return (
    <div
      className={`relative rounded-xl border border-neutral-200 dark:border-neutral-800 border-t-[3px] ${c.top} bg-white dark:bg-neutral-900 p-5`}
    >
      {isLeader && (
        <span className="absolute -top-2.5 right-3.5 rounded-md bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-700">
          👑 현재 왕좌
        </span>
      )}

      {/* 헤더줄: 로고 + 종목명 + 코드 + (오른쪽) 세션 태그(작은 원 + 멘트, nowrap) */}
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
        <span className="ml-auto inline-flex items-center gap-1 whitespace-nowrap text-[11px] text-neutral-400">
          <span
            className={`h-1.5 w-1.5 rounded-full ${session.dot} ${
              session.pulse ? 'animate-pulse' : ''
            }`}
          />
          {session.text}
        </span>
      </div>

      {/* 가격줄: 가격+원(nowrap) + (오른쪽) 등락(nowrap) */}
      <div className="mt-3 flex items-baseline gap-2">
        <span className="flex items-baseline gap-1.5 whitespace-nowrap">
          <AnimatedNumber
            value={company.price}
            format={formatPrice}
            className="text-2xl font-semibold tabular-nums"
          />
          <span className="text-xs text-neutral-400">원</span>
        </span>
        <span
          className={`ml-auto whitespace-nowrap text-sm font-medium tabular-nums ${changeColor}`}
        >
          {up ? '▲' : '▼'} {formatPct(change.pct)}
          {change.hasBasis && (
            <span className="ml-1 text-[11px]">
              ({formatChangeAmount(change.amount)})
            </span>
          )}
        </span>
      </div>

      {/* 달러 환산(추정·프리장 시) */}
      {showUsd && (
        <div className="mt-1 text-[12px] text-neutral-400 tabular-nums">
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

      {/* 직전 완료 거래일 종가(애프터마켓 종가) + 고가 */}
      <div className="mt-2 space-y-0.5 text-[11px] text-neutral-400 tabular-nums">
        {closeValue != null && closeDate && (
          <div>
            {formatDateWithDay(closeDate)} 종가 : {formatPrice(closeValue)}원
          </div>
        )}
        {company.high != null && company.regularCloseDate && (
          <div>
            {formatDateWithDay(company.regularCloseDate)} 고가 :{' '}
            {formatPrice(company.high)}원
          </div>
        )}
      </div>
    </div>
  )
}
