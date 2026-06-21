export type MarketMode = 'REGULAR' | 'NXT' | 'ESTIMATE'

export type Company = {
  code: string
  name: string
  /** 'blue' = 삼성전자, 'red' = SK하이닉스 */
  color: 'blue' | 'red'
  /** 현재가(원) */
  price: number
  /** 전일 대비 등락률(%) */
  changePct: number
  /** 상장주식수(보통주) */
  sharesOutstanding: number
}

export type MarketSnapshot = {
  mode: MarketMode
  /** 스냅샷 시각 (ISO-8601) */
  at: string
  samsung: Company
  hynix: Company
}
