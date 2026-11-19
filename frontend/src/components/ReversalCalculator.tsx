import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { Comparison } from '../lib/marketCap'
import { formatCap, formatPrice, simulateReversal } from '../lib/marketCap'
import { brandColor } from '../lib/brandColor'

export function ReversalCalculator({ cmp }: { cmp: Comparison | null }) {
  const [increasePct, setIncreasePct] = useState(0)
  if (!cmp) {
    return (
      <div data-testid="reversal-comparison" className="comparison-pending" role="status">
        <div data-testid="reversal-bar" className="share-track pending-track" aria-hidden="true" />
        <p>두 기업의 시세를 받으면 시가총액 비중과 역전 조건을 표시합니다.</p>
      </div>
    )
  }
  // 기업의 위치와 색은 순위가 바뀌어도 고정한다.
  const samsung = cmp.leader.code === '005930' ? cmp.leader : cmp.challenger
  const hynix = cmp.leader.code === '000660' ? cmp.leader : cmp.challenger
  const samsungCap = samsung.price * samsung.sharesOutstanding
  const hynixCap = hynix.price * hynix.sharesOutstanding
  const samsungShare = samsungCap / (samsungCap + hynixCap) * 100
  const needPct = Math.max(cmp.reversalPct, 0)
  const maximum = Math.max(40, Math.ceil(needPct / 10) * 10)
  const scenario = simulateReversal(cmp, increasePct)
  const samsungBrand = brandColor(samsung.code)
  const hynixBrand = brandColor(hynix.code)
  const tied = cmp.gap < 1
  const colors = {
    '--samsung-light': samsungBrand.light,
    '--samsung-dark': samsungBrand.dark,
    '--hynix-light': hynixBrand.light,
    '--hynix-dark': hynixBrand.dark,
  } as CSSProperties

  return (
    <div data-testid="reversal-comparison" className="reversal-comparison" style={colors}>
      <div className="share-labels">
        <span>{samsung.name} <b>{samsungShare.toFixed(1)}%</b></span>
        <span>{hynix.name} <b>{(100 - samsungShare).toFixed(1)}%</b></span>
      </div>
      <div data-testid="reversal-bar" className="share-track" role="img" aria-label={`${samsung.name} ${samsungShare.toFixed(1)}%, ${hynix.name} ${(100 - samsungShare).toFixed(1)}%`}>
        <div data-testid={cmp.leader.code === samsung.code ? 'reversal-bar-leader' : undefined} className="share-samsung" style={{ width: `${samsungShare}%` }} />
        <div data-testid={cmp.leader.code === hynix.code ? 'reversal-bar-leader' : undefined} className="share-hynix" />
        <span className="share-midpoint" />
      </div>
      <div className="comparison-summary">
        <strong>{tied ? '두 기업의 추정 시가총액이 같습니다' : `${cmp.leader.name} ${formatCap(cmp.gap)} 원 우위`}</strong>
        <span>{tied ? '주가가 움직이면 순위가 달라질 수 있습니다' : `${cmp.challenger.name} +${needPct.toFixed(1)}% 상승 시 역전`}</span>
      </div>
      <details className="reversal-details">
        <summary className="control-button">계산해 보기 <span className="chevron" aria-hidden="true" /></summary>
        <section className="reversal-calculator" aria-labelledby="reversal-title">
          <div className="calculator-heading"><h3 id="reversal-title">왕좌가 바뀌려면</h3><span>{cmp.leader.name} 가격 고정</span></div>
          <div className="calculator-target">
            <div><p>{cmp.challenger.name}</p><strong>+{needPct.toFixed(1)}<small>%</small></strong><span>상승하면 추정 시가총액이 같아집니다.</span></div>
            <div><p>동률 기준 추정가</p><b>{formatPrice(cmp.reversalPrice)}<small>원</small></b><span>현재보다 약 +{formatPrice(Math.max(0, cmp.reversalPrice - cmp.challenger.price))}원</span></div>
          </div>
          <div className="reversal-simulation">
            <label htmlFor="reversal-increase">{cmp.challenger.name} 상승률 가정 <output>{`+${increasePct.toFixed(1)}%`}</output></label>
            <input id="reversal-increase" type="range" min="0" max={maximum} step="0.1" value={increasePct} onChange={(event) => setIncreasePct(Number(event.target.value))} aria-describedby="reversal-result" />
            <div className="range-labels"><span>0%</span><span>+{maximum}%</span></div>
            <p id="reversal-result" className={`simulation-result ${scenario.status}`} aria-live="polite">
              {scenario.status === 'tied'
                ? '두 기업의 추정 시가총액이 같습니다.'
                : scenario.status === 'behind'
                  ? `역전까지 ${formatCap(scenario.gap)} 원`
                  : `왕좌 교체 · ${cmp.challenger.name} ${formatCap(-scenario.gap)} 원 우위`}
            </p>
            <p className="simulation-note">상대 기업의 가격을 고정한 가정이며 실제 시세에는 반영되지 않습니다.</p>
          </div>
        </section>
      </details>
    </div>
  )
}
