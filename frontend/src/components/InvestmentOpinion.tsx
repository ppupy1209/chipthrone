import type { OpinionConsensus, OpinionReport, StockOpinion } from '../types'
import { formatPrice } from '../lib/marketCap'
import { useOpinions } from '../hooks/useOpinions'

const MAX_ROWS = 6

// 의견 텍스트(예: '매수','중립','매도','Buy')를 색 톤으로 분류. 국장 관례: 매수=빨강, 매도=파랑.
function opinionTone(opinion: string): string {
  const o = opinion.toLowerCase()
  if (o.includes('매수') || o.includes('buy')) return 'text-red-600'
  if (o.includes('매도') || o.includes('sell')) return 'text-blue-600'
  return 'text-neutral-500'
}

function scoreLabel(score: number): string {
  if (score >= 4.5) return '강력매수'
  if (score >= 3.5) return '매수'
  if (score >= 2.5) return '중립'
  if (score >= 1.5) return '매도'
  return '강력매도'
}

function Distribution({ c }: { c: OpinionConsensus }) {
  const total = c.buy + c.hold + c.sell
  if (total === 0) return null
  const pct = (n: number) => `${(n / total) * 100}%`
  return (
    <div>
      <div className="flex h-1.5 w-full overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
        <div className="bg-red-500" style={{ width: pct(c.buy) }} />
        <div className="bg-neutral-400" style={{ width: pct(c.hold) }} />
        <div className="bg-blue-500" style={{ width: pct(c.sell) }} />
      </div>
      <div className="mt-1 flex gap-3 text-[11px] text-neutral-400 tabular-nums">
        <span className="text-red-600">매수 {c.buy}</span>
        <span>중립 {c.hold}</span>
        <span className="text-blue-600">매도 {c.sell}</span>
      </div>
    </div>
  )
}

function StockOpinionBlock({ stock }: { stock: StockOpinion }) {
  const { consensus: c, reports } = stock
  const rows = reports.slice(0, MAX_ROWS)

  return (
    <div className="rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-4">
      <div className="flex items-baseline gap-2">
        <span className="text-[14px] font-medium">{stock.name}</span>
        <span className="text-[11px] text-neutral-400">{stock.code}</span>
        {c.score != null && (
          <span className="ml-auto inline-flex items-baseline gap-1">
            <span className="text-[11px] text-neutral-400">{scoreLabel(c.score)}</span>
            <span className="text-[15px] font-semibold tabular-nums">
              {c.score.toFixed(2)}
            </span>
            <span className="text-[11px] text-neutral-400">/ 5</span>
          </span>
        )}
      </div>

      <div className="mt-2 flex flex-wrap items-baseline gap-x-4 gap-y-1 text-[12px]">
        <span className="text-neutral-500">
          평균 목표주가{' '}
          <span className="font-medium tabular-nums text-neutral-900 dark:text-neutral-100">
            {c.avgTargetPrice != null ? `${formatPrice(c.avgTargetPrice)}원` : '—'}
          </span>
        </span>
        <span className="text-neutral-400 tabular-nums">
          추정기관수 {c.institutionCount}
        </span>
      </div>

      <div className="mt-2.5">
        <Distribution c={c} />
      </div>

      {rows.length > 0 && (
        <table className="mt-3 w-full text-[11px] tabular-nums">
          <thead>
            <tr className="text-neutral-400">
              <th className="py-1 text-left font-normal">발표일</th>
              <th className="py-1 text-left font-normal">의견</th>
              <th className="py-1 text-right font-normal">목표가</th>
              <th className="py-1 text-right font-normal">증권사</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r: OpinionReport, i) => (
              <tr
                key={`${r.broker}-${r.date}-${i}`}
                className="border-t border-neutral-100 dark:border-neutral-800"
              >
                <td className="py-1 text-left text-neutral-500">{r.date.slice(5)}</td>
                <td className={`py-1 text-left font-medium ${opinionTone(r.opinion)}`}>
                  {r.prevOpinion && r.prevOpinion !== r.opinion ? (
                    <span>
                      <span className="text-neutral-400">{r.prevOpinion}→</span>
                      {r.opinion}
                    </span>
                  ) : (
                    r.opinion
                  )}
                </td>
                <td className="py-1 text-right">
                  {r.targetPrice != null ? formatPrice(r.targetPrice) : '—'}
                </td>
                <td className="py-1 text-right text-neutral-600 dark:text-neutral-300">
                  {r.broker}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export function InvestmentOpinion() {
  const data = useOpinions()
  const stocks = (data?.stocks ?? []).filter(
    (s) => s.reports.length > 0 || s.consensus.institutionCount > 0,
  )
  if (stocks.length === 0) return null

  return (
    <section className="mt-3">
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-[13px] font-medium text-neutral-500">증권사 투자의견</h2>
        <span className="text-[10px] text-neutral-400">
          KIS 제공{data?.asOf ? ` · ${data.asOf} 기준` : ''}
        </span>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {stocks.map((s) => (
          <StockOpinionBlock key={s.code} stock={s} />
        ))}
      </div>
    </section>
  )
}
