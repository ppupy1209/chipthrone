export const MIN_WATCHLIST: number
export const MAX_WATCHLIST: number
export function normalizeWatchlist(
  value: unknown,
  supportedCodes: string[],
  requiredCodes?: string[],
): string[]
