import test from 'node:test'
import assert from 'node:assert/strict'
import { inactiveHouseholdLabel } from '../src/utils/pendingBillDisplay.js'

test('待缴账单明确标记停用住户且正常住户不显示标签', () => {
  assert.equal(inactiveHouseholdLabel(false), '住户已停用')
  assert.equal(inactiveHouseholdLabel(true), '')
  assert.equal(inactiveHouseholdLabel(null), '')
})
