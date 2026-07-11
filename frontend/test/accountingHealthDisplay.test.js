import test from 'node:test'
import assert from 'node:assert/strict'
import {
  healthMessage,
  healthReferenceLabel,
  healthSeverityLabel,
  healthTypeLabel
} from '../src/utils/accountingHealthDisplay.js'

const knownTypes = [
  'NEGATIVE_WATER_BILL_AMOUNT',
  'WATER_BILL_OVERPAID',
  'INCONSISTENT_WATER_BILL_STATUS',
  'ORPHAN_WATER_BILL',
  'PAYMENT_TOTAL_MISMATCH',
  'DUPLICATE_WATER_BILL',
  'NEGATIVE_READING_USAGE',
  'ORPHAN_READING',
  'DUPLICATE_MONTHLY_READING',
  'BROKEN_READING_CHAIN',
  'ORPHAN_PREPAYMENT_LOG',
  'NEGATIVE_PREPAYMENT_BALANCE',
  'ORPHAN_ADJUSTMENT_TARGET',
  'ORPHAN_WATER_PAYMENT',
  'ORPHAN_MATERIAL_PAYMENT'
]

test('全部已知账务问题都有中文名称和说明', () => {
  for (const type of knownTypes) {
    const label = healthTypeLabel(type)
    const message = healthMessage({ type, message: 'raw english message' })

    assert.match(label, /[\u4e00-\u9fff]/, `${type} 缺少中文名称`)
    assert.notEqual(label, type)
    assert.match(message, /[\u4e00-\u9fff]/, `${type} 缺少中文说明`)
    assert.notEqual(message, 'raw english message')
  }
})

test('严重程度显示为普通用户可理解的中文', () => {
  assert.equal(healthSeverityLabel('ERROR'), '严重')
  assert.equal(healthSeverityLabel('WARNING'), '提醒')
  assert.equal(healthSeverityLabel('OTHER'), '未知')
})

test('关联对象显示中文名称和记录编号', () => {
  assert.equal(healthReferenceLabel('water_bill', 1), '水费账单（记录 1）')
  assert.equal(healthReferenceLabel('reading', 2), '抄表记录（记录 2）')
  assert.equal(healthReferenceLabel('payment', null), '水费缴费记录')
  assert.equal(healthReferenceLabel('unknown_ref', 3), '相关记录（记录 3）')
})

test('未知问题不显示后端英文代码和说明', () => {
  assert.equal(healthTypeLabel('NEW_UNKNOWN_TYPE'), '未知账务问题')
  assert.equal(
    healthMessage({ type: 'NEW_UNKNOWN_TYPE', message: 'raw english message' }),
    '系统发现一项暂未识别的账务问题，请联系系统维护人员核对。'
  )
})

test('截图中的收款合计异常整行显示通俗中文', () => {
  const issue = {
    type: 'PAYMENT_TOTAL_MISMATCH',
    severity: 'ERROR',
    refType: 'water_bill',
    refId: 1,
    message: 'payment and prepayment total does not match bill paid amount'
  }

  assert.equal(healthSeverityLabel(issue.severity), '严重')
  assert.equal(healthTypeLabel(issue.type), '账单收款合计不一致')
  assert.equal(healthReferenceLabel(issue.refType, issue.refId), '水费账单（记录 1）')
  assert.equal(
    healthMessage(issue),
    '该账单的缴费记录和预存抵扣合计，与账单已收金额不一致，请核对相关收款记录。'
  )
})
