/**
 * 전광판에서 두 회사를 구분하는 색.
 * 등락 색(up 빨강, down 파랑)과 겹치지 않게 SK하이닉스는 빨강 대신 주황 계열로 둔다.
 */
const TEAM_TONE: Record<string, { text: string; bar: string }> = {
  '005930': { text: 'text-samsung', bar: 'bg-samsung' },
  '000660': { text: 'text-sk', bar: 'bg-sk' },
}
const FALLBACK_TONE = { text: 'text-ink', bar: 'bg-ink-2' }

export function teamTone(code: string): { text: string; bar: string } {
  return TEAM_TONE[code] ?? FALLBACK_TONE
}
