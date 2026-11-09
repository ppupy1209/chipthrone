/**
 * 미국 카드 상단 스트립에 쓰는 종목별 시그니처 색.
 *
 * light는 각 사의 대표 색, dark는 어두운 배경에서 대비를 확보한 변형이다.
 * 애플처럼 대표 색이 검정인 종목은 다크 모드에서 그대로 쓰면 배경에 묻히므로
 * 두 값을 따로 둔다. 값 하나만 고치면 되도록 종목당 한 줄로 유지한다.
 */
export type BrandColor = { light: string; dark: string }

const BRAND: Record<string, BrandColor> = {
  SNDK: { light: '#E31B23', dark: '#FF5A61' }, // 샌디스크 레드
  MU: { light: '#0072CE', dark: '#4FA8E8' }, // 마이크론 블루
  AVGO: { light: '#CC0000', dark: '#F05252' }, // 브로드컴 레드
  AMD: { light: '#ED1C24', dark: '#FF6B72' }, // AMD 레드
  ASML: { light: '#1E22AA', dark: '#8C90FF' }, // ASML 블루
  AAPL: { light: '#000000', dark: '#F5F5F7' }, // 애플 블랙 / 다크에서는 실버
  MSFT: { light: '#0078D4', dark: '#4CADF5' }, // 마이크로소프트 블루
  GOOGL: { light: '#4285F4', dark: '#8AB4F8' }, // 구글 블루
  AMZN: { light: '#FF9900', dark: '#FFB84D' }, // 아마존 오렌지
  NVDA: { light: '#76B900', dark: '#9FE015' }, // 엔비디아 그린
  META: { light: '#0064E0', dark: '#5B9BFF' }, // 메타 블루
  TSLA: { light: '#E82127', dark: '#FF5C61' }, // 테슬라 레드
  TSM: { light: '#C8102E', dark: '#F2536C' }, // TSMC 레드
  SKHY: { light: '#EA002C', dark: '#FF5470' }, // SK 레드
}

/** 목록에 없는 종목이 늘어나도 카드가 깨지지 않도록 중립색으로 떨어뜨린다. */
const FALLBACK: BrandColor = { light: '#525252', dark: '#A3A3A3' }

export function brandColor(code: string): BrandColor {
  return BRAND[code] ?? FALLBACK
}
