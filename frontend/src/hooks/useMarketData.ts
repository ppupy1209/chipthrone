import { useEffect, useState } from 'react'
import type { Company, ConnectionState, MarketSnapshot } from '../types'
import { mockSnapshot } from '../data/mockMarket'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const META: Record<string, Pick<Company, 'color' | 'logo'>> = {
  '005930': { color: 'blue', logo: '/logos/samsung.svg' },
  '000660': { color: 'red', logo: '/logos/skhynix.svg' },
}
const COLORS: Company['color'][] = ['blue', 'red', 'emerald', 'violet']

type BackendStock = {
  code: string
  name: string
  priceKrw: number
  priceUsd: number
  changePct: number
  sharesOutstanding: number
  marketCap: number
  regularClose: number | null
  regularCloseDate: string | null
  nxtClose: number | null
  nxtCloseDate: string | null
  high: number | null
  market: Company['market']
  source: Company['source']
  status: Company['status']
}

type BackendSnapshot = {
  mode: MarketSnapshot['mode']
  at: string
  fxRate: number
  stocks: BackendStock[]
}

function toCompany(stock: BackendStock, index: number): Company {
  return {
    code: stock.code,
    name: stock.name,
    color: META[stock.code]?.color ?? COLORS[index % COLORS.length],
    logo: META[stock.code]?.logo ?? '',
    price: stock.priceKrw,
    changePct: stock.changePct,
    regularClose: stock.regularClose,
    regularCloseDate: stock.regularCloseDate,
    nxtClose: stock.nxtClose,
    nxtCloseDate: stock.nxtCloseDate,
    high: stock.high ?? null,
    sharesOutstanding: stock.sharesOutstanding,
    market: stock.market,
    source: stock.source,
    status: stock.status,
  }
}

export function useMarketData(symbols: string[]): {
  snapshot: MarketSnapshot
  connection: ConnectionState
  hasReceived: boolean
} {
  const [snapshot, setSnapshot] = useState<MarketSnapshot>(mockSnapshot)
  const [connectionState, setConnectionState] = useState<{ key: string; value: ConnectionState }>({
    key: '',
    value: 'connecting',
  })
  const [receivedState, setReceivedState] = useState({ key: '', value: false })
  const symbolKey = symbols.join(',')
  const connection = connectionState.key === symbolKey ? connectionState.value : 'connecting'
  const hasReceived = receivedState.key === symbolKey && receivedState.value

  useEffect(() => {
    if (!symbolKey) return
    const selectedCodes = symbolKey.split(',')
    const es = new EventSource(`${API_BASE}/api/stream?symbols=${encodeURIComponent(symbolKey)}`)
    es.onopen = () => {
      setConnectionState({ key: symbolKey, value: 'connected' })
    }
    const onQuotes = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as BackendSnapshot
        setReceivedState({ key: symbolKey, value: true })
        setSnapshot((previous) => {
          const merged = new Map(previous.stocks.map((stock) => [stock.code, stock]))
          data.stocks.forEach((stock, index) => merged.set(stock.code, toCompany(stock, index)))
          return {
            mode: data.mode,
            at: data.at,
            fxRate: data.fxRate,
            stocks: selectedCodes.map((code) => merged.get(code)).filter((stock): stock is Company => !!stock),
          }
        })
      } catch {
        // 파싱 실패 시 직전 스냅샷 유지
      }
    }
    es.addEventListener('quotes', onQuotes as EventListener)
    es.onerror = () => setConnectionState({ key: symbolKey, value: 'reconnecting' })
    return () => es.close()
  }, [symbolKey])

  return { snapshot, connection, hasReceived }
}
