import { useMemo } from 'react'
import type { SupportedAsset } from '../types'

const US_GROUPS = [
  {
    id: 'semiconductor',
    label: '반도체',
    description: '메모리, 파운드리, 팹리스',
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

/**
 * 종목 고르기. 박스를 겹치지 않고 그룹 이름 뒤에 글자 버튼을 이어 붙인다.
 * 선택한 종목은 진한 글자와 실선 밑줄, 고르지 않은 종목은 흐린 글자와 점선 밑줄로 구분한다.
 */
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
    <div data-testid="watchlist" className="mt-6 space-y-3">
      <p className="text-[13px] text-ink-2">종목 고르기</p>
      {groups.map((group) => (
        <section key={group.id} aria-labelledby={`asset-group-${group.id}`} className="flex flex-wrap items-baseline gap-x-4 gap-y-2">
          <h3 id={`asset-group-${group.id}`} className="text-[14px] font-semibold">
            {group.label}
            <span className="ml-1.5 text-[12px] font-normal text-ink-2">{group.description}</span>
          </h3>
          {group.assets.map((asset) => {
            const isSelected = selectedSet.has(asset.code)
            return (
              <button
                key={asset.code}
                type="button"
                aria-pressed={isSelected}
                onClick={() => (isSelected ? onRemove(asset.code) : onAdd(asset.code))}
                className={`whitespace-nowrap border-b pb-px text-[14px] ${
                  isSelected
                    ? 'border-ink font-medium text-ink'
                    : 'border-dotted border-ink-2 text-ink-2 hover:text-ink'
                }`}
              >
                {asset.name}
              </button>
            )
          })}
        </section>
      ))}
    </div>
  )
}
