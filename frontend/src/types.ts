export type MarketMode = 'REGULAR' | 'NXT' | 'PREMARKET' | 'ESTIMATE'

export type Company = {
  code: string
  name: string
  /** 'blue' = 삼성전자, 'red' = SK하이닉스 */
  color: 'blue' | 'red'
  /** 로고 이미지 경로 */
  logo: string
  /** 현재가(원) — 모드에 따라 실제가 또는 추정가 */
  price: number
  /** 등락률(%) — 추정 모드는 애프터마켓/정규장 종가 대비 */
  changePct: number
  /** 정규장 종가(원) — KIS 미연동 시 null */
  regularClose: number | null
  /** 정규장 종가 기준일(yyyy-MM-dd) — KIS 미연동 시 null */
  regularCloseDate: string | null
  /** NXT 애프터마켓 종가(원) — KIS 미연동 시 null */
  nxtClose: number | null
  /** NXT 애프터마켓 종가 기준일(yyyy-MM-dd) — KIS 미연동 시 null */
  nxtCloseDate: string | null
  /** 직전 완료 거래일 정규장 고가(원) — KIS 미연동 시 null */
  high: number | null
  /** 상장주식수(보통주) */
  sharesOutstanding: number
}

export type MarketSnapshot = {
  mode: MarketMode
  /** 스냅샷 시각 (ISO-8601) */
  at: string
  /** USD/KRW 환율 — 추정가(달러 환산)에 사용 */
  fxRate: number
  samsung: Company
  hynix: Company
}

/** 증권사 1건의 투자의견 리포트 */
export type OpinionReport = {
  /** 발표일 (yyyy-MM-dd) */
  date: string
  /** 증권사명 */
  broker: string
  /** 현재 투자의견 (예: '매수') */
  opinion: string
  /** 직전 투자의견 */
  prevOpinion: string
  /** 목표가(원) — 없으면 null */
  targetPrice: number | null
}

/** 종목 컨센서스 요약 */
export type OpinionConsensus = {
  /** 평균 목표가(원) — 없으면 null */
  avgTargetPrice: number | null
  /** 추정기관수(증권사 수) */
  institutionCount: number
  /** 매수계열 의견 수 */
  buy: number
  /** 중립 의견 수 */
  hold: number
  /** 매도계열 의견 수 */
  sell: number
  /** 컨센서스 점수 1~5 (강력매도 1 … 강력매수 5) — 없으면 null */
  score: number | null
}

export type StockOpinion = {
  code: string
  name: string
  consensus: OpinionConsensus
  reports: OpinionReport[]
}

export type OpinionsResponse = {
  /** 기준일 (yyyy-MM-dd) */
  asOf: string
  stocks: StockOpinion[]
}
