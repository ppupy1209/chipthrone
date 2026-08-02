import { useEffect, useMemo, useState } from 'react'
import { Header } from './components/Header'
import { CompanyCard } from './components/CompanyCard'
import { MarketCapBar } from './components/MarketCapBar'
import { ReversalCalculator } from './components/ReversalCalculator'
import { InvestmentOpinion } from './components/InvestmentOpinion'
import { WatchlistPicker } from './components/WatchlistPicker'
import { ConnectionStatus } from './components/ConnectionStatus'
import { useMarketData } from './hooks/useMarketData'
import { useSupportedAssets } from './hooks/useSupportedAssets'
import { compare, marketCap } from './lib/marketCap'
import { fallbackAssets } from './data/mockMarket'
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

  const { snapshot, connection, hasReceived } = useMarketData(symbols)
  const companies = useMemo(
    () => symbols.map((code) => snapshot.stocks.find((stock) => stock.code === code)).filter((stock): stock is NonNullable<typeof stock> => !!stock),
    [snapshot.stocks, symbols],
  )
  const krxCompanies = companies.filter((company) => company.market === 'KRX')
  const usCompanies = companies.filter((company) => company.market === 'US')
  const krxRanked = useMemo(() => [...krxCompanies].sort((a, b) => marketCap(b) - marketCap(a)), [krxCompanies])
  const comparison = krxRanked.length >= 2 ? compare(krxRanked[0], krxRanked[1]) : null
  const fallbackEstimate = snapshot.mode !== 'ESTIMATE'
    && krxCompanies.length > 0
    && krxCompanies.every((company) => company.source === 'HYPERLIQUID')
  const displayMode = fallbackEstimate ? 'ESTIMATE' : snapshot.mode

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
    <div className="min-h-screen bg-neutral-50 text-neutral-900 dark:bg-neutral-950 dark:text-neutral-100">
      <div className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-8">
        <Header mode={displayMode} statuses={companies.map((company) => company.status)} />
        <ConnectionStatus state={connection} updatedAt={snapshot.at} hasReceived={hasReceived} />

        {fallbackEstimate && (
          <div className="mt-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-[12px] text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
            <span aria-hidden>ⓘ</span>
            <span>KIS 미연동 — 해외 거래소(Hyperliquid) 추정 시세로 표시 중입니다.</span>
          </div>
        )}

        <WatchlistPicker
          assets={assets}
          selected={symbols}
          fixed={CORE_SYMBOLS}
          onAdd={add}
          onRemove={remove}
        />

        <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_240px] lg:items-start">
          <section data-testid="krx-main">
            <div className="mb-2 flex items-end justify-between gap-3">
              <div>
                <h2 className="text-[13px] font-medium">국장 메인</h2>
                <p className="mt-0.5 text-[10px] text-neutral-400">삼성전자 · SK하이닉스 왕좌 비교</p>
              </div>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {krxCompanies.map((company) => (
                <CompanyCard
                  key={company.code}
                  company={company}
                  isLeader={krxRanked[0]?.code === company.code}
                  mode={displayMode}
                  fxRate={snapshot.fxRate}
                />
              ))}
            </div>
            {krxCompanies.length > 0 && (
              <div className="mt-3">
                <MarketCapBar companies={krxCompanies} />
              </div>
            )}
            {comparison && (
              <div className="mt-3">
                <ReversalCalculator cmp={comparison} />
              </div>
            )}
            <InvestmentOpinion symbols={krxCompanies.map((company) => company.code)} />
          </section>

          <aside data-testid="us-sidebar" className="rounded-xl border border-neutral-200 bg-neutral-100/60 p-2.5 dark:border-neutral-800 dark:bg-neutral-900/40">
            <div className="mb-2 px-0.5">
              <h2 className="text-[13px] font-medium">미장 반도체</h2>
              <p className="mt-0.5 text-[10px] text-neutral-400">관심 종목 · Alpaca IEX</p>
            </div>
            {usCompanies.length > 0 ? (
              <div className="space-y-2">
                {usCompanies.map((company) => (
                  <CompanyCard
                    key={company.code}
                    company={company}
                    isLeader={false}
                    mode={displayMode}
                    fxRate={snapshot.fxRate}
                    compact
                  />
                ))}
              </div>
            ) : (
              <p className="rounded-lg border border-dashed border-neutral-300 px-3 py-6 text-center text-[11px] text-neutral-400 dark:border-neutral-700">
                관심 종목에서 미장 종목을 추가하세요.
              </p>
            )}
          </aside>
        </div>

        <footer className="mt-8 border-t border-neutral-200 pt-4 text-[11px] leading-relaxed text-neutral-400 dark:border-neutral-800">
          <p className="mb-1 font-medium text-neutral-500">면책조항</p>
          <p>
            본 사이트는 재미와 학습 목적으로 만든 비상업 프로젝트입니다. 국장은 KIS,
            미장은 Alpaca IEX 범위의 시세를 사용하며 전체 미국 거래소 통합시세가 아닙니다.
            소스 미연동·장애 시에는 Hyperliquid와 환율로 산출한 <span className="text-neutral-500">추정치</span>를
            표시합니다. 시세는 지연·오차가 있을 수 있습니다. 본 정보는 <span className="text-neutral-500">투자
            권유나 자문이 아니며</span>, 모든 투자 판단과 그 결과의 책임은 이용자 본인에게
            있습니다.
          </p>
        </footer>
      </div>
    </div>
  )
}

export default App
