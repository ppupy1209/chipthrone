import type { Company } from '../types'

/** 시가총액(원) = 현재가 × 상장주식수 */
export function marketCap(c: Company): number {
  return c.price * c.sharesOutstanding
}

export function officialMarketCap(c: Company): number {
  if (c.officialMarketCap != null) return c.officialMarketCap
  return (c.regularClose ?? 0) * c.sharesOutstanding
}

/**
 * 확정 종가 대비 현재 추정가의 등락률(%). 증권앱이 보여주는 등락률과 같은 기준이다.
 * Hyperliquid `prevDayPx` 기준 24시간 등락률과는 기준점이 달라 값이 크게 벌어질 수 있다.
 * 확정 종가가 없으면(공공데이터 미연동) null.
 */
export function closeChangePct(c: Company): number | null {
  if (c.regularClose == null || c.regularClose <= 0) return null
  return (c.price / c.regularClose - 1) * 100
}

/**
 * 미국 종목의 직전 정규장 마감(16:00 ET) 대비 등락률(%).
 * 양쪽 다 달러라 환율 변동이 섞이지 않는다. 미국 현지에서 보는 등락률과 같은 기준이다.
 */
export function sessionCloseChangePct(c: Company): number | null {
  if (c.sessionCloseUsd == null || c.sessionCloseUsd <= 0) return null
  return (c.priceUsd / c.sessionCloseUsd - 1) * 100
}

/** 달러 금액을 "$183.52" 형식으로 */
export function formatUsdAmount(usd: number): string {
  return `$${usd.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
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
