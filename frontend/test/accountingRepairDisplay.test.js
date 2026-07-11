import test from 'node:test'
import assert from 'node:assert/strict'
import {
  affectedRecordText,
  createLatestRequestGate,
  healthIssuesFromRepairResult,
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
  assert.equal(bill.before, '应收 ¥5.40，已收 ¥0.00，待收 ¥5.40，状态：未收')
  assert.equal(bill.after, '应收 ¥5.40，已收 ¥1.40，待收 ¥4.00，状态：部分收')
})

test('账单金额保留负号但预存抵扣金额按抵扣语义显示正数', () => {
  const rows = repairChangeRows(
    {
      bills: [{ id: 8, meter: 'WM-NEG', year: 2026, month: 8, charge: -5.4, actual: -1.4, status: '异常', remainingDue: -4 }],
      prepaymentLogs: [{ id: 9, billId: 8, meter: 'WM-NEG', type: 'AUTO_DEDUCT', amount: -1.4 }]
    },
    {
      bills: [{ id: 8, meter: 'WM-NEG', year: 2026, month: 8, charge: 0, actual: 0, status: '无需缴费', remainingDue: 0 }],
      prepaymentLogs: [{ id: 9, billId: 10, meter: 'WM-NEG', type: 'AUTO_DEDUCT', amount: -1.4 }]
    }
  )

  assert.equal(rows.find(row => row.key === 'bill-8').before, '应收 ¥-5.40，已收 ¥-1.40，待收 ¥-4.00，状态：异常')
  assert.equal(rows.find(row => row.key === 'prepayment-9').record, '预存抵扣记录 9（WM-NEG，抵扣金额 ¥1.40）')
})

test('修复变化用关联账单描述预存抵扣改绑且不泄露内部字段名', () => {
  const rows = repairChangeRows(before, after)
  const log = rows.find(row => row.key === 'prepayment-3')

  assert.equal(log.record, '预存抵扣记录 3（210500692，抵扣金额 ¥1.40）')
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
  assert.deepEqual(repairNavigation('ORPHAN_ADJUSTMENT_TARGET'), {
    label: '查看调账记录核对',
    path: '/settings',
    targetId: 'accounting-controls'
  })
  assert.equal(repairNavigation('UNKNOWN'), null)
})

test('预览请求门闩只接受最新请求且关闭后使当前请求失效', () => {
  const gate = createLatestRequestGate()
  const requestA = gate.begin()
  const requestB = gate.begin()

  assert.equal(gate.isCurrent(requestA), false)
  assert.equal(gate.isCurrent(requestB), true)

  gate.invalidate()
  assert.equal(gate.isCurrent(requestB), false)
})

test('修复完成直接采用事务内复检结果', () => {
  const remainingIssues = [{ type: 'DUPLICATE_WATER_BILL', refId: 7 }]

  assert.equal(healthIssuesFromRepairResult({ remainingIssues }), remainingIssues)
  assert.deepEqual(healthIssuesFromRepairResult({}), [])
})
