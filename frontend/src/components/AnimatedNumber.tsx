import { useEffect, useRef, useState } from 'react'

// 값이 바뀌면 이전 값에서 새 값으로 부드럽게 굴러가는(count-up) 숫자.
export function AnimatedNumber({
  value,
  format,
  className,
}: {
  value: number
  format: (n: number) => string
  className?: string
}) {
  const [display, setDisplay] = useState(value)
  const prevRef = useRef(value)

  useEffect(() => {
    const from = prevRef.current
    const to = value
    if (from === to) return

    const duration = 500
    const start = performance.now()
    let raf = 0

    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3) // easeOutCubic
      setDisplay(from + (to - from) * eased)
      if (t < 1) {
        raf = requestAnimationFrame(step)
      } else {
        prevRef.current = to
      }
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [value])

  return <span className={className}>{format(display)}</span>
}
