import assert from 'node:assert/strict'
import test from 'node:test'
import { MAX_WATCHLIST, normalizeWatchlist } from './watchlist.js'

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

test('every supported symbol can be selected at once', () => {
  assert.deepEqual(normalizeWatchlist(supported, supported), supported)
})

test('selection is capped at the backend subscription limit', () => {
  const many = Array.from({ length: MAX_WATCHLIST + 3 }, (_, index) => `S${index}`)
  assert.equal(normalizeWatchlist(many, many).length, MAX_WATCHLIST)
})

test('required core symbols are restored before optional symbols', () => {
  assert.deepEqual(
    normalizeWatchlist(['SNDK', 'MU'], ['005930', '000660', 'SNDK', 'MU'], ['005930', '000660']),
    ['005930', '000660', 'SNDK', 'MU'],
  )
})
