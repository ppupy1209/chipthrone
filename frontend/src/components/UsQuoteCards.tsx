import type { QuoteCurrency, QuoteSlot } from '../types'
import { UsCompanyCard } from './CompanyCard'

export function UsQuoteCards({ slots, currency, emptyMessage }: { slots: QuoteSlot[]; currency: QuoteCurrency; emptyMessage: string }) {
  if (slots.length === 0) {
    return (
      <div className="empty-watchlist" role="status">
        {emptyMessage}
      </div>
    )
  }

  return (
    <div className="us-quotes">
      {slots.map((slot) => <UsCompanyCard key={slot.code} slot={slot} currency={currency} />)}
    </div>
  )
}
