import test from 'node:test'
import assert from 'node:assert/strict'
import { formatLocalDate, formatLocalMonth, formatLocalTimestamp } from '../src/utils/localDate.js'

test('北京时间凌晨使用本地当天和当月', () => {
  const value = new Date(2026, 6, 1, 0, 30, 0)
  assert.equal(formatLocalDate(value), '2026-07-01')
  assert.equal(formatLocalMonth(value), '2026-07')
})

test('文件时间戳包含本地日期和秒', () => {
  const value = new Date(2026, 6, 1, 8, 9, 7)
  assert.equal(formatLocalTimestamp(value), '20260701_080907')
})
