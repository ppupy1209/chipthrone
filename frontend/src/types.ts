export type MarketMode = 'REGULAR' | 'NXT' | 'ESTIMATE'

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
