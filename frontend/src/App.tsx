import { Header } from './components/Header'
import { CompanyCard } from './components/CompanyCard'
import { MarketCapBar } from './components/MarketCapBar'
import { ReversalCalculator } from './components/ReversalCalculator'
import { useMarketData } from './hooks/useMarketData'
import { compare } from './lib/marketCap'

function App() {
  const snapshot = useMarketData()
  const cmp = compare(snapshot.samsung, snapshot.hynix)
  const samsungLeads = cmp.leader.code === snapshot.samsung.code

  return (
    <div className="min-h-screen bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100">
      <div className="mx-auto max-w-2xl px-5 py-8">
        <Header mode={snapshot.mode} at={snapshot.at} />

        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <CompanyCard
            company={snapshot.samsung}
            isLeader={samsungLeads}
            mode={snapshot.mode}
            fxRate={snapshot.fxRate}
          />
          <CompanyCard
            company={snapshot.hynix}
            isLeader={!samsungLeads}
            mode={snapshot.mode}
            fxRate={snapshot.fxRate}
          />
        </div>

        <div className="mt-3">
          <MarketCapBar samsung={snapshot.samsung} hynix={snapshot.hynix} />
        </div>

        <div className="mt-3">
          <ReversalCalculator cmp={cmp} />
        </div>

        <footer className="mt-6 text-center text-[11px] text-neutral-400">
          정규장 KRX / NXT 실제가 · 야간·주말 Hyperliquid perp 기반 추정치 (투자 참고용)
        </footer>
      </div>
    </div>
  )
}

export default App
