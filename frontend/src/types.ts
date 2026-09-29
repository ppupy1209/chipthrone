export type MarketMode = 'REGULAR' | 'NXT' | 'PREMARKET' | 'ESTIMATE'

export type Company = {
  code: string
  name: string
  color: 'blue' | 'red' | 'emerald' | 'violet'
  /** 로고 이미지 경로 */
  logo: string
  /** 현재가(원) — 모드에 따라 실제가 또는 추정가 */
  price: number
  /** Hyperliquid 추정가(달러) */
  priceUsd: number
  /** Hyperliquid 전일 기준가 대비 등락률(%) */
  changePct: number
  /** 이전 API 호환 필드. 현재는 null */
  nxtClose: number | null
  /** 이전 API 호환 필드. 현재는 null */
  nxtCloseDate: string | null
  /** 상장주식수(보통주) */
  sharesOutstanding: number
  /** 미국 종목 전용. 직전 정규장 마감(16:00 ET) 시점 추정가(달러) */
  sessionCloseUsd: number | null
  /** 미국 종목 전용. 그 마감 기준일(ET 기준 yyyy-MM-dd) */
  sessionCloseDate: string | null
  market: 'KRX' | 'US'
  source: 'HYPERLIQUID'
  status: 'ESTIMATE'
}

export type MarketSnapshot = {
  mode: MarketMode
  /** 스냅샷 시각 (ISO-8601) */
  at: string
  stocks: Company[]
}

/**
 * 화면의 한 자리. 코드와 이름은 처음부터 알지만 시세(company)는 첫 SSE 응답이 와야 채워진다.
 * company가 없으면 숫자 자리에 "-"를 보여 준다.
 */
export type QuoteSlot = {
  code: string
  name: string
  company?: Company
}

export type SupportedAsset = {
  code: string
  name: string
  market: 'KRX' | 'US'
}
