import { useEffect, useState } from 'react'
import type { SupportedAsset } from '../types'
import { fallbackAssets } from '../data/mockMarket'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export function useSupportedAssets(): SupportedAsset[] {
  const [assets, setAssets] = useState<SupportedAsset[]>(fallbackAssets)

  useEffect(() => {
    fetch(`${API_BASE}/api/assets`)
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) setAssets(data as SupportedAsset[])
      })
      .catch(() => {
        // 목록 API 장애 시 현재 검증된 기본 목록 유지
      })
  }, [])

  return assets
}
