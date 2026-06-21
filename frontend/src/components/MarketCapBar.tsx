import type { Company } from '../types'
import { formatCap, marketCap } from '../lib/marketCap'

// 좌우 위치는 항상 고정: 삼성(왼쪽/블루), 하이닉스(오른쪽/레드).
export function MarketCapBar({
  samsung,
  hynix,
}: {
  samsung: Company
  hynix: Company
}) {
  const capS = marketCap(samsung)
  const capH = marketCap(hynix)
  const total = capS + capH
  const samsungW = (capS / total) * 100

  const gap = Math.abs(capS - capH)
  const gapPct = (gap / Math.min(capS, capH)) * 100

  return (
    <div className="rounded-xl bg-neutral-50 dark:bg-neutral-900 p-5">
      <div className="mb-2 flex justify-between text-[13px]">
        <span className="font-medium text-blue-600">
          삼성전자 {formatCap(capS)}
        </span>
        <span className="text-neutral-400">시총 비교</span>
        <span className="font-medium text-red-600">
          {formatCap(capH)} SK하이닉스
        </span>
      </div>
      <div className="flex h-3.5 overflow-hidden rounded-md">
        <div className="bg-blue-500" style={{ width: `${samsungW}%` }} />
        <div className="bg-red-500" style={{ width: `${100 - samsungW}%` }} />
      </div>
      <div className="mt-2 text-center text-xs text-neutral-400">
        격차{' '}
        <span className="font-medium text-neutral-700 dark:text-neutral-200 tabular-nums">
          {formatCap(gap)} ({gapPct.toFixed(1)}%)
        </span>{' '}
        · 역전 초읽기
      </div>
    </div>
  )
}
