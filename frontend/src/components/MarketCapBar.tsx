import type { Company } from '../types'
import { formatCap, marketCap } from '../lib/marketCap'
import { AnimatedNumber } from './AnimatedNumber'

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
    <div className="rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-5">
      <div className="mb-2.5 text-center text-[10px] tracking-[0.14em] text-neutral-400">
        시총 비교
      </div>
      <div className="flex items-baseline justify-between text-[13px]">
        <span className="font-medium text-blue-600">
          삼성전자{' '}
          <AnimatedNumber
            value={capS}
            format={formatCap}
            className="font-normal text-neutral-500 tabular-nums"
          />
        </span>
        <span className="font-medium text-red-600">
          <AnimatedNumber
            value={capH}
            format={formatCap}
            className="font-normal text-neutral-500 tabular-nums"
          />{' '}
          SK하이닉스
        </span>
      </div>
      <div className="my-2 flex h-1.5 overflow-hidden rounded-full">
        <div
          className="bg-blue-500"
          style={{ width: `${samsungW}%`, transition: 'width 0.5s ease-out' }}
        />
        <div
          className="bg-red-500"
          style={{ width: `${100 - samsungW}%`, transition: 'width 0.5s ease-out' }}
        />
      </div>
      <div className="text-center text-[11px] text-neutral-400 tabular-nums">
        격차{' '}
        <AnimatedNumber
          value={gap}
          format={formatCap}
          className="font-medium text-neutral-600 dark:text-neutral-300"
        />{' '}
        · {gapPct.toFixed(1)}%
      </div>
    </div>
  )
}
