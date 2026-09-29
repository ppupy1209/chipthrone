import { useEffect, useMemo, useState } from 'react'
import { Header } from './components/Header'
import { TeamScore } from './components/Scoreboard'
import { UsQuoteTable } from './components/UsQuoteTable'
import { ReversalCalculator } from './components/ReversalCalculator'
import { WatchlistPicker } from './components/WatchlistPicker'
import { useMarketData } from './hooks/useMarketData'
import { useSupportedAssets } from './hooks/useSupportedAssets'
import { compareMarketCap, marketCap } from './lib/marketCap'
import { fallbackAssets } from './data/mockMarket'
import type { QuoteSlot } from './types'
import { MAX_WATCHLIST, MIN_WATCHLIST, normalizeWatchlist } from './lib/watchlist.js'

const STORAGE_KEY = 'chipthrone.watchlist.v1'
const CORE_SYMBOLS = ['005930', '000660']

function storedWatchlist(): unknown {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')
  } catch {
    return null
  }
}

function App() {
  const assets = useSupportedAssets()
  const [storedSymbols, setStoredSymbols] = useState<string[]>(() =>
    normalizeWatchlist(storedWatchlist(), fallbackAssets.map((asset) => asset.code), CORE_SYMBOLS),
  )
  const symbols = useMemo(
    () => normalizeWatchlist(storedSymbols, assets.map((asset) => asset.code), CORE_SYMBOLS),
    [assets, storedSymbols],
  )
  useEffect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(symbols)), [symbols])

  const { snapshot } = useMarketData(symbols)
  const companies = useMemo(
    () => symbols.map((code) => snapshot.stocks.find((stock) => stock.code === code)).filter((stock): stock is NonNullable<typeof stock> => !!stock),
    [snapshot.stocks, symbols],
  )
  const krxCompanies = companies.filter((company) => company.market === 'KRX')
  // 시세가 오기 전에도 자리를 그리도록 코드와 이름은 지원 종목 목록에서 먼저 채운다.
  const assetByCode = useMemo(() => new Map(assets.map((asset) => [asset.code, asset])), [assets])
  const slotOf = (code: string): QuoteSlot => ({
    code,
    name: assetByCode.get(code)?.name ?? code,
    company: companies.find((company) => company.code === code),
  })
  const usSlots = symbols.filter((code) => assetByCode.get(code)?.market === 'US').map(slotOf)
  const krxRanked = useMemo(
    () => [...krxCompanies].sort((a, b) => marketCap(b) - marketCap(a)),
    [krxCompanies],
  )
  const comparison = krxRanked.length >= 2 ? compareMarketCap(krxRanked[0], krxRanked[1]) : null
  // 전광판 자리는 고정한다. 1위가 바뀌면 자리가 아니라 "현재 1위" 표시가 옮겨 간다.
  const [left, right] = CORE_SYMBOLS.map(slotOf)
  // 두 종목 시세가 모두 와야 순위를 매긴다. 그 전에는 null이라 순위 자리에 "-"가 나온다.
  const rankOf = (code: string) =>
    krxRanked.length >= 2 ? krxRanked.findIndex((company) => company.code === code) + 1 : null

  const add = (code: string) =>
    setStoredSymbols((current) =>
      current.length >= MAX_WATCHLIST || current.includes(code) ? current : [...current, code],
    )
  const remove = (code: string) =>
    setStoredSymbols((current) =>
      CORE_SYMBOLS.includes(code) || current.length <= MIN_WATCHLIST
        ? current
        : current.filter((symbol) => symbol !== code),
    )

  return (
    <div className="min-h-screen bg-paper text-ink">
      <div className="mx-auto max-w-[1040px] px-4 pt-6 pb-16 sm:px-6">
        <Header updatedAt={snapshot.at} />

        <section data-testid="estimate-krx" aria-label="삼성전자와 SK하이닉스 시가총액 대결">
          <div className="mt-8 grid grid-cols-1 gap-8 sm:mt-10 sm:grid-cols-2 sm:gap-10">
            <TeamScore slot={left} rank={rankOf(left.code)} align="left" />
            <TeamScore slot={right} rank={rankOf(right.code)} align="right" />
          </div>
          <ReversalCalculator cmp={comparison} left={left} right={right} />
        </section>

        <section data-testid="us-watchlist" className="mt-16">
          <h2 className="mb-4 text-[20px] font-semibold tracking-[-0.01em]">미국 반도체와 AI</h2>
          <UsQuoteTable slots={usSlots} />
          <WatchlistPicker
            assets={assets}
            selected={symbols}
            onAdd={add}
            onRemove={remove}
          />
        </section>

        <footer className="mt-16 text-[12px] leading-[1.7] text-ink-2">
          원화 환산값은 업비트 KRW-USDC 최우선 호가의 중간값을 참고한 추정값입니다.
          24시간 가격은 실제 주식 체결가가 아닌 Hyperliquid 파생시장의 추정값이며 지연이나 괴리가 발생할 수 있습니다.
          {/* 넓은 화면에서는 가격 설명과 계산 안내를 문장 단위로 나눠 두 줄로 맞춘다 */}
          <br className="max-lg:hidden" />
          시가총액 순위와 왕좌 교체 조건도 이 추정값으로 계산합니다.
          본 정보는 투자 권유나 자문이 아닙니다.
        </footer>
      </div>
    </div>
  )
}

export default App
