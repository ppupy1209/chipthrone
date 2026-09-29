import { ThemeToggle } from './ThemeToggle'

function formatTime(iso: string): string | null {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return null
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}

/**
 * 카드마다 붙던 "해외 파생 추정" 배지를 대신한다. 이 화면의 모든 가격이 같은 기준이므로
 * 기준과 마지막 갱신 시각은 머리글에 한 번만 적는다.
 */
export function Header({ updatedAt }: { updatedAt: string }) {
  const time = formatTime(updatedAt)
  return (
    <header className="flex items-center justify-between gap-3 border-b-2 border-ink pb-3">
      <div className="text-[15px] font-semibold tracking-[0.2em]">CHIP THRONE</div>
      <div className="flex items-center gap-3">
        <span className="text-[12px] text-ink-2 sm:text-[13px]">
          해외 파생시장 추정값{time && <span className="tabular-nums max-[420px]:hidden"> · {time} 갱신</span>}
        </span>
        <ThemeToggle />
      </div>
    </header>
  )
}
