export type AssetFilter = 'all' | 'semiconductor' | 'm7'

export const US_GROUPS: {
  id: Exclude<AssetFilter, 'all'>
  label: string
  description: string
  codes: string[]
}[] = [
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

/** 분류는 표시만 바꾼다. 저장한 관심목록이나 SSE 구독 목록은 바꾸지 않는다. */
export function matchesAssetGroup(code: string, filter: AssetFilter): boolean {
  return filter === 'all' || US_GROUPS.find((group) => group.id === filter)?.codes.includes(code) === true
}
