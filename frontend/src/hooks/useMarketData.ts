import { useEffect, useState } from 'react'
import type { Company, MarketSnapshot } from '../types'
import { mockSnapshot } from '../data/mockMarket'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

// 종목코드별 정적 메타(색상/로고) — 백엔드는 시세만 내려준다.
const META: Record<string, Pick<Company, 'color' | 'logo'>> = {
  '005930': { color: 'blue', logo: '/logos/samsung.svg' },
  '000660': { color: 'red', logo: '/logos/skhynix.svg' },
}

type BackendStock = {
  code: string
  name: string
  priceKrw: number
  priceUsd: number
  changePct: number
  changeBasis: string
  sharesOutstanding: number
  marketCap: number
  regularClose: number | null
  nxtClose: number | null
}

type BackendSnapshot = {
  mode: MarketSnapshot['mode']
  at: string
  fxRate: number
  stocks: BackendStock[]
}

function toCompany(s: BackendStock): Company {
  return {
    code: s.code,
    name: s.name,
    color: META[s.code]?.color ?? 'blue',
    logo: META[s.code]?.logo ?? '',
    price: s.priceKrw,
    changePct: s.changePct,
    changeBasis: s.changeBasis,
    regularClose: s.regularClose,
    nxtClose: s.nxtClose,
    sharesOutstanding: s.sharesOutstanding,
  }
}

function mapSnapshot(d: BackendSnapshot): MarketSnapshot {
  const byCode = Object.fromEntries(d.stocks.map((s) => [s.code, s]))
  return {
    mode: d.mode,
    at: d.at,
    fxRate: d.fxRate,
    samsung: toCompany(byCode['005930']),
    hynix: toCompany(byCode['000660']),
  }
}

// 백엔드 SSE(/api/stream)를 구독한다. 연결 실패 시 마지막(또는 목) 스냅샷 유지.
export function useMarketData(): MarketSnapshot {
  const [snapshot, setSnapshot] = useState<MarketSnapshot>(mockSnapshot)

  useEffect(() => {
    const es = new EventSource(`${API_BASE}/api/stream`)
    const onQuotes = (e: MessageEvent) => {
      try {
        setSnapshot(mapSnapshot(JSON.parse(e.data) as BackendSnapshot))
      } catch {
        // 파싱 실패 시 직전 스냅샷 유지
      }
    }
    es.addEventListener('quotes', onQuotes as EventListener)
    // onerror 시 EventSource가 자동 재연결한다. 별도 처리 없이 마지막 값 유지.
    return () => es.close()
  }, [])

  return snapshot
}
