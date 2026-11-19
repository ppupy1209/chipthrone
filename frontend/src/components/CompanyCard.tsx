import type { CSSProperties } from 'react'
import type { Company, QuoteCurrency, QuoteSlot } from '../types'
import { brandColor, brandForeground } from '../lib/brandColor'
import {
  formatCap,
  formatDateWithDay,
  formatPct,
  formatPrice,
  formatUsdAmount,
  marketCap,
  sessionCloseChangePct,
} from '../lib/marketCap'

function companyStyle(code: string): CSSProperties {
  const brand = brandColor(code)
  return {
    '--brand-light': brand.light,
    '--brand-dark': brand.dark,
    '--brand-foreground-light': brandForeground(brand.light),
    '--brand-foreground-dark': brandForeground(brand.dark),
  } as CSSProperties
}

function sessionCloseHint(company: Company): string | undefined {
  if (company.sessionCloseUsd == null) return undefined
  return `${formatDateWithDay(company.sessionCloseDate)} 16:00 ET 마감 추정가 `
    + `${formatUsdAmount(company.sessionCloseUsd)} 대비`
}

function Divergence({ company }: { company: Company }) {
  const value = sessionCloseChangePct(company)
  if (value == null) return null
  return (
    <div className="quote-change" tabIndex={0} aria-label={`정규장 마감 추정가 대비 ${formatPct(value)}`} aria-describedby={`session-close-${company.code}`}>
      <span className={value >= 0 ? 'change-value positive' : 'change-value negative'}>{formatPct(value)}</span>
      <span id={`session-close-${company.code}`} role="tooltip" className="quote-tooltip">{sessionCloseHint(company)}</span>
    </div>
  )
}

export function EstimateCompanyCard({ slot, isLeader }: { slot: QuoteSlot; isLeader: boolean }) {
  const { code, name, company } = slot
  return (
    <article
      data-testid="estimate-company-card"
      className="domestic-quote company-colors"
      style={companyStyle(code)}
      aria-busy={!company}
    >
      <div className="company-heading">
        <h2>{name}</h2>
        {isLeader ? <span className="leader-label">현재 1위</span> : <span className="stock-code">{code}</span>}
      </div>
      <p className={`domestic-price${company ? '' : ' pending-quote'}`}>
        <span>{company ? formatPrice(company.price) : '—'}</span><small>원</small>
      </p>
      <p className="secondary-price">{company ? `≈ ${formatUsdAmount(company.priceUsd)}` : '시세를 받는 중'}</p>
      <div className="domestic-cap">
        <span>추정 시가총액</span>
        <strong>{company ? formatCap(marketCap(company)) : '—'}</strong>
      </div>
    </article>
  )
}

export function UsCompanyCard({ slot, currency }: { slot: QuoteSlot; currency: QuoteCurrency }) {
  const { code, name, company } = slot
  return (
    <article
      data-testid="us-company-card"
      className="us-quote company-colors"
      style={companyStyle(code)}
      aria-busy={!company}
    >
      <div className="us-quote-heading">
        <h3 className="company-name"><span className="brand-marker" aria-hidden="true" />{name}</h3>
        <span className="ticker-label">{code}</span>
      </div>
      <div className="us-price-row">
        <div className="us-price-stack">
          <p className={`us-price${company ? '' : ' pending-quote'}`}>
            {company ? currency === 'USD' ? formatUsdAmount(company.priceUsd) : formatPrice(company.price) : '—'}
            {currency === 'KRW' && <small>원</small>}
          </p>
          <p className="secondary-price">
            {company
              ? currency === 'USD' ? `≈ ${formatPrice(company.price)}원` : `≈ ${formatUsdAmount(company.priceUsd)}`
              : '시세를 받는 중'}
          </p>
        </div>
        {company && <Divergence company={company} />}
      </div>
      <div className="us-cap"><span>추정 시총</span><strong>{company ? formatCap(marketCap(company)) : '—'}</strong></div>
    </article>
  )
}
