import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  applyReadingSaveResult,
  buildReadingSaveItems,
  summarizeHouseholdRemovals
} from '../src/utils/readingSave.js'
import { createEditableSnapshot, isRowDirty } from '../src/utils/dirtyRows.js'

function extractFunctionBody(source, functionName) {
  const signature = `async function ${functionName}()`
  const signatureIndex = source.indexOf(signature)
  assert.notEqual(signatureIndex, -1, `未找到 ${functionName} 函数`)

  const bodyStart = source.indexOf('{', signatureIndex + signature.length)
  let depth = 0
  for (let index = bodyStart; index < source.length; index++) {
    if (source[index] === '{') depth++
    if (source[index] === '}') depth--
    if (depth === 0) return source.slice(bodyStart + 1, index)
  }
  assert.fail(`${functionName} 函数体括号不完整`)
}

function row(values) {
  const result = {
    waterMeterId: values.waterMeterId,
    currentReading: values.currentReading ?? null,
    chargeableUsage: values.chargeableUsage ?? null,
    note: values.note ?? ''
  }
  result.originalSnapshot = createEditableSnapshot(result)
  Object.assign(result, values.changed || {})
  return result
}

test('保存项只包含有合法本次表底的脏行，不依赖村组或勾选状态', () => {
  const rows = [
    row({ waterMeterId: 'M001', changed: { currentReading: '12.5', chargeableUsage: 2.5, note: '复核' } }),
    row({ waterMeterId: 'M002', changed: { note: '仅修改备注' } }),
    row({ waterMeterId: 'M003', currentReading: '8' }),
    row({ waterMeterId: 'M004', changed: { currentReading: '不是数字' } }),
    row({ waterMeterId: 'M005', changed: { currentReading: '0' } }),
    row({ waterMeterId: 'M006', currentReading: '10', changed: { currentReading: '   ' } })
  ]

  assert.deepEqual(buildReadingSaveItems(rows), [
    { waterMeterId: 'M001', currentReading: 12.5, chargeableUsage: 2.5, note: '复核' },
    { waterMeterId: 'M005', currentReading: 0 }
  ])
})

test('保存结果只清除成功行脏状态，失败行保留输入和脏状态', () => {
  const successful = row({ waterMeterId: 'M001', changed: { currentReading: '12.5' } })
  const failed = row({ waterMeterId: 'M002', changed: { currentReading: '9.5' } })

  applyReadingSaveResult([successful, failed], {
    details: [
      { waterMeterId: 'M001', status: 'success' },
      { waterMeterId: 'M002', status: 'fail', message: '账单已锁定' }
    ]
  })

  assert.equal(isRowDirty(successful), false)
  assert.equal(isRowDirty(failed), true)
  assert.equal(failed.currentReading, '9.5')
})

test('异常但已写入的抄表行也视为保存成功', () => {
  const abnormal = row({ waterMeterId: 'M001', changed: { currentReading: '120' } })

  applyReadingSaveResult([abnormal], {
    details: [{ waterMeterId: 'M001', status: 'abnormal' }]
  })

  assert.equal(isRowDirty(abnormal), false)
})

test('住户处理结果汇总物理删除、停用和欠费金额', () => {
  const summary = summarizeHouseholdRemovals([
    { action: 'DELETED', outstandingAmount: 0 },
    { action: 'DEACTIVATED', outstandingAmount: 12.4 },
    { action: 'DEACTIVATED', outstandingAmount: '3.60' }
  ])

  assert.deepEqual(summary, {
    deletedCount: 1,
    deactivatedCount: 2,
    outstandingAmount: 16,
    message: '已永久删除 1 户，停用并保留历史 2 户；停用户仍有欠费 16.00 元'
  })
})

test('页面保存入口不依赖村组或勾选住户，并统一构建脏行保存项', () => {
  const source = readFileSync(new URL('../src/views/Readings.vue', import.meta.url), 'utf8')
  const body = extractFunctionBody(source, 'batchSave')

  assert.doesNotMatch(body, /\bselectedVillage\b/)
  assert.doesNotMatch(body, /\bselectedHouseholdIds\b/)
  assert.match(body, /\bbuildReadingSaveItems\s*\(/)
})
