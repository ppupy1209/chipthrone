import { useMemo } from 'react'
import type { SupportedAsset } from '../types'

const US_GROUPS = [
  {
    id: 'semiconductor',
    label: '반도체',
    description: '메모리·파운드리·팹리스',
    codes: ['SNDK', 'MU', 'AVGO', 'TSM', 'SKHY', 'AMD', 'ASML'],
  },
  {
    id: 'm7',
    label: 'M7',
    description: '미국 초대형 기술주',
    codes: ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA'],
  },
]

const GROUPED_CODES = new Set(US_GROUPS.flatMap((group) => group.codes))

export function WatchlistPicker({
  assets,
  selected,
  onAdd,
  onRemove,
}: {
  assets: SupportedAsset[]
  selected: string[]
  onAdd: (code: string) => void
  onRemove: (code: string) => void
}) {
  const assetByCode = useMemo(() => new Map(assets.map((asset) => [asset.code, asset])), [assets])
  const selectedSet = new Set(selected)
  // 그룹에 없는 지원 종목이 목록에서 사라지지 않도록 자동으로 묶는다.
  const ungrouped = useMemo(
    () => assets.filter((asset) => asset.market === 'US' && !GROUPED_CODES.has(asset.code)).map((asset) => asset.code),
    [assets],
  )
  const definedGroups = ungrouped.length > 0
    ? [...US_GROUPS, { id: 'etc', label: '기타', description: '그 외 지원 종목', codes: ungrouped }]
    : US_GROUPS
  const groups = definedGroups
    .map((group) => ({
      ...group,
      assets: group.codes
        .map((code) => assetByCode.get(code))
        .filter((asset): asset is SupportedAsset => !!asset),
    }))
    .filter((group) => group.assets.length > 0)

  return (
    <div data-testid="watchlist" className="rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="space-y-3">
        {groups.map((group) => (
          <section key={group.id} aria-labelledby={`asset-group-${group.id}`} className="overflow-hidden rounded-lg border border-neutral-100 dark:border-neutral-800">
            <div className="flex items-baseline gap-2 bg-neutral-50 px-3 py-2 dark:bg-neutral-950/50">
              <h3 id={`asset-group-${group.id}`} className="text-[11px] font-medium">{group.label}</h3>
              <span className="text-[9px] text-neutral-400">{group.description}</span>
            </div>
            <div className="grid grid-cols-2 gap-1.5 p-2 sm:grid-cols-3 lg:grid-cols-4">
              {group.assets.map((asset) => {
                const isSelected = selectedSet.has(asset.code)
                return (
                  <button
                    key={asset.code}
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => (isSelected ? onRemove(asset.code) : onAdd(asset.code))}
                    className={`flex min-w-0 items-center gap-1.5 rounded-md border px-2.5 py-2 text-left text-[11px] transition-colors ${
                      isSelected
                        ? 'border-neutral-300 bg-neutral-100 dark:border-neutral-600 dark:bg-neutral-800'
                        : 'border-neutral-100 hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-800 dark:hover:border-neutral-600 dark:hover:bg-neutral-800'
                    }`}
                  >
                    <span className="truncate font-medium">{asset.name}</span>
                    {asset.name !== asset.code && <span className="shrink-0 text-[9px] text-neutral-400">{asset.code}</span>}
                    <span className="ml-auto shrink-0 text-[9px] text-neutral-400">{isSelected ? '해제' : '추가'}</span>
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
