import type { QuoteSlot } from '../types'
import { UsCompanyCard } from './CompanyCard'

export function UsQuoteCards({ slots }: { slots: QuoteSlot[] }) {
  if (slots.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-neutral-200 px-4 py-6 text-center text-[11px] text-neutral-400 dark:border-neutral-800">
        위 목록에서 관심 종목을 추가하세요.
      </div>
    )
  }

  return (
    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      {slots.map((slot) => <UsCompanyCard key={slot.code} slot={slot} />)}
    </div>
  )
}
