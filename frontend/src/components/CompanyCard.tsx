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
  emerald: { top: 'border-t-emerald-500', cap: 'text-emerald-600' },
  violet: { top: 'border-t-violet-500', cap: 'text-violet-600' },
} as const

const SESSION_TEXT: Record<MarketMode, string> = {
  REGULAR: '실시간 · 정규장',
  NXT: '실시간 · 애프터마켓',
  PREMARKET: '실시간 · 프리마켓',
  ESTIMATE: '장 마감 · 추정 시세',
}

function quoteLabel(company: Company, mode: MarketMode): string {
  if (company.source === 'ALPACA_IEX') {
    return company.status === 'LIVE' ? 'IEX 실시간 · 미장' : '미장 마감 · IEX 종가'
  }
  if (company.source === 'HYPERLIQUID') return '해외 파생 · 추정 시세'
  return SESSION_TEXT[mode]
}

export function CompanyCard({
  company,
  isLeader,
  mode,
  fxRate,
  compact = false,
}: {
  company: Company
  isLeader: boolean
  mode: MarketMode
  fxRate: number
  compact?: boolean
}) {
  const c = COLOR[company.color]
  // 달러 환산은 해외 추정치(야간·주말 = ESTIMATE)일 때만. 프리마켓/정규장/애프터마켓은 국내 실거래가.
  const showUsd = company.market === 'US' || company.source === 'HYPERLIQUID'
  const change = computeChange(company)
  const up = change.pct >= 0
  // 국장 관례: 상승=빨강, 하락=파랑
  const changeColor = up ? 'text-red-600' : 'text-blue-600'
  const sessionText = quoteLabel(company, mode)
  const sessionDot = company.status === 'LIVE'
    ? 'bg-emerald-500 animate-pulse'
    : company.status === 'CLOSED' ? 'bg-neutral-400' : 'bg-amber-400'
  // 표시 종가: 정규장 종가(KIS 기준 '종가') 우선, 없으면 애프터마켓 종가
  const closeValue = company.regularClose ?? company.nxtClose
  const closeDate =
    company.regularClose != null ? company.regularCloseDate : company.nxtCloseDate

  return (
    <div
      data-testid="company-card"
      className={`relative rounded-xl border border-neutral-200 dark:border-neutral-800 ${compact ? 'border-t-2 p-3' : 'border-t-[3px] p-5'} ${c.top} bg-white dark:bg-neutral-900`}
    >
      {isLeader && (
        <span className="absolute -top-2.5 right-3.5 inline-flex items-center gap-1.5 rounded-md bg-amber-100 px-2 py-1 text-[11px] font-medium text-amber-700">
          <svg viewBox="11 17 42 43" className="h-4 w-4" aria-hidden="true">
            <path d="M12 50 L15 23 L24 33 L32 18 L40 33 L49 23 L52 50 Z" fill="#b8924a" />
            <rect x="13" y="52" width="38" height="7" rx="2.5" fill="#b8924a" />
          </svg>
          현재 왕좌
        </span>
      )}

      {/* 헤더줄: 종목명 + 코드 + (오른쪽) 세션 태그(작은 원 + 멘트, nowrap) */}
      <div className="flex items-center gap-2">
        <span className={`${compact ? 'text-[13px]' : 'text-[15px]'} font-medium leading-tight`}>
          {company.name}
        </span>
        <span className={`${compact ? 'text-[10px]' : 'text-[11px]'} text-neutral-400`}>{company.code}</span>
        <span className={`ml-auto inline-flex items-center gap-1 whitespace-nowrap text-neutral-400 ${compact ? 'text-[9px]' : 'text-[11px]'}`}>
          <span
            className={`h-1.5 w-1.5 rounded-full ${sessionDot}`}
          />
          {sessionText}
        </span>
      </div>

      {/* 가격줄: 가격+원(nowrap) + (오른쪽) 등락(nowrap) */}
      <div className={`${compact ? 'mt-2' : 'mt-3'} flex items-baseline gap-2`}>
        <span className="flex items-baseline gap-1.5 whitespace-nowrap">
          <AnimatedNumber
            value={company.price}
            format={formatPrice}
            className={`${compact ? 'text-xl' : 'text-2xl'} font-semibold tabular-nums`}
          />
          <span className={`${compact ? 'text-[10px]' : 'text-xs'} text-neutral-400`}>원</span>
        </span>
        <span
          className={`ml-auto whitespace-nowrap font-medium tabular-nums ${compact ? 'text-[11px]' : 'text-sm'} ${changeColor}`}
        >
          {up ? '▲' : '▼'} {formatPct(change.pct)}
          {change.hasBasis && (
            <span className="ml-1 text-[11px]">
              ({formatChangeAmount(change.amount)})
            </span>
          )}
        </span>
      </div>

      {/* 달러 환산(추정·프리마켓 시) */}
      {showUsd && (
        <div className={`${compact ? 'mt-0.5 text-[10px]' : 'mt-1 text-[12px]'} text-neutral-400 tabular-nums`}>
          ≈ {formatUsd(company.price, fxRate)}
        </div>
      )}

      <div className={`${compact ? 'mt-2 pt-2 text-[11px]' : 'mt-3 pt-2.5 text-[13px]'} flex items-center justify-between border-t border-neutral-100 dark:border-neutral-800`}>
        <span className="text-neutral-500">시가총액</span>
        <AnimatedNumber
          value={marketCap(company)}
          format={formatCap}
          className={`font-medium tabular-nums ${c.cap}`}
        />
      </div>

      {/* 직전 완료 거래일 종가(애프터마켓 종가) + 고가 */}
      {!compact && <div className="mt-2 space-y-0.5 text-[11px] text-neutral-400 tabular-nums">
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
      </div>}
    </div>
  )
}
