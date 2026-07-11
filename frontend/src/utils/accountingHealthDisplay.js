const ISSUE_DISPLAY = {
  NEGATIVE_WATER_BILL_AMOUNT: {
    label: '水费账单金额为负数',
    message: '该账单的用水量或应收水费小于 0，请核对抄表读数和水价。'
  },
  WATER_BILL_OVERPAID: {
    label: '水费实收大于应收',
    message: '该账单的已收金额超过应收水费，请核对缴费和调账记录。'
  },
  INCONSISTENT_WATER_BILL_STATUS: {
    label: '账单收款状态不一致',
    message: '该账单显示的收款状态与应收、已收金额不一致，请核对账单状态。'
  },
  ORPHAN_WATER_BILL: {
    label: '水费账单找不到对应住户',
    message: '该水费账单找不到对应的住户资料，请核对水表号或住户资料。'
  },
  PAYMENT_TOTAL_MISMATCH: {
    label: '账单收款合计不一致',
    message: '该账单的缴费记录和预存抵扣合计，与账单已收金额不一致，请核对相关收款记录。'
  },
  DUPLICATE_WATER_BILL: {
    label: '同月水费账单重复',
    message: '同一水表在同一个月份存在多张水费账单，请核对并保留正确账单。'
  },
  NEGATIVE_READING_USAGE: {
    label: '抄表用水量为负数',
    message: '本次抄表计算出的用水量小于 0，请核对上次和本次水表读数。'
  },
  ORPHAN_READING: {
    label: '抄表记录找不到对应住户',
    message: '该抄表记录找不到对应的住户资料，请核对水表号或住户资料。'
  },
  DUPLICATE_MONTHLY_READING: {
    label: '同月抄表记录重复',
    message: '同一水表在同一个月份存在多条抄表记录，请核对并保留正确记录。'
  },
  BROKEN_READING_CHAIN: {
    label: '前后抄表读数不衔接',
    message: '本次抄表的上次读数与前一条记录的本次读数不一致，请按时间顺序核对抄表记录。'
  },
  ORPHAN_PREPAYMENT_LOG: {
    label: '预存记录找不到对应住户',
    message: '该预存水费记录找不到对应的住户资料，请核对水表号或住户资料。'
  },
  NEGATIVE_PREPAYMENT_BALANCE: {
    label: '预存余额为负数',
    message: '该水表的预存水费余额小于 0，请核对预存和抵扣记录。'
  },
  ORPHAN_ADJUSTMENT_TARGET: {
    label: '调账记录找不到原始账目',
    message: '该调账记录对应的水费账单或材料费记录已不存在，请核对相关账目。'
  },
  ORPHAN_WATER_PAYMENT: {
    label: '水费缴费记录找不到账单',
    message: '该水费缴费记录找不到对应的水费账单，请核对缴费记录和账单。'
  },
  ORPHAN_MATERIAL_PAYMENT: {
    label: '材料费缴费记录找不到应收记录',
    message: '该材料费缴费记录找不到对应的材料费应收记录，请核对相关记录。'
  }
}

const REFERENCE_LABELS = {
  water_bill: '水费账单',
  reading: '抄表记录',
  prepayment_log: '预存记录',
  payment: '水费缴费记录',
  material_payment: '材料费缴费记录',
  accounting_adjustment: '调账记录'
}

export function healthSeverityLabel(severity) {
  return {
    ERROR: '严重',
    WARNING: '提醒',
    INFO: '提示'
  }[severity] || '未知'
}

export function healthTypeLabel(type) {
  return ISSUE_DISPLAY[type]?.label || '未知账务问题'
}

export function healthMessage(issue = {}) {
  return ISSUE_DISPLAY[issue.type]?.message
    || '系统发现一项暂未识别的账务问题，请联系系统维护人员核对。'
}

export function healthReferenceLabel(refType, refId) {
  const label = REFERENCE_LABELS[refType] || '相关记录'
  return refId == null ? label : `${label}（记录 ${refId}）`
}
