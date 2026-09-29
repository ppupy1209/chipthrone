import type { QuoteSlot } from '../types'
import type { Comparison } from '../lib/marketCap'
import { formatPrice, marketCap } from '../lib/marketCap'
import { teamTone } from '../lib/teamTone'

function particle(word: string, withFinal: string, withoutFinal: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  return code >= 0 && code <= 0x2ba3 && code % 28 !== 0 ? withFinal : withoutFinal
}

/**
 * 줄다리기 막대와 역전 조건. 막대는 전광판과 같은 자리(left, right)를 따르고,
 * 가장 큰 숫자인 "필요 상승률"이 이 사이트가 답하는 질문 자체다.
 * 두 종목 시세가 모두 오기 전에는 막대를 중립색 반반으로 두고 숫자 자리에 "-"를 둔다.
 */
export function ReversalCalculator({ cmp, left, right }: { cmp: Comparison | null; left: QuoteSlot; right: QuoteSlot }) {
  const leftCap = left.company ? marketCap(left.company) : 0
  const rightCap = right.company ? marketCap(right.company) : 0
  const ready = cmp != null && leftCap > 0 && rightCap > 0
  // 두 종목 합계 대비 점유율. 50%가 역전선.
  const leftShare = ready ? (leftCap / (leftCap + rightCap)) * 100 : 50
  const leftTone = ready ? teamTone(left.code) : { text: 'text-ink-2', bar: 'bg-rule' }
  const rightTone = ready ? teamTone(right.code) : { text: 'text-ink-2', bar: 'bg-rule' }

  return (
    <div data-testid="reversal-comparison">
      <div className="mt-10">
        <div
          data-testid="reversal-bar"
          role="img"
          aria-label={ready ? `${left.name} ${leftShare.toFixed(1)}%, ${right.name} ${(100 - leftShare).toFixed(1)}%` : '시세를 받는 중'}
          className="relative flex h-5"
        >
          <div className={`h-full ${leftTone.bar}`} style={{ width: `${leftShare}%` }} />
          <div className={`h-full flex-1 ${rightTone.bar}`} />
          <span className="absolute -top-2 -bottom-2 left-1/2 w-0.5 -translate-x-1/2 bg-ink" />
        </div>
        <div className="mt-3 flex items-baseline justify-between text-[13px] text-ink-2">
          <span className={`num text-[22px] font-semibold ${leftTone.text}`}>{ready ? `${leftShare.toFixed(1)}%` : '-%'}</span>
          <span>역전선</span>
          <span className={`num text-[22px] font-semibold ${rightTone.text}`}>{ready ? `${(100 - leftShare).toFixed(1)}%` : '-%'}</span>
        </div>
      </div>

      <div className="mt-14 grid grid-cols-1 items-end gap-4 border-b border-rule pb-10 sm:grid-cols-[auto_minmax(0,1fr)] sm:gap-10">
        {ready && cmp ? <Verdict cmp={cmp} /> : <PendingVerdict />}
      </div>
    </div>
  )
}

function Verdict({ cmp }: { cmp: Comparison }) {
  const needPct = Math.max(cmp.reversalPct, 0)
  const needPrice = Math.round(cmp.reversalPrice - cmp.challenger.price)
  return (
    <>
      <p className={`num text-[104px] leading-[0.9] font-bold sm:text-[160px] ${teamTone(cmp.challenger.code).text}`}>
        +{needPct.toFixed(1)}%
      </p>
      <div>
        <p className="max-w-[30ch] text-[19px] leading-[1.5] sm:text-[20px]">
          {cmp.challenger.name}{particle(cmp.challenger.name, '이', '가')} 이만큼 오르면{' '}
          {cmp.leader.name}{particle(cmp.leader.name, '을', '를')} 제치고 왕좌가 바뀝니다.
        </p>
        <p className="mt-2 text-[14px] tabular-nums text-ink-2">
          {formatPrice(cmp.challenger.price)}원에서 {formatPrice(cmp.reversalPrice)}원으로, 약 {formatPrice(needPrice)}원 상승 필요
        </p>
      </div>
    </>
  )
}

function PendingVerdict() {
  return (
    <>
      <p className="num text-[104px] leading-[0.9] font-bold text-ink-2 sm:text-[160px]">-%</p>
      <div>
        <p className="max-w-[30ch] text-[19px] leading-[1.5] sm:text-[20px]">2위가 얼마나 올라야 왕좌가 바뀌는지 계산합니다.</p>
        <p className="mt-2 text-[14px] text-ink-2">실시간 시세를 받는 중입니다.</p>
      </div>
    </>
  )
}
