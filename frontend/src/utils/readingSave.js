import { createEditableSnapshot, dirtyRows } from './dirtyRows.js'

export function hasValidCurrentReading(row) {
  if (row.currentReading === null || row.currentReading === undefined || row.currentReading === '') return false
  if (typeof row.currentReading === 'string' && !row.currentReading.trim()) return false
  const value = Number(row.currentReading)
  return Number.isFinite(value) && value >= 0
}

export function buildReadingSaveItems(rows) {
  return dirtyRows(rows)
    .filter(hasValidCurrentReading)
    .map(row => {
      const item = {
        waterMeterId: row.waterMeterId,
        currentReading: Number(row.currentReading)
      }
      const chargeableUsage = Number(row.chargeableUsage)
      if (row.chargeableUsage !== null && row.chargeableUsage !== '' && Number.isFinite(chargeableUsage) && chargeableUsage >= 0) {
        item.chargeableUsage = chargeableUsage
      }
      if (row.note) item.note = row.note
      return item
    })
}

export function applyReadingSaveResult(rows, result) {
  const successfulMeters = new Set((result?.details || [])
    .filter(detail => detail.status === 'success' || detail.status === 'abnormal')
    .map(detail => detail.waterMeterId))

  rows.forEach(row => {
    if (successfulMeters.has(row.waterMeterId)) {
      row.originalSnapshot = createEditableSnapshot(row)
    }
  })
}

export function summarizeHouseholdRemovals(results) {
  const rows = Array.isArray(results) ? results : results ? [results] : []
  const deletedCount = rows.filter(row => row.action === 'DELETED').length
  const deactivatedCount = rows.filter(row => row.action === 'DEACTIVATED').length
  const outstandingAmount = rows.reduce((sum, row) => {
    const amount = Number(row.outstandingAmount)
    return sum + (Number.isFinite(amount) && amount > 0 ? amount : 0)
  }, 0)

  const parts = []
  if (deletedCount) parts.push(`已永久删除 ${deletedCount} 户`)
  if (deactivatedCount) parts.push(`停用并保留历史 ${deactivatedCount} 户`)
  let message = parts.join('，') || '住户处理完成'
  if (outstandingAmount > 0) {
    message += `；停用户仍有欠费 ${outstandingAmount.toFixed(2)} 元`
  }

  return { deletedCount, deactivatedCount, outstandingAmount, message }
}
