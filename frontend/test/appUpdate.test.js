import test from 'node:test'
import assert from 'node:assert/strict'
import {
  describeUpdateError,
  formatBytes,
  formatEta,
  formatSpeed,
  getUpdateCheckFailurePresentation,
  shouldShowUpdate
} from '../src/utils/appUpdate.js'

test('仅可用更新状态需要展示更新对话框', () => {
  assert.equal(shouldShowUpdate({ status: 'DEFERRED' }), false)
  assert.equal(shouldShowUpdate({ status: 'SKIPPED' }), false)
  assert.equal(shouldShowUpdate({ status: 'AVAILABLE' }), true)
})

test('启动检查失败使用通知，手动检查失败使用对话框', () => {
  assert.equal(getUpdateCheckFailurePresentation('startup'), 'notification')
  assert.equal(getUpdateCheckFailurePresentation('manual'), 'dialog')
})

test('格式化下载大小、速度和剩余时间', () => {
  assert.equal(formatBytes(64600000), '61.6 MB')
  assert.equal(formatSpeed(2936012), '2.8 MB/秒')
  assert.equal(formatEta(24), '预计还需 24 秒')
})

test('格式化进度边界值', () => {
  assert.equal(formatBytes(0), '0.0 KB')
  assert.equal(formatBytes(1024 * 1024), '1.0 MB')
  assert.equal(formatEta(59), '预计还需 59 秒')
  assert.equal(formatEta(60), '预计还需 1 分钟')
  assert.equal(formatEta(Number.POSITIVE_INFINITY), '')
})

test('网络错误使用普通用户文案并保留技术详情', () => {
  const result = describeUpdateError('error sending request for url github.com')

  assert.equal(result.title, '暂时无法检查更新')
  assert.match(result.message, /网络无法连接 GitHub/)
  assert.match(result.technicalDetail, /error sending request/)
})

test('检查更新的 GitHub 下载地址连接失败仍归类为检查失败', () => {
  const detail = 'error sending request for url (https://github.com/GUkirito/water-management/releases/latest/download/latest.json)'
  const result = describeUpdateError(detail)

  assert.equal(result.title, '暂时无法检查更新')
  assert.match(result.message, /网络无法连接 GitHub/)
  assert.equal(result.technicalDetail, detail)
})

test('更新错误按阶段转换为中文主文案', () => {
  const cases = [
    ['request timeout', '暂时无法检查更新'],
    ['invalid manifest json', '更新信息暂时不可用'],
    ['download stream interrupted', '更新包下载未完成'],
    ['signature verify failed', '安装包未通过安全校验'],
    ['install failed', '更新安装失败'],
    ['unexpected updater failure', '更新未能完成']
  ]

  for (const [detail, title] of cases) {
    const result = describeUpdateError(detail)
    assert.equal(result.title, title)
    assert.doesNotMatch(result.message, /request|timeout|manifest|json|download|stream|signature|verify|install|unexpected|updater|failure/i)
    assert.equal(result.technicalDetail, detail)
  }
})

test('明确更新阶段优先于宽泛网络关键字', () => {
  const result = describeUpdateError('signature verify failed for github release')

  assert.equal(result.title, '安装包未通过安全校验')
  assert.equal(result.technicalDetail, 'signature verify failed for github release')
})

test('无效进度值使用占位语义', () => {
  for (const value of [null, undefined, Number.NaN, -1]) {
    assert.equal(formatBytes(value), '-')
    assert.equal(formatSpeed(value), '-')
    assert.equal(formatEta(value), '')
  }
})
