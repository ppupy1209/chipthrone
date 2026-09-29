import type { QuoteSlot } from '../types'
import { formatPrice, formatUsdAmount, marketCap } from '../lib/marketCap'
import { teamTone } from '../lib/teamTone'
import { AnimatedNumber } from './AnimatedNumber'

/** 시총을 "1,595.2" 형식으로. 단위 "조"는 크기를 달리해 따로 붙인다. */
function formatTrillion(cap: number): string {
  return (cap / 1e12).toLocaleString('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

/**
 * 전광판의 한쪽 팀. 자리는 고정하고(삼성전자 왼쪽, SK하이닉스 오른쪽) 순위 표시만 바꿔,
 * 1위가 바뀌는 순간이 자리 이동이 아니라 "현재 1위" 표시의 이동으로 보이게 한다.
 * 첫 시세가 오기 전에는 순위와 숫자 자리에 "-"를 둔다.
 */
export function TeamScore({ slot, rank, align }: { slot: QuoteSlot; rank: number | null; align: 'left' | 'right' }) {
  const { company } = slot
  const tone = teamTone(slot.code)
  return (
    <div data-testid="estimate-company-card" className={align === 'right' ? 'sm:text-right' : undefined}>
      <p className="text-[13px] text-ink-2">
        {rank === 1 ? <strong className="font-semibold text-ink">현재 1위</strong> : rank ? `${rank}위` : '-'}
      </p>
      <h2 className="mt-1 text-[22px] font-semibold tracking-[-0.01em] sm:text-[26px]">
        {slot.name}
        <span className="ml-2 text-[13px] font-normal text-ink-2">{slot.code}</span>
      </h2>
      {/* 줄 높이를 1보다 넉넉히 둬야 큰 숫자의 쉼표 꼬리가 아래 가격 줄을 침범하지 않는다 */}
      <p className={`num mt-3 text-[64px] leading-[1.1] font-semibold sm:text-[104px] ${company ? tone.text : 'text-ink-2'}`}>
        {company ? <AnimatedNumber value={marketCap(company)} format={formatTrillion} /> : '-'}
        <span className="ml-1 text-[28px] font-medium sm:text-[38px]">조</span>
      </p>
      <p className="mt-2 text-[15px] tabular-nums text-ink-2">
        {company ? (
          <>
            <AnimatedNumber value={company.price} format={formatPrice} />원 · {formatUsdAmount(company.priceUsd)}
          </>
        ) : (
          '-원 · $-'
        )}
      </p>
    </div>
  )
}
