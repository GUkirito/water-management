import test from 'node:test'
import assert from 'node:assert/strict'
import { calculateCollectionRate } from '../src/utils/collectionRate.js'

test('没有应收时收缴率为空', () => {
  assert.equal(calculateCollectionRate(0, 0), null)
})

test('有应收时按实收金额计算收缴率', () => {
  assert.equal(calculateCollectionRate(200, 150), '75.0')
})
