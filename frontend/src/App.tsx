import { Header } from './components/Header'
import { CompanyCard } from './components/CompanyCard'
import { MarketCapBar } from './components/MarketCapBar'
import { ReversalCalculator } from './components/ReversalCalculator'
import { InvestmentOpinion } from './components/InvestmentOpinion'
import { useMarketData } from './hooks/useMarketData'
import { compare } from './lib/marketCap'

function App() {
  const snapshot = useMarketData()
  const cmp = compare(snapshot.samsung, snapshot.hynix)
  const samsungLeads = cmp.leader.code === snapshot.samsung.code

  // KIS 미연동 폴백: 거래 세션 시간인데 KIS 종가가 전혀 없으면, 실거래가 대신 해외 추정으로 폴백된 상태.
  // 이때는 표시를 추정(ESTIMATE)으로 일관시키고 상단 배너로 알린다.
  const fallbackEstimate =
    snapshot.mode !== 'ESTIMATE' &&
    snapshot.samsung.regularClose == null &&
    snapshot.hynix.regularClose == null
  const displayMode = fallbackEstimate ? 'ESTIMATE' : snapshot.mode

  return (
    <div className="min-h-screen bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100">
      <div className="mx-auto max-w-4xl px-5 sm:px-8 py-8">
        <Header mode={displayMode} />

        {fallbackEstimate && (
          <div className="mt-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-[12px] text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
            <span aria-hidden>ⓘ</span>
            <span>KIS 미연동 — 해외 거래소(Hyperliquid) 추정 시세로 표시 중입니다.</span>
          </div>
        )}

        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <CompanyCard
            company={snapshot.samsung}
            isLeader={samsungLeads}
            mode={displayMode}
            fxRate={snapshot.fxRate}
          />
          <CompanyCard
            company={snapshot.hynix}
            isLeader={!samsungLeads}
            mode={displayMode}
            fxRate={snapshot.fxRate}
          />
        </div>

        <div className="mt-3">
          <MarketCapBar samsung={snapshot.samsung} hynix={snapshot.hynix} />
        </div>

        <div className="mt-3">
          <ReversalCalculator cmp={cmp} />
        </div>

        <InvestmentOpinion />

        <footer className="mt-8 border-t border-neutral-200 dark:border-neutral-800 pt-4 text-[11px] leading-relaxed text-neutral-400">
          <p className="mb-1 font-medium text-neutral-500">면책조항</p>
          <p>
            본 사이트는 재미와 학습 목적으로 만든 비상업 프로젝트입니다. 표시되는 가격은
            실제 체결가가 아니며, 특히 야간·주말은 해외 파생상품(Hyperliquid)과 환율로
            산출한 <span className="text-neutral-500">추정치</span>입니다. 정규장/애프터마켓
            시세도 지연·오차가 있을 수 있습니다. 본 정보는 <span className="text-neutral-500">투자
            권유나 자문이 아니며</span>, 모든 투자 판단과 그 결과의 책임은 이용자 본인에게
            있습니다.
          </p>
        </footer>
      </div>
    </div>
  )
}

export default App
