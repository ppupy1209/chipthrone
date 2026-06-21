import { useEffect, useState } from 'react'
import type { MarketSnapshot } from '../types'
import { mockSnapshot } from '../data/mockMarket'

// 현재는 목 데이터를 가볍게 흔들어 "실시간" 느낌을 준다.
// TODO: 백엔드 SSE(/api/stream) 구독으로 교체.
function jitter(price: number): number {
  const delta = price * (Math.random() - 0.5) * 0.002 // ±0.1%
  return Math.round((price + delta) / 100) * 100
}

export function useMarketData(): MarketSnapshot {
  const [snapshot, setSnapshot] = useState<MarketSnapshot>(mockSnapshot)

  useEffect(() => {
    const id = setInterval(() => {
      setSnapshot((prev) => ({
        ...prev,
        at: new Date().toISOString(),
        samsung: { ...prev.samsung, price: jitter(prev.samsung.price) },
        hynix: { ...prev.hynix, price: jitter(prev.hynix.price) },
      }))
    }, 2000)
    return () => clearInterval(id)
  }, [])

  return snapshot
}
