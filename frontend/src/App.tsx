import { useEffect, useMemo, useState } from 'react'
import { Header } from './components/Header'
import { EstimateCompanyCard } from './components/CompanyCard'
import { ReversalCalculator } from './components/ReversalCalculator'
import { UsQuoteCards } from './components/UsQuoteCards'
import { WatchlistPicker } from './components/WatchlistPicker'
import { useMarketData } from './hooks/useMarketData'
import { useSupportedAssets } from './hooks/useSupportedAssets'
import { compareMarketCap } from './lib/marketCap'
import { fallbackAssets } from './data/mockMarket'
import type { QuoteSlot } from './types'
import { MAX_WATCHLIST, MIN_WATCHLIST, normalizeWatchlist } from './lib/watchlist.js'
import { matchesAssetGroup, US_GROUPS } from './data/assetGroups'
import type { AssetFilter } from './data/assetGroups'
import type { QuoteCurrency } from './types'

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
  const [filter, setFilter] = useState<AssetFilter>('all')
  const [currency, setCurrency] = useState<QuoteCurrency>('USD')
  const [storedSymbols, setStoredSymbols] = useState<string[]>(() =>
    normalizeWatchlist(storedWatchlist(), fallbackAssets.map((asset) => asset.code), CORE_SYMBOLS),
  )
  const symbols = useMemo(
    () => normalizeWatchlist(storedSymbols, assets.map((asset) => asset.code), CORE_SYMBOLS),
    [assets, storedSymbols],
  )
  useEffect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(symbols)), [symbols])

  const { snapshot, connection } = useMarketData(symbols)
  const assetByCode = useMemo(() => new Map(assets.map((asset) => [asset.code, asset])), [assets])
  const slotOf = (code: string): QuoteSlot => ({
    code,
    name: assetByCode.get(code)?.name ?? code,
    company: snapshot.stocks.find((stock) => stock.code === code),
  })
  const krxSlots = CORE_SYMBOLS.map(slotOf)
  const usSlots = symbols.filter((code) => assetByCode.get(code)?.market === 'US').map(slotOf)
  const visibleUsSlots = usSlots.filter((slot) => matchesAssetGroup(slot.code, filter))
  const [samsung, skHynix] = krxSlots
  const comparison = samsung.company && skHynix.company
    ? compareMarketCap(samsung.company, skHynix.company)
    : null

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
    <div className="dashboard">
      <Header updatedAt={snapshot.at} connection={connection} />
      <main>
        <section data-testid="estimate-krx" aria-labelledby="krx-title">
          <div className="section-heading">
            <h1 id="krx-title">국내 반도체</h1>
            <span>해외 파생 추정</span>
          </div>
          <div className="domestic-quotes">
            {krxSlots.map((slot) => (
              <EstimateCompanyCard
                key={slot.code}
                slot={slot}
                isLeader={comparison?.leader.code === slot.code}
              />
            ))}
          </div>
          <ReversalCalculator cmp={comparison} />
        </section>

        <section data-testid="us-watchlist" className="us-watchlist" aria-labelledby="us-title">
          <div className="section-heading">
            <h2 id="us-title">미국 반도체·AI</h2>
            <button
              type="button"
              className="control-button"
              aria-label={`미국 관심종목 가격 통화 변경, 현재 ${currency}`}
              onClick={() => setCurrency((current) => current === 'USD' ? 'KRW' : 'USD')}
            >
              가격 {currency}
            </button>
          </div>
          <div className="watchlist-toolbar">
            <div className="quote-filters" role="group" aria-label="미국 관심종목 분류">
              {[{ id: 'all' as const, label: '전체' }, ...US_GROUPS].map((group) => (
                <button
                  key={group.id}
                  type="button"
                  aria-pressed={filter === group.id}
                  onClick={() => setFilter(group.id)}
                >
                  {group.label}
                </button>
              ))}
            </div>
            <WatchlistPicker assets={assets} selected={symbols} onAdd={add} onRemove={remove} />
          </div>
          <UsQuoteCards
            slots={visibleUsSlots}
            currency={currency}
            emptyMessage={usSlots.length === 0
              ? '종목 선택에서 관심 있는 기업을 추가해 주세요.'
              : '이 분류에 선택한 종목이 없습니다. 전체를 보거나 관심종목을 추가해 주세요.'}
          />
          <p className="watchlist-note">해외 파생시장 추정값 · 등락률은 정규장 마감 추정가 대비입니다.</p>
        </section>

        <footer className="dashboard-footer">
          <p className="source-caption">해외 파생시장 추정값 <span>Hyperliquid · 업비트 KRW-USDC 중간값 참고</span></p>
          <p>
            원화 환산값은 업비트 KRW-USDC 최우선 호가의 중간값을 참고한 추정값입니다.
            24시간 가격은 실제 주식 체결가가 아닌 Hyperliquid 파생시장의 추정값이며 지연이나 괴리가 발생할 수 있습니다.
            시가총액 순위와 왕좌 교체 조건도 이 추정값으로 계산합니다.
            본 정보는 투자 권유나 자문이 아닙니다.
          </p>
        </footer>
      </main>
    </div>
  )
}

export default App
