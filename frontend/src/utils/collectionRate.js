export function calculateCollectionRate(receivable, paid) {
  const total = Number(receivable || 0)
  if (total <= 0) return null
  return ((Number(paid || 0) / total) * 100).toFixed(1)
}
