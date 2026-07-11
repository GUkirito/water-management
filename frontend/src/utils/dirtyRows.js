const EDITABLE_FIELDS = ['currentReading', 'chargeableUsage', 'note']

export function createEditableSnapshot(row) {
  return Object.fromEntries(EDITABLE_FIELDS.map(field => [field, normalize(row[field])]))
}

export function isRowDirty(row) {
  if (!row.originalSnapshot) return false
  return EDITABLE_FIELDS.some(field => normalize(row[field]) !== row.originalSnapshot[field])
}

export function dirtyRows(rows) {
  return rows.filter(isRowDirty)
}

function normalize(value) {
  if (value === undefined || value === null) return null
  if (typeof value === 'string') return value.trim() || null
  return value
}
