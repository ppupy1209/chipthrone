import { useEffect, useMemo, useState } from 'react'
import { Header } from './components/Header'
import {
  EstimateCompanyCard,
  OfficialCloseTitle,
  OfficialCompanyCard,
  UsCompanyCard,
} from './components/CompanyCard'
import { ReversalCalculator } from './components/ReversalCalculator'
import { WatchlistPicker } from './components/WatchlistPicker'
import { useMarketData } from './hooks/useMarketData'
import { useSupportedAssets } from './hooks/useSupportedAssets'
import { compareOfficial, officialMarketCap } from './lib/marketCap'
import { fallbackAssets } from './data/mockMarket'
import { MAX_WATCHLIST, MIN_WATCHLIST, normalizeWatchlist } from './lib/watchlist.js'

const STORAGE_KEY = 'chipthrone.watchlist.v1'
const CORE_SYMBOLS = ['005930', '000660']

/** ISO-8601 시각을 "14:32" 형식으로. 값이 없으면 "-" */
function formatClock(iso: string | null): string {
  if (!iso) return '-'
  const at = new Date(iso)
  if (Number.isNaN(at.getTime())) return '-'
  return at.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

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
  const usCompanies = companies.filter((company) => company.market === 'US')
  const krxRanked = useMemo(
    () => [...krxCompanies].sort((a, b) => officialMarketCap(b) - officialMarketCap(a)),
    [krxCompanies],
  )
  const comparison = krxRanked.length >= 2 ? compareOfficial(krxRanked[0], krxRanked[1]) : null
  const officialDate = krxCompanies.map((company) => company.regularCloseDate).find(Boolean) ?? null

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
        <Header />

        <section data-testid="official-krx" className="mt-5">
          <h2 className="mb-2 text-[14px] font-medium tabular-nums">
            <OfficialCloseTitle date={officialDate} />
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {krxCompanies.map((company) => (
              <OfficialCompanyCard
                key={company.code}
                company={company}
                isLeader={krxRanked[0]?.code === company.code && officialMarketCap(company) > 0}
              />
            ))}
          </div>
          {comparison && <div className="mt-3"><ReversalCalculator cmp={comparison} /></div>}
        </section>

        <section data-testid="estimate-krx" className="mt-7">
          <div className="mb-2 flex items-end justify-between gap-3">
            <div>
              <h2 className="text-[14px] font-medium">해외 추정 시세</h2>
            </div>
            <div className="rounded-lg border border-neutral-200 bg-white px-3 py-2 text-right dark:border-neutral-800 dark:bg-neutral-900">
              {/* Pyth는 소수 5자리로 온다. 통화 표시는 2자리로 끊는다. */}
              <div className="text-[12px] font-medium tabular-nums">
                USD/KRW {snapshot.fxRate.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              <div className="mt-0.5 text-[9px] text-neutral-400">
                {snapshot.fxSource === 'PYTH'
                  ? `Pyth 실시간 · 갱신 ${formatClock(snapshot.fxFetchedAt)}`
                  : '실시간 환율 연결 대기 · 설정 기준값'}
              </div>
            </div>
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {krxCompanies.map((company) => (
              <EstimateCompanyCard key={company.code} company={company} fxRate={snapshot.fxRate} />
            ))}
          </div>
        </section>

        <section data-testid="us-watchlist" className="mt-7">
          <WatchlistPicker
            assets={assets}
            selected={symbols}
            onAdd={add}
            onRemove={remove}
          />
          <div className="mt-4">
            <div className="mb-2">
              <h2 className="text-[14px] font-medium">미국 AI·반도체</h2>
            </div>
            {usCompanies.length > 0 ? (
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {usCompanies.map((company) => (
                  <UsCompanyCard key={company.code} company={company} />
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-neutral-200 px-4 py-6 text-center text-[11px] text-neutral-400 dark:border-neutral-800">
                위 목록에서 관심 종목을 추가하세요.
              </div>
            )}
          </div>
        </section>

        <footer className="mt-8 border-t border-neutral-200 pt-4 text-[11px] leading-relaxed text-neutral-400 dark:border-neutral-800">
          <p className="mb-1 font-medium text-neutral-500">면책조항</p>
          <p>
            국내 확정값은 금융위원회 일별 주식시세정보, 환율은 Pyth Network의 USD/KRW 실시간 시세를 사용합니다.
            Pyth는 공적 고시환율이 아닌 민간 오라클 네트워크의 집계값입니다.
            24시간 가격은 실제 주식 체결가가 아닌 Hyperliquid 파생시장의 추정값이며 지연·괴리가 있을 수 있습니다.
            본 정보는 투자 권유나 자문이 아닙니다.
          </p>
        </footer>
      </div>
    </div>
  )
}

export default App
