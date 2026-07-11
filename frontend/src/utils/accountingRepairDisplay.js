const RECORD_TYPE_LABELS = {
  water_bill: '水费账单',
  prepayment_log: '预存抵扣记录',
  payment: '缴费记录',
  reading: '抄表记录'
}

const READING_ISSUES = new Set([
  'NEGATIVE_READING_USAGE',
  'ORPHAN_READING',
  'DUPLICATE_MONTHLY_READING',
  'BROKEN_READING_CHAIN'
])

const BILLING_ISSUES = new Set([
  'NEGATIVE_WATER_BILL_AMOUNT',
  'WATER_BILL_OVERPAID',
  'INCONSISTENT_WATER_BILL_STATUS',
  'ORPHAN_WATER_BILL',
  'PAYMENT_TOTAL_MISMATCH',
  'DUPLICATE_WATER_BILL',
  'ORPHAN_PREPAYMENT_LOG',
  'NEGATIVE_PREPAYMENT_BALANCE',
  'ORPHAN_WATER_PAYMENT'
])

function money(value) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function deductionMoney(value) {
  return `¥${Math.abs(Number(value ?? 0)).toFixed(2)}`
}

function byId(items = []) {
  return new Map(items.map(item => [item.id, item]))
}

function billSummary(bill = {}) {
  return `应收 ${money(bill.charge)}，已收 ${money(bill.actual)}，待收 ${money(bill.remainingDue)}，状态：${bill.status || '未知'}`
}

function prepaymentRows(before = {}, after = {}) {
  const beforeLogs = byId(before.movedLogs?.length ? before.movedLogs : before.prepaymentLogs)
  const afterLogs = byId(after.movedLogs?.length ? after.movedLogs : after.prepaymentLogs)
  return [...new Set([...beforeLogs.keys(), ...afterLogs.keys()])].flatMap(id => {
    const oldLog = beforeLogs.get(id) || {}
    const newLog = afterLogs.get(id) || {}
    if (oldLog.billId === newLog.billId) return []
    const log = newLog.id == null ? oldLog : newLog
    return [{
      key: `prepayment-${id}`,
      record: `预存抵扣记录 ${id}（${log.meter || '未知表号'}，抵扣金额 ${deductionMoney(log.amount)}）`,
      before: `关联水费账单 ${oldLog.billId ?? '无'}`,
      after: `关联水费账单 ${newLog.billId ?? '无'}`
    }]
  })
}

export function repairChangeRows(before = {}, after = {}) {
  const beforeBills = byId(before.bills)
  const afterBills = byId(after.bills)
  const bills = [...new Set([...beforeBills.keys(), ...afterBills.keys()])].flatMap(id => {
    const oldBill = beforeBills.get(id) || {}
    const newBill = afterBills.get(id) || {}
    if (billSummary(oldBill) === billSummary(newBill)) return []
    const bill = newBill.id == null ? oldBill : newBill
    return [{
      key: `bill-${id}`,
      record: `水费账单 ${id}（${bill.meter || '未知表号'}，${bill.year || '-'}年${bill.month || '-'}月）`,
      before: billSummary(oldBill),
      after: billSummary(newBill)
    }]
  })
  return [...bills, ...prepaymentRows(before, after)]
}

export function affectedRecordText(record = {}) {
  const type = RECORD_TYPE_LABELS[record.recordType] || '相关记录'
  const id = record.recordId == null ? '' : ` ${record.recordId}`
  return `${type}${id}：${record.description || '将进行核对处理'}`
}

export function repairNavigation(issueType) {
  if (READING_ISSUES.has(issueType)) {
    return { label: '前往抄表录入核对', path: '/readings' }
  }
  if (BILLING_ISSUES.has(issueType)) {
    return { label: '前往缴费管理核对', path: '/billing' }
  }
  if (issueType === 'ORPHAN_MATERIAL_PAYMENT') {
    return { label: '前往材料费管理核对', path: '/material-fee' }
  }
  if (issueType === 'ORPHAN_ADJUSTMENT_TARGET') {
    return { label: '查看调账记录核对', path: '/settings', targetId: 'accounting-controls' }
  }
  return null
}

export function createLatestRequestGate() {
  let currentRequestId = 0
  return {
    begin() {
      currentRequestId += 1
      return currentRequestId
    },
    isCurrent(requestId) {
      return requestId === currentRequestId
    },
    invalidate() {
      currentRequestId += 1
    }
  }
}

export function healthIssuesFromRepairResult(result = {}) {
  return Array.isArray(result.remainingIssues) ? result.remainingIssues : []
}
