import test from 'node:test'
import assert from 'node:assert/strict'
import { createEditableSnapshot, isRowDirty, dirtyRows } from '../src/utils/dirtyRows.js'

test('加载后的原始行不是脏行', () => {
  const row = { waterMeterId: 'M001', currentReading: 10, chargeableUsage: 8, note: '正常' }
  row.originalSnapshot = createEditableSnapshot(row)
  assert.equal(isRowDirty(row), false)
})

test('只返回实际修改的行', () => {
  const first = { waterMeterId: 'M001', currentReading: 10, chargeableUsage: 8, note: '' }
  const second = { waterMeterId: 'M002', currentReading: 20, chargeableUsage: 12, note: '' }
  first.originalSnapshot = createEditableSnapshot(first)
  second.originalSnapshot = createEditableSnapshot(second)
  second.note = '复核'

  assert.deepEqual(dirtyRows([first, second]).map(row => row.waterMeterId), ['M002'])
})

test('恢复到原值后不再是脏行', () => {
  const row = { waterMeterId: 'M001', currentReading: null, chargeableUsage: null, note: '' }
  row.originalSnapshot = createEditableSnapshot(row)
  row.currentReading = 10
  assert.equal(isRowDirty(row), true)
  row.currentReading = null
  assert.equal(isRowDirty(row), false)
})
