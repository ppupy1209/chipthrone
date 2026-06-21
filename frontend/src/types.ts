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
  /** 등락률(%) — 기준은 changeBasis 참고 */
  changePct: number
  /** 등락률 기준 라벨 (예: "애프터마켓 대비", "전일 대비") */
  changeBasis: string
  /** 정규장 종가(원) — KIS 미연동 시 null */
  regularClose: number | null
  /** NXT 애프터마켓 종가(원) — KIS 미연동 시 null */
  nxtClose: number | null
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
