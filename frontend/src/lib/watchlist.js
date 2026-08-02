export const MIN_WATCHLIST = 2
/** 지원 종목 전체 수. 사용자에게 노출하는 선택 제한은 없고, 백엔드 구독 상한과 맞춘 안전값이다. */
export const MAX_WATCHLIST = 16

/** 저장값을 현재 지원 목록에 맞춰 검증하고 최소 선택 수를 채운다. */
export function normalizeWatchlist(value, supportedCodes, requiredCodes = []) {
  const supported = new Set(supportedCodes)
  const selected = [...new Set(requiredCodes.filter((code) => supported.has(code)))]
  const stored = Array.isArray(value)
    ? value.filter((code) => typeof code === 'string' && supported.has(code))
    : []
  for (const code of stored) {
    if (!selected.includes(code)) selected.push(code)
  }
  for (const code of supportedCodes) {
    if (selected.length >= MIN_WATCHLIST) break
    if (!selected.includes(code)) selected.push(code)
  }
  return selected.slice(0, MAX_WATCHLIST)
}
