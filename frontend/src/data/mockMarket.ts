import type { MarketSnapshot } from '../types'

// 백엔드 SSE 연결 전/실패 시 사용하는 폴백 스냅샷.
// 실제 값은 백엔드(/api/stream)에서 내려온다.
export const mockSnapshot: MarketSnapshot = {
  mode: 'ESTIMATE',
  at: new Date().toISOString(),
  fxRate: 1531,
  samsung: {
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
    sharesOutstanding: 5_919_637_922,
  },
  hynix: {
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
    sharesOutstanding: 728_002_365,
  },
}
