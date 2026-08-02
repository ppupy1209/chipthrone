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
  /** 금융위원회 확정 종가(원) — 공공데이터 미연동 시 null */
  regularClose: number | null
  /** 금융위원회 확정 종가 기준일(yyyy-MM-dd) */
  regularCloseDate: string | null
  /** 이전 API 호환 필드. 현재는 null */
  nxtClose: number | null
  /** 이전 API 호환 필드. 현재는 null */
  nxtCloseDate: string | null
  /** 금융위원회 확정 거래일 고가(원) */
  high: number | null
  /** 상장주식수(보통주) */
  sharesOutstanding: number
  /** 금융위원회 전일 확정 시가총액(원) */
  officialMarketCap: number | null
  /** 확정 종가와 같은 시점(정규장 마감)의 추정가(원) */
  officialCloseEstimate: number | null
  /** 확정 종가 대비 같은 시점 추정가의 괴리율(%) */
  officialDivergencePct: number | null
  market: 'KRX' | 'US'
  source: 'HYPERLIQUID'
  status: 'ESTIMATE'
}

export type MarketSnapshot = {
  mode: MarketMode
  /** 스냅샷 시각 (ISO-8601) */
  at: string
  /** USD/KRW 환율 — 추정가(달러 환산)에 사용 */
  fxRate: number
  fxAsOfDate: string | null
  fxSource: 'KOREA_EXIMBANK' | 'CONFIG_FALLBACK'
  stocks: Company[]
}

export type SupportedAsset = {
  code: string
  name: string
  market: 'KRX' | 'US'
}
