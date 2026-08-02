import type { Company } from '../types'

/** 시가총액(원) = 현재가 × 상장주식수 */
export function marketCap(c: Company): number {
  return c.price * c.sharesOutstanding
}

export function officialMarketCap(c: Company): number {
  if (c.officialMarketCap != null) return c.officialMarketCap
  return (c.regularClose ?? 0) * c.sharesOutstanding
}

/** 시총을 "552.3조" 형식으로 */
export function formatCap(cap: number): string {
  return `${(cap / 1e12).toFixed(1)}조`
}

/** 가격을 정수 원 단위 "92,500" 형식으로 */
export function formatPrice(price: number): string {
  return Math.round(price).toLocaleString('ko-KR')
}

/** 등락률을 "+1.4%" / "-0.8%" 형식으로 */
export function formatPct(pct: number): string {
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}

const KOR_DAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

/** "yyyy-MM-dd" → "2026-06-19 (금)" (null이면 빈 문자열) */
export function formatDateWithDay(date: string | null): string {
  if (!date) return ''
  const parts = date.split('-')
  if (parts.length !== 3) return ''
  const [y, m, d] = parts.map((p) => parseInt(p, 10))
  if (!y || !m || !d) return ''
  const day = KOR_DAYS[new Date(y, m - 1, d).getDay()]
  const mm = String(m).padStart(2, '0')
  const dd = String(d).padStart(2, '0')
  return `${y}-${mm}-${dd} (${day})`
}

/** 원화가를 달러 환산해 "$66.91" 형식으로 */
export function formatUsd(priceKrw: number, fxRate: number): string {
  return `$${(priceKrw / fxRate).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

export type Comparison = {
  leader: Company
  challenger: Company
  leaderCap: number
  challengerCap: number
  /** 격차(원) */
  gap: number
  /** 격차 비율(%) — 도전자 시총 대비 */
  gapPct: number
  /** 도전자가 역전하려면 필요한 등락률(%) */
  reversalPct: number
  /** 도전자가 역전하려면 필요한 가격(원) */
  reversalPrice: number
}

/** 금융위원회 확정 시가총액 기준 왕좌 교체 조건. */
export function compareOfficial(a: Company, b: Company): Comparison | null {
  if (!a.regularClose || !b.regularClose) return null
  const capA = officialMarketCap(a)
  const capB = officialMarketCap(b)
  const [leader, challenger, leaderCap, challengerCap] =
    capA >= capB ? [a, b, capA, capB] : [b, a, capB, capA]
  const challengerClose = challenger.regularClose!
  const reversalPrice = leaderCap / challenger.sharesOutstanding
  return {
    leader,
    challenger,
    leaderCap,
    challengerCap,
    gap: leaderCap - challengerCap,
    gapPct: ((leaderCap - challengerCap) / challengerCap) * 100,
    reversalPct: (reversalPrice / challengerClose - 1) * 100,
    reversalPrice,
  }
}
