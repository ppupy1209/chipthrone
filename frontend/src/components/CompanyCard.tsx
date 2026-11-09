import type { CSSProperties } from 'react'
import type { Company, QuoteSlot } from '../types'
import { brandColor } from '../lib/brandColor'
import {
  formatCap,
  formatDateWithDay,
  formatPct,
  formatPrice,
  formatUsdAmount,
  marketCap,
  sessionCloseChangePct,
} from '../lib/marketCap'
import { AnimatedNumber } from './AnimatedNumber'

/**
 * 미국 카드의 "종가 대비" 기준. 정규장 마감 시각의 추정가라는 점을 hover로 밝힌다.
 * 카드 폭 안에서 한 줄로 끝나야 하므로 "미국"·"정규장"처럼 섹션 제목으로 이미 아는 말은 뺀다.
 */
function sessionCloseHint(company: Company): string | undefined {
  if (company.sessionCloseUsd == null) return undefined
  return `${formatDateWithDay(company.sessionCloseDate)} 16:00 ET 마감 추정가 `
    + `${formatUsdAmount(company.sessionCloseUsd)} 대비`
}

/**
 * Hyperliquid 파생시장 추정값임을 알리는 배지. 국내·미국 추정 카드가 같이 쓴다.
 * 미국 카드는 타이포 스케일이 한 단계 작아 compact로 맞춘다.
 */
function EstimateBadge({ compact = false }: { compact?: boolean }) {
  return (
    <span
      className={`ml-auto inline-flex shrink-0 items-center gap-1 text-amber-600 dark:text-amber-400 ${
        compact ? 'text-[9px]' : 'text-[10px]'
      }`}
    >
      <span className={`motion-safe:animate-pulse rounded-full bg-amber-400 ${compact ? 'h-1 w-1' : 'h-1.5 w-1.5'}`} />
      해외 파생 추정
    </span>
  )
}

function Divergence({ value, label, hint }: { value: number | null; label: string; hint?: string }) {
  if (value == null) return null
  const tone = value >= 0 ? 'text-red-600' : 'text-blue-600'
  const row = (
    <div className="flex items-center justify-between text-[11px]">
      <span className={hint ? 'text-neutral-400 decoration-dotted underline underline-offset-[3px] decoration-neutral-300 dark:decoration-neutral-600' : 'text-neutral-400'}>
        {label}
      </span>
      <span className={`font-medium tabular-nums ${tone}`}>{formatPct(value)}</span>
    </div>
  )
  if (!hint) return <div className="mt-2">{row}</div>

  // 브라우저 기본 title 툴팁은 스타일을 못 준다. 카드와 같은 표면·테두리로 직접 그린다.
  //
  // focus-within이 아니라 :focus-visible을 쓰는 이유: 마우스로 클릭하면 포커스가 남아
  // 커서를 치워도 툴팁이 안 사라진다. :focus-visible은 키보드 이동에만 걸리므로
  // 키보드 접근성은 지키면서 클릭 후 툴팁이 붙어 있는 문제가 없다.
  return (
    <div className="group relative mt-2" tabIndex={0}>
      {row}
      <div
        role="tooltip"
        className="pointer-events-none absolute left-0 top-full z-20 mt-1.5 w-max max-w-full origin-top-left
                   translate-y-[-2px] scale-[0.98] rounded-lg border border-neutral-200 bg-white px-3 py-2
                   text-[10.5px] leading-[1.55] text-neutral-500 opacity-0 shadow-md shadow-neutral-900/[0.06]
                   transition duration-150 ease-out
                   group-hover:translate-y-0 group-hover:scale-100 group-hover:opacity-100
                   group-[:focus-visible]:translate-y-0 group-[:focus-visible]:scale-100 group-[:focus-visible]:opacity-100
                   dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-400 dark:shadow-black/30"
      >
        <span className="absolute -top-[4px] left-4 h-[7px] w-[7px] rotate-45 border-l border-t border-neutral-200 bg-white dark:border-neutral-700 dark:bg-neutral-800" />
        {hint}
      </div>
    </div>
  )
}

/** 국내 추정 카드. 왕좌 배지는 역전 대시보드와 같은 추정 시가총액 순위를 따른다. */
export function EstimateCompanyCard({ slot, isLeader }: { slot: QuoteSlot; isLeader: boolean }) {
  const { code, name, company } = slot
  const strip = code === '005930' ? 'border-t-blue-500' : 'border-t-red-500'
  return (
    <div data-testid="estimate-company-card" className={`relative rounded-xl border border-t-[3px] border-neutral-200 bg-white p-5 dark:border-x-neutral-800 dark:border-b-neutral-800 dark:bg-neutral-900 ${strip}`}>
      {isLeader && (
        <span className="absolute -top-2.5 right-3.5 rounded-md bg-amber-100 px-2 py-1 text-[11px] font-medium text-amber-700">
          ♛ 현재 왕좌
        </span>
      )}
      <div className="flex items-baseline gap-2">
        <span className="text-[15px] font-medium">{name}</span>
        <span className="text-[11px] text-neutral-400">{code}</span>
        <EstimateBadge />
      </div>
      <div className="mt-3 flex items-baseline gap-1.5">
        {company ? (
          <AnimatedNumber value={company.price} format={formatPrice} className="text-2xl font-semibold tabular-nums" />
        ) : (
          <span className="text-2xl font-semibold">-</span>
        )}
        <span className="text-xs text-neutral-400">원</span>
      </div>
      <div className="mt-1 text-[12px] tabular-nums text-neutral-400">{company ? `≈ ${formatUsdAmount(company.priceUsd)}` : '-'}</div>
      <div className="mt-3 flex items-center justify-between border-t border-neutral-100 pt-2.5 text-[13px] dark:border-neutral-800">
        <span className="text-neutral-500">추정 시가총액</span>
        {company ? <AnimatedNumber value={marketCap(company)} format={formatCap} className="font-medium tabular-nums" /> : '-'}
      </div>
    </div>
  )
}

export function UsCompanyCard({ slot }: { slot: QuoteSlot }) {
  const { code, name, company } = slot
  const brand = brandColor(code)
  return (
    <div
      data-testid="us-company-card"
      className="brand-strip rounded-xl border border-t-[3px] border-neutral-200 bg-white p-3 dark:border-neutral-800 dark:bg-neutral-900"
      style={{ '--brand': brand.light, '--brand-dark': brand.dark } as CSSProperties}
    >
      <div className="flex items-baseline gap-1.5">
        <span className="min-w-0 truncate text-[13px] font-medium">{name}</span>
        <span className="shrink-0 text-[9px] text-neutral-400">{code}</span>
        <EstimateBadge compact />
      </div>
      <div className="mt-2 text-xl font-semibold tabular-nums">
        {company ? formatUsdAmount(company.priceUsd) : '-'}
      </div>
      <div className="mt-0.5 text-[10px] tabular-nums">{company ? `≈ ${formatPrice(company.price)}원` : '-'}</div>
      <div className="mt-2 flex items-center justify-between border-t border-neutral-100 pt-2 text-[11px] dark:border-neutral-800">
        <span className="text-neutral-500">추정 시가총액</span>
        <span className="font-medium tabular-nums">{company ? formatCap(marketCap(company)) : '-'}</span>
      </div>
      {company?.sessionCloseUsd != null && (
        <Divergence
          value={sessionCloseChangePct(company)}
          label={`종가(${formatUsdAmount(company.sessionCloseUsd)}) 대비`}
          hint={sessionCloseHint(company)}
        />
      )}
    </div>
  )
}
