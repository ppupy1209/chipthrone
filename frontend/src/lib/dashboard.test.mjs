import assert from 'node:assert/strict'
import test from 'node:test'
import { compareMarketCap, simulateReversal } from './marketCap.ts'
import { brandColor, brandForeground } from './brandColor.ts'
import { matchesAssetGroup } from '../data/assetGroups.ts'

function company(code, price, sharesOutstanding = 10) {
  return { code, name: code, price, sharesOutstanding }
}

test('classification preserves the existing semiconductor and M7 catalog and includes new symbols in all', () => {
  assert.equal(matchesAssetGroup('TSM', 'semiconductor'), true)
  assert.equal(matchesAssetGroup('NVDA', 'm7'), true)
  assert.equal(matchesAssetGroup('NVDA', 'semiconductor'), false)
  assert.equal(matchesAssetGroup('NEW', 'all'), true)
  assert.equal(matchesAssetGroup('NEW', 'm7'), false)
})

test('brand labels use dark ink on bright colors and light ink on dark colors', () => {
  assert.equal(brandForeground(brandColor('NVDA').light), '#191f28')
  assert.equal(brandForeground(brandColor('AMZN').light), '#191f28')
  assert.equal(brandForeground(brandColor('AAPL').light), '#ffffff')
  assert.equal(brandForeground(brandColor('AAPL').dark), '#191f28')
  assert.equal(brandForeground(brandColor('005930').light), '#ffffff')
})

test('the zero-percent scenario reproduces the current market-cap gap', () => {
  const comparison = compareMarketCap(company('005930', 100), company('000660', 80))
  const scenario = simulateReversal(comparison, 0)
  assert.equal(scenario.price, 80)
  assert.equal(scenario.gap, comparison.gap)
  assert.equal(scenario.status, 'behind')
})

test('reaching the target price is a tie, not a reversal', () => {
  const comparison = compareMarketCap(company('005930', 100), company('000660', 80))
  const scenario = simulateReversal(comparison, comparison.reversalPct)
  assert.equal(scenario.price, comparison.reversalPrice)
  assert.equal(scenario.status, 'tied')
})

test('a scenario above the target crosses the cap gap without changing live company prices', () => {
  const samsung = company('005930', 100)
  const hynix = company('000660', 80)
  const comparison = compareMarketCap(samsung, hynix)
  const scenario = simulateReversal(comparison, 30)
  assert.equal(scenario.status, 'ahead')
  assert.equal(scenario.gap, -40)
  assert.equal(hynix.price, 80)
  assert.equal(samsung.price, 100)
})

test('when SK hynix leads the scenario follows Samsung as the challenger', () => {
  const comparison = compareMarketCap(company('005930', 70, 11), company('000660', 90, 10))
  assert.equal(comparison.leader.code, '000660')
  assert.equal(comparison.challenger.code, '005930')
  assert.equal(simulateReversal(comparison, comparison.reversalPct).status, 'tied')
})

test('missing quotes cannot produce a market-cap comparison', () => {
  assert.equal(compareMarketCap(company('005930', 0), company('000660', 80)), null)
})
