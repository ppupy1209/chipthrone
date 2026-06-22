import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

// 초기 테마는 index.html 인라인 스크립트가 이미 .dark 클래스로 적용해 둠(FOUC 방지).
// 여기서는 그 상태를 읽어와 토글만 관리한다.
function initialTheme(): Theme {
  if (typeof document !== 'undefined' && document.documentElement.classList.contains('dark')) {
    return 'dark'
  }
  return 'light'
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(initialTheme)

  useEffect(() => {
    const root = document.documentElement
    root.classList.toggle('dark', theme === 'dark')
    root.style.colorScheme = theme
    try {
      localStorage.setItem('theme', theme)
    } catch {
      // 저장 실패는 무시(시크릿 모드 등)
    }
  }, [theme])

  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))
  return { theme, toggle }
}
