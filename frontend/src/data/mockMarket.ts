import type { MarketSnapshot } from '../types'

// 목 데이터 — 이후 백엔드 SSE(/api/stream) 로 교체 예정.
// 상장주식수(보통주)는 구현 시 KRX 기준으로 재검증 필요.
export const mockSnapshot: MarketSnapshot = {
  mode: 'ESTIMATE',
  at: new Date().toISOString(),
  fxRate: 1390,
  samsung: {
    code: '005930',
    name: '삼성전자',
    color: 'blue',
    logo: '/logos/samsung.svg',
    price: 93000,
    changePct: -0.8,
    regularClose: 93500,
    nxtClose: 93200,
    sharesOutstanding: 5_919_637_922,
  },
  hynix: {
    code: '000660',
    name: 'SK하이닉스',
    color: 'red',
    logo: '/logos/skhynix.svg',
    price: 752000,
    changePct: 1.4,
    regularClose: 745000,
    nxtClose: 749000,
    sharesOutstanding: 728_002_365,
  },
}
