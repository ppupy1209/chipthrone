import { ThemeToggle } from './ThemeToggle'

export function Header() {
  return (
    <header className="flex items-center justify-between gap-3">
      <div>
        <div className="text-xl font-medium tracking-[0.22em] text-neutral-900 dark:text-neutral-100">
          CHIP<span className="mx-0.5 font-normal text-[#b8924a]">·</span>THRONE
        </div>
        <div className="mt-0.5 text-[10px] tracking-[0.18em] text-neutral-400">
          삼성전자 · SK하이닉스 시총 비교
        </div>
      </div>
      <div className="flex items-center gap-2">
        <span className="hidden text-[10px] text-neutral-400 sm:inline">전일 확정 · 24시간 추정</span>
        <ThemeToggle />
      </div>
    </header>
  )
}
