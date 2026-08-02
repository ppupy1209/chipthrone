import type { MarketMode } from '../types'

// 헤더 모드 표시와 카드 세션 태그가 "같은 모드면 같은 점 색"을 쓰도록 한 곳에서 관리.
// 장 마감 추정치는 회색 고정점으로 실시간 상태와 구분한다.
export const MODE_DOT: Record<MarketMode, { dot: string; pulse: boolean }> = {
  REGULAR: { dot: 'bg-[#1d9e75]', pulse: false },
  NXT: { dot: 'bg-[#2f7fb3]', pulse: false },
  PREMARKET: { dot: 'bg-[#c2912e]', pulse: true },
  ESTIMATE: { dot: 'bg-neutral-400', pulse: false },
}
