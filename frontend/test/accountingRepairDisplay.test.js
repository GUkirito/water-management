import test from 'node:test'
import assert from 'node:assert/strict'
import {
  affectedRecordText,
  repairChangeRows,
  repairNavigation
} from '../src/utils/accountingRepairDisplay.js'

const before = {
  wrongBillId: 1,
  bills: [
    { id: 1, meter: '210503109', year: 2026, month: 7, charge: 14.4, actual: 15.8, status: '已收', remainingDue: 0 },
    { id: 2, meter: '210500692', year: 2026, month: 7, charge: 5.4, actual: 0, status: '未收', remainingDue: 5.4 }
  ],
  prepaymentLogs: [
    { id: 3, billId: 1, meter: '210500692', type: 'AUTO_DEDUCT', amount: -1.4 }
  ],
  movedLogs: [
    { id: 3, billId: 1, meter: '210500692', type: 'AUTO_DEDUCT', amount: -1.4, fromBillId: 1, toBillId: 2 }
  ]
}

const after = {
  ...before,
  bills: [
    { id: 1, meter: '210503109', year: 2026, month: 7, charge: 14.4, actual: 14.4, status: '已收', remainingDue: 0 },
    { id: 2, meter: '210500692', year: 2026, month: 7, charge: 5.4, actual: 1.4, status: '部分收', remainingDue: 4 }
  ],
  prepaymentLogs: [
    { id: 3, billId: 2, meter: '210500692', type: 'AUTO_DEDUCT', amount: -1.4 }
  ],
  movedLogs: [
    { id: 3, billId: 2, meter: '210500692', type: 'AUTO_DEDUCT', amount: -1.4, fromBillId: 1, toBillId: 2 }
  ]
}

test('修复变化将账单金额和状态显示为普通中文', () => {
  const rows = repairChangeRows(before, after)
  const bill = rows.find(row => row.key === 'bill-2')

  assert.equal(bill.record, '水费账单 2（210500692，2026年7月）')
  assert.equal(bill.before, '已收 ¥0.00，待收 ¥5.40，状态：未收')
  assert.equal(bill.after, '已收 ¥1.40，待收 ¥4.00，状态：部分收')
})

test('修复变化用关联账单描述预存抵扣改绑且不泄露内部字段名', () => {
  const rows = repairChangeRows(before, after)
  const log = rows.find(row => row.key === 'prepayment-3')

  assert.equal(log.record, '预存抵扣记录 3（210500692，金额 ¥1.40）')
  assert.equal(log.before, '关联水费账单 1')
  assert.equal(log.after, '关联水费账单 2')
  assert.doesNotMatch(JSON.stringify(rows), /wrongBillId|actual|remainingDue|prepaymentLogs|movedLogs/)
})

test('受影响记录和不可自动修复问题给出中文说明与业务入口', () => {
  assert.equal(
    affectedRecordText({ recordType: 'water_bill', recordId: 2, description: '将按有效流水重算实收金额和状态' }),
    '水费账单 2：将按有效流水重算实收金额和状态'
  )
  assert.deepEqual(repairNavigation('BROKEN_READING_CHAIN'), { label: '前往抄表录入核对', path: '/readings' })
  assert.deepEqual(repairNavigation('ORPHAN_WATER_PAYMENT'), { label: '前往缴费管理核对', path: '/billing' })
  assert.deepEqual(repairNavigation('ORPHAN_MATERIAL_PAYMENT'), { label: '前往材料费管理核对', path: '/material-fee' })
  assert.deepEqual(repairNavigation('ORPHAN_ADJUSTMENT_TARGET'), { label: '查看调账记录核对', path: '/settings#accounting-controls' })
  assert.equal(repairNavigation('UNKNOWN'), null)
})
