import type { Company } from '../types'
import { formatCap, formatPrice, formatUsdAmount, marketCap, sessionCloseChangePct } from '../lib/marketCap'

function Change({ value }: { value: number | null }) {
  if (value == null) return <span className="text-ink-2">-</span>
  const up = value >= 0
  return (
    <span className={up ? 'text-up' : 'text-down'}>
      <span aria-hidden="true" className="mr-1 align-[0.12em] text-[0.6em]">{up ? '▲' : '▼'}</span>
      <span className="sr-only">{up ? '상승' : '하락'} </span>
      {Math.abs(value).toFixed(1)}%
    </span>
  )
}

/**
 * 관심 종목 시세표. 카드를 반복하지 않고 한 표에서 시총 순으로 비교한다.
 * 좁은 화면에서는 원화 환산 열을 숨겨 네 열만 남긴다.
 */
export function UsQuoteTable({ companies }: { companies: Company[] }) {
  const rows = [...companies].sort((a, b) => marketCap(b) - marketCap(a))
  return (
    <div>
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b border-ink text-[12px] font-medium text-ink-2">
            <th scope="col" className="py-2 text-left font-medium">종목</th>
            <th scope="col" className="py-2 text-right font-medium">현재가</th>
            <th scope="col" className="hidden py-2 text-right font-medium sm:table-cell">원화 환산</th>
            <th scope="col" className="py-2 text-right font-medium">추정 시가총액</th>
            <th scope="col" className="py-2 text-right font-medium">종가 대비</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="border-b border-rule py-6 text-[14px] text-ink-2">
                아래 목록에서 종목을 고르면 여기에 시세가 표시됩니다.
              </td>
            </tr>
          ) : (
            rows.map((company) => (
              <tr key={company.code} data-testid="us-company-card" className="border-b border-rule">
                <td className="py-3.5 pr-3 text-left">
                  <span className="font-semibold">{company.name}</span>
                  <span className="ml-2 text-[12px] text-ink-2">{company.code}</span>
                </td>
                <td className="num py-3.5 text-right text-[18px] font-semibold sm:text-[22px]">{formatUsdAmount(company.priceUsd)}</td>
                <td className="hidden py-3.5 text-right text-[15px] tabular-nums sm:table-cell">{formatPrice(company.price)}원</td>
                <td className="num py-3.5 text-right text-[18px] font-semibold sm:text-[22px]">{formatCap(marketCap(company))}</td>
                <td className="num py-3.5 text-right text-[18px] font-semibold sm:text-[22px]">
                  <Change value={sessionCloseChangePct(company)} />
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      <p className="mt-2 text-[12px] text-ink-2">종가 대비는 직전 정규장 마감(16:00 ET) 추정가와 비교한 등락률입니다.</p>
    </div>
  )
}
