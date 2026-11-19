import { useMemo } from 'react'
import type { SupportedAsset } from '../types'
import { US_GROUPS } from '../data/assetGroups'
import { MAX_WATCHLIST } from '../lib/watchlist.js'

const GROUPED_CODES = new Set(US_GROUPS.flatMap((group) => group.codes))

export function WatchlistPicker({ assets, selected, onAdd, onRemove }: {
  assets: SupportedAsset[]
  selected: string[]
  onAdd: (code: string) => void
  onRemove: (code: string) => void
}) {
  const assetByCode = useMemo(() => new Map(assets.map((asset) => [asset.code, asset])), [assets])
  const selectedSet = new Set(selected)
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
      assets: group.codes.map((code) => assetByCode.get(code)).filter((asset): asset is SupportedAsset => !!asset),
    }))
    .filter((group) => group.assets.length > 0)
  const selectedCount = assets.filter((asset) => asset.market === 'US' && selectedSet.has(asset.code)).length

  return (
    <details data-testid="watchlist" className="watchlist-picker">
      <summary className="control-button">종목 선택 <span>{selectedCount}</span><span className="chevron" aria-hidden="true" /></summary>
      <div className="watchlist-options">
        {groups.map((group) => (
          <section key={group.id} aria-labelledby={`asset-group-${group.id}`}>
            <div className="picker-group-heading">
              <h3 id={`asset-group-${group.id}`}>{group.label}</h3><span>{group.description}</span>
            </div>
            <div className="picker-assets">
              {group.assets.map((asset) => {
                const isSelected = selectedSet.has(asset.code)
                return (
                  <button
                    key={asset.code}
                    type="button"
                    aria-pressed={isSelected}
                    aria-label={`${asset.name} ${isSelected ? '해제' : '추가'}`}
                    disabled={!isSelected && selected.length >= MAX_WATCHLIST}
                    onClick={() => (isSelected ? onRemove(asset.code) : onAdd(asset.code))}
                  >
                    <span className="picker-check" aria-hidden="true">{isSelected && (
                      <svg viewBox="0 0 16 16" fill="none"><path d="m3.5 8 3 3 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
                    )}</span>
                    <span className="picker-asset-name">{asset.name}</span>
                    <span className="picker-asset-code">{asset.code}</span>
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>
    </details>
  )
}
