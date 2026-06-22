import { useEffect, useState } from 'react'
import type { OpinionsResponse } from '../types'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

// 증권사 투자의견(/api/opinions)을 가져온다. 의견은 자주 안 바뀌므로 10분 주기로만 갱신.
// 백엔드 미연동/실패 시 null 유지 → 섹션은 렌더되지 않는다.
export function useOpinions(): OpinionsResponse | null {
  const [data, setData] = useState<OpinionsResponse | null>(null)

  useEffect(() => {
    let alive = true
    const load = () => {
      fetch(`${API_BASE}/api/opinions`)
        .then((res) => (res.ok ? res.json() : null))
        .then((json) => {
          if (alive && json) setData(json as OpinionsResponse)
        })
        .catch(() => {
          // 실패 시 직전 값 유지
        })
    }
    load()
    const id = setInterval(load, 10 * 60 * 1000)
    return () => {
      alive = false
      clearInterval(id)
    }
  }, [])

  return data
}
