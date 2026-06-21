import type { Company } from '../types'

/** 시가총액(원) = 현재가 × 상장주식수 */
export function marketCap(c: Company): number {
  return c.price * c.sharesOutstanding
}

/** 시총을 "552.3조" 형식으로 */
export function formatCap(cap: number): string {
  return `${(cap / 1e12).toFixed(1)}조`
}

/** 가격을 "92,500" 형식으로 */
export function formatPrice(price: number): string {
  return price.toLocaleString('ko-KR')
}

/** 등락률을 "+1.4%" / "-0.8%" 형식으로 */
export function formatPct(pct: number): string {
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
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

/** 두 회사의 시총을 비교해 1위/도전자와 역전 조건을 계산 */
export function compare(a: Company, b: Company): Comparison {
  const capA = marketCap(a)
  const capB = marketCap(b)
  const [leader, challenger, leaderCap, challengerCap] =
    capA >= capB ? [a, b, capA, capB] : [b, a, capB, capA]

  const gap = leaderCap - challengerCap
  const gapPct = (gap / challengerCap) * 100
  // 도전자가 1위 시총에 도달하기 위한 가격/등락률
  const reversalPrice = leaderCap / challenger.sharesOutstanding
  const reversalPct = (reversalPrice / challenger.price - 1) * 100

  return {
    leader,
    challenger,
    leaderCap,
    challengerCap,
    gap,
    gapPct,
    reversalPct,
    reversalPrice,
  }
}
