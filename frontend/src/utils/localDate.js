const pad2 = value => String(value).padStart(2, '0')

export function formatLocalDate(date = new Date()) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function formatLocalMonth(date = new Date()) {
  return formatLocalDate(date).slice(0, 7)
}

export function formatLocalTimestamp(date = new Date()) {
  return `${date.getFullYear()}${pad2(date.getMonth() + 1)}${pad2(date.getDate())}_${pad2(date.getHours())}${pad2(date.getMinutes())}${pad2(date.getSeconds())}`
}
