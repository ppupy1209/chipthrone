import type { MarketSnapshot, SupportedAsset } from '../types'

export const fallbackAssets: SupportedAsset[] = [
  { code: '005930', name: '삼성전자', market: 'KRX' },
  { code: '000660', name: 'SK하이닉스', market: 'KRX' },
  { code: 'SNDK', name: '샌디스크', market: 'US' },
  { code: 'MU', name: '마이크론', market: 'US' },
  { code: 'AVGO', name: '브로드컴', market: 'US' },
  { code: 'AMD', name: 'AMD', market: 'US' },
  { code: 'ASML', name: 'ASML', market: 'US' },
  { code: 'AAPL', name: '애플', market: 'US' },
  { code: 'MSFT', name: '마이크로소프트', market: 'US' },
  { code: 'GOOGL', name: '알파벳', market: 'US' },
  { code: 'AMZN', name: '아마존', market: 'US' },
  { code: 'NVDA', name: '엔비디아', market: 'US' },
  { code: 'META', name: '메타', market: 'US' },
  { code: 'TSLA', name: '테슬라', market: 'US' },
  { code: 'TSM', name: 'TSMC ADR', market: 'US' },
  { code: 'SKHY', name: 'SK하이닉스 ADR', market: 'US' },
]

// 첫 SSE 응답 전 상태. 예전 가격을 목업으로 보여 주면 실제 시세처럼 읽히므로 비워 둔다.
export const emptySnapshot: MarketSnapshot = {
  mode: 'ESTIMATE',
  at: '',
  stocks: [],
}
