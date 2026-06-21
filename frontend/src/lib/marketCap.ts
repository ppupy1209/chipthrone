import type { Company, MarketMode } from '../types'

/** 시가총액(원) = 현재가 × 상장주식수 */
export function marketCap(c: Company): number {
  return c.price * c.sharesOutstanding
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

/** "yyyy-MM-dd" → "M/D" (null이면 빈 문자열) */
export function formatDateMMDD(date: string | null): string {
  if (!date) return ''
  const parts = date.split('-')
  if (parts.length !== 3) return ''
  return `${parseInt(parts[1], 10)}/${parseInt(parts[2], 10)}`
}

export type DisplayChange = {
  /** 등락률(%) */
  pct: number
  /** 등락 금액(원) */
  amount: number
  /** 기준 문구 (예: "6/19 애프터마켓 종가 대비"). 기준값 없으면 '' */
  label: string
  /** 기준 종가가 있어 금액/문구를 표시할 수 있는지 */
  hasBasis: boolean
}

/**
 * 시장 상황별 등락 기준:
 * - 애프터마켓(NXT) 시간 → 정규장 종가 대비
 * - 정규장 / 야간 / 주말 → 애프터마켓 종가 대비
 */
export function computeChange(company: Company, mode: MarketMode): DisplayChange {
  const useRegular = mode === 'NXT'
  const close = useRegular ? company.regularClose : company.nxtClose
  const date = useRegular ? company.regularCloseDate : company.nxtCloseDate
  const basisName = useRegular ? '정규장 종가 대비' : '애프터마켓 종가 대비'

  if (close != null && close !== 0) {
    const datePart = date ? `${formatDateMMDD(date)} ` : ''
    return {
      pct: ((company.price - close) / close) * 100,
      amount: company.price - close,
      label: `${datePart}${basisName}`,
      hasBasis: true,
    }
  }
  // 기준 종가가 없으면 백엔드 등락률로 폴백, 문구/금액 생략
  return { pct: company.changePct, amount: 0, label: '', hasBasis: false }
}

/** 등락 금액을 "+18,855원" / "-3,200원" 형식으로 */
export function formatChangeAmount(amount: number): string {
  const sign = amount > 0 ? '+' : amount < 0 ? '-' : ''
  return `${sign}${Math.abs(Math.round(amount)).toLocaleString('ko-KR')}원`
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
