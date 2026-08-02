import type { MarketSnapshot, SupportedAsset } from '../types'

export const fallbackAssets: SupportedAsset[] = [
  { code: '005930', name: '삼성전자', market: 'KRX' },
  { code: '000660', name: 'SK하이닉스', market: 'KRX' },
  { code: 'SNDK', name: '샌디스크', market: 'US' },
  { code: 'MU', name: '마이크론', market: 'US' },
  { code: 'AVGO', name: '브로드컴', market: 'US' },
]

// SSE 첫 응답 전 레이아웃을 유지하는 개발용 초기값. 연결 상태는 별도로 "연결 중"으로 표시한다.
export const mockSnapshot: MarketSnapshot = {
  mode: 'ESTIMATE',
  at: new Date().toISOString(),
  fxRate: 1531,
  stocks: [
    {
      code: '005930',
      name: '삼성전자',
      color: 'blue',
      logo: '/logos/samsung.svg',
      price: 369500,
      changePct: 1.1,
      regularClose: 354000,
      regularCloseDate: '2026-06-19',
      nxtClose: 355500,
      nxtCloseDate: '2026-06-19',
      high: 357000,
      sharesOutstanding: 5_919_637_922,
      market: 'KRX',
      source: 'HYPERLIQUID',
      status: 'ESTIMATE',
    },
    {
      code: '000660',
      name: 'SK하이닉스',
      color: 'red',
      logo: '/logos/skhynix.svg',
      price: 2_929_400,
      changePct: 2.4,
      regularClose: 2_764_000,
      regularCloseDate: '2026-06-19',
      nxtClose: 2_780_000,
      nxtCloseDate: '2026-06-19',
      high: 2_799_000,
      sharesOutstanding: 728_002_365,
      market: 'KRX',
      source: 'HYPERLIQUID',
      status: 'ESTIMATE',
    },
  ],
}
