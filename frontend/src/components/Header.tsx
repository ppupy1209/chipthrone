import { ThemeToggle } from './ThemeToggle'

export function Header() {
  return (
    <header className="flex items-center justify-between gap-3">
      <div className="text-xl font-medium tracking-[0.22em] text-neutral-900 dark:text-neutral-100">
        CHIP<span className="mx-0.5 font-normal text-[#b8924a]">·</span>THRONE
      </div>
      <div className="flex items-center gap-2">
        {/* 로고 옆 한 줄로 두려면 약 407px이 필요하다. 그보다 좁으면 접히거나 넘치므로 숨긴다. */}
        <span className="whitespace-nowrap text-[10px] text-neutral-400 max-[420px]:hidden">
          삼성전자 · SK하이닉스 시총 비교
        </span>
        <ThemeToggle />
      </div>
    </header>
  )
}
