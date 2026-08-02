import { useMemo, useState } from 'react'
import type { SupportedAsset } from '../types'
import { MAX_WATCHLIST, MIN_WATCHLIST } from '../lib/watchlist.js'

export function WatchlistPicker({
  assets,
  selected,
  fixed,
  onAdd,
  onRemove,
}: {
  assets: SupportedAsset[]
  selected: string[]
  fixed: string[]
  onAdd: (code: string) => void
  onRemove: (code: string) => void
}) {
  const [query, setQuery] = useState('')
  const normalizedQuery = query.trim().toLowerCase()
  const candidates = useMemo(
    () =>
      assets.filter(
        (asset) =>
          !selected.includes(asset.code) &&
          (!normalizedQuery ||
            asset.code.includes(normalizedQuery) ||
            asset.name.toLowerCase().includes(normalizedQuery)),
      ),
    [assets, normalizedQuery, selected],
  )
  const selectedAssets = selected
    .map((code) => assets.find((asset) => asset.code === code))
    .filter((asset): asset is SupportedAsset => !!asset)
  const candidateGroups = [
    { market: 'KRX' as const, label: '국장 반도체' },
    { market: 'US' as const, label: '미장 반도체' },
  ].map((group) => ({
    ...group,
    assets: candidates.filter((asset) => asset.market === group.market),
  }))

  return (
    <section data-testid="watchlist" className="mt-5 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-[13px] font-medium">관심 종목</h2>
          <p className="mt-0.5 text-[10px] text-neutral-400">
            국장 2개 고정 · 미장 최대 {MAX_WATCHLIST - MIN_WATCHLIST}개 · 이 브라우저에 저장
          </p>
        </div>
        <span className="text-[11px] tabular-nums text-neutral-400">
          {selected.length}/{MAX_WATCHLIST}
        </span>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {selectedAssets.map((asset) => (
          <span
            key={asset.code}
            className="inline-flex items-center gap-1.5 rounded-full bg-neutral-100 py-1 pl-2.5 pr-1.5 text-[11px] dark:bg-neutral-800"
          >
            <span>{asset.name}</span>
            <span className="text-neutral-400">{asset.code}</span>
            <span className="rounded bg-neutral-200 px-1 py-0.5 text-[9px] text-neutral-500 dark:bg-neutral-700 dark:text-neutral-300">
              {asset.market === 'KRX' ? '국장' : '미장'}
            </span>
            {fixed.includes(asset.code) ? (
              <span className="px-1 text-[9px] text-neutral-400">고정</span>
            ) : (
              <button
                type="button"
                aria-label={`${asset.name} 삭제`}
                onClick={() => onRemove(asset.code)}
                className="grid h-5 w-5 place-items-center rounded-full text-neutral-400 hover:bg-neutral-200 hover:text-neutral-700 dark:hover:bg-neutral-700 dark:hover:text-neutral-100"
              >
                ×
              </button>
            )}
          </span>
        ))}
      </div>

      <div className="relative mt-3">
        <label htmlFor="stock-search" className="sr-only">지원 종목 검색</label>
        <input
          id="stock-search"
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="종목명 또는 코드·티커 검색"
          className="w-full rounded-lg border border-neutral-200 bg-transparent px-3 py-2 text-[12px] outline-none placeholder:text-neutral-400 focus:border-neutral-400 dark:border-neutral-700 dark:focus:border-neutral-500"
        />
      </div>

      {(normalizedQuery || selected.length < assets.length) && (
        <div className="mt-2 max-h-36 overflow-y-auto rounded-lg border border-neutral-100 dark:border-neutral-800">
          {candidates.length > 0 ? (
            candidateGroups.map((group) =>
              group.assets.length > 0 && (
                <div key={group.market}>
                  <div className="bg-neutral-50 px-3 py-1.5 text-[10px] font-medium text-neutral-400 dark:bg-neutral-950/50">
                    {group.label}
                  </div>
                  {group.assets.map((asset) => (
                    <button
                      key={asset.code}
                      type="button"
                      disabled={selected.length >= MAX_WATCHLIST}
                      onClick={() => {
                        onAdd(asset.code)
                        setQuery('')
                      }}
                      className="flex w-full items-center gap-2 border-b border-neutral-100 px-3 py-2 text-left text-[12px] last:border-0 hover:bg-neutral-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-neutral-800 dark:hover:bg-neutral-800"
                    >
                      <span className="font-medium">{asset.name}</span>
                      <span className="text-neutral-400">{asset.code}</span>
                      <span className="ml-auto text-neutral-400">추가</span>
                    </button>
                  ))}
                </div>
              ),
            )
          ) : (
            <p className="px-3 py-3 text-[11px] text-neutral-400">검색 가능한 미선택 종목이 없습니다.</p>
          )}
        </div>
      )}
    </section>
  )
}
