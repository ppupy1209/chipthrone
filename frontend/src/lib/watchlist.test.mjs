import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeWatchlist } from './watchlist.js'

const supported = [
  '005930',
  '000660',
  'SNDK',
  'MU',
  'AVGO',
  'NVDA',
  'TSM',
  'SKHY',
  'MSFT',
]

test('invalid and duplicate stored symbols are removed and minimum is restored', () => {
  assert.deepEqual(normalizeWatchlist(['999999', '005930', '005930'], supported), ['005930', '000660'])
})

test('selection is capped at two core and six optional symbols', () => {
  assert.deepEqual(normalizeWatchlist(supported, supported), supported.slice(0, 8))
})

test('required core symbols are restored before optional symbols', () => {
  assert.deepEqual(
    normalizeWatchlist(['SNDK', 'MU'], ['005930', '000660', 'SNDK', 'MU'], ['005930', '000660']),
    ['005930', '000660', 'SNDK', 'MU'],
  )
})
