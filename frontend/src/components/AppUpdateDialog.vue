<template>
  <el-dialog
    v-model="visible"
    class="wm-update-dialog"
    modal-class="wm-update-overlay"
    width="580px"
    :close-on-click-modal="mode !== 'download'"
    :close-on-press-escape="mode !== 'download'"
    :show-close="mode !== 'download'"
    :before-close="beforeClose"
    align-center
  >
    <template #header="{ titleId, titleClass }">
      <h2 :id="titleId" :class="[titleClass, 'wm-visually-hidden']">{{ dialogAriaLabel }}</h2>
    </template>

    <section
      v-if="mode === 'offer'"
      class="wm-update-state"
      aria-live="polite"
    >
      <header class="wm-update-header">
        <span class="wm-update-icon" aria-hidden="true">
          <el-icon><Download /></el-icon>
        </span>
        <div class="wm-update-heading">
          <small>系统更新</small>
          <h2>有新版本可以安装</h2>
          <p>确认后才会开始下载，更新前可以继续正常使用系统。</p>
        </div>
      </header>

      <div class="wm-update-main">
        <div class="wm-update-versions" aria-label="版本变化">
          <div>
            <span>当前版本</span>
            <strong>{{ currentVersion }}</strong>
          </div>
          <el-icon class="wm-update-arrow" aria-hidden="true"><Right /></el-icon>
          <div class="wm-update-target">
            <span>新版本</span>
            <strong>{{ targetVersion }}</strong>
          </div>
        </div>

        <h3 class="wm-update-section-title">本次更新</h3>
        <ul class="wm-update-notes">
          <li v-for="(line, index) in releaseNotes" :key="`${index}-${line}`">{{ line }}</li>
        </ul>

        <div class="wm-update-skip">
          <el-checkbox v-model="skipThisVersion">
            启动时不再提醒 {{ targetVersion }}
          </el-checkbox>
          <small>仍可随时到“系统设置”中手动检查并安装</small>
        </div>
      </div>

      <footer class="wm-update-footer">
        <span class="wm-update-footnote">安装时程序会自动重启</span>
        <div class="wm-update-actions">
          <el-button :loading="postponing" @click="postpone">稍后处理</el-button>
          <el-button type="primary" :disabled="postponing" @click="downloadUpdate">
            <el-icon><Download /></el-icon>
            <span>下载并安装</span>
          </el-button>
        </div>
      </footer>
    </section>

    <section
      v-else-if="mode === 'download'"
      class="wm-update-state"
      aria-live="polite"
      aria-busy="true"
    >
      <header class="wm-update-header">
        <span class="wm-update-icon" aria-hidden="true">
          <el-icon class="is-loading"><Loading /></el-icon>
        </span>
        <div class="wm-update-heading">
          <small>{{ targetVersion }}</small>
          <h2>{{ downloadHeading }}</h2>
          <p>下载完成后会自动进行安全校验，校验通过才会开始安装。</p>
        </div>
      </header>

      <div class="wm-update-main wm-update-progress-area">
        <div class="wm-update-progress-top">
          <span>{{ downloadedSummary }}</span>
          <strong v-if="hasKnownTotal">{{ progress.percent }}%</strong>
          <strong v-else class="wm-update-calculating">正在计算</strong>
        </div>
        <el-progress
          :percentage="hasKnownTotal ? progress.percent : 100"
          :indeterminate="!hasKnownTotal"
          :show-text="false"
          :stroke-width="9"
          color="#32b5d6"
        />
        <div class="wm-update-metrics">
          <span>当前速度 {{ formatSpeed(progress.bytesPerSecond) }}</span>
          <span>{{ hasKnownTotal ? (formatEta(progress.etaSeconds) || '正在计算剩余时间') : '正在获取文件大小' }}</span>
        </div>

        <ol class="wm-update-stages" aria-label="更新进度阶段">
          <li v-for="stage in stages" :key="stage.phase" :class="stage.state">
            <span aria-hidden="true"></span>
            {{ stage.label }}
          </li>
        </ol>
      </div>

      <footer class="wm-update-footer">
        <span class="wm-update-footnote">更新过程中请保持程序运行</span>
        <el-button disabled>处理中</el-button>
      </footer>
    </section>

    <section v-else class="wm-update-state" aria-live="assertive">
      <header class="wm-update-header">
        <span class="wm-update-icon wm-update-icon--error" aria-hidden="true">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <div class="wm-update-heading">
          <small>系统更新</small>
          <h2>{{ errorInfo.title }}</h2>
          <p>现有程序和数据不受影响，可以继续正常使用。</p>
        </div>
      </header>

      <div class="wm-update-main">
        <div class="wm-update-error" role="alert">
          <strong>{{ errorInfo.title }}</strong>
          <span>{{ errorInfo.message }}</span>
        </div>

        <div v-for="step in failureSteps" :key="step.label" class="wm-update-trace">
          <span>{{ step.label }}</span>
          <strong :class="`is-${step.state}`">{{ step.status }}</strong>
        </div>

        <el-collapse v-if="errorInfo.technicalDetail" v-model="expandedPanels" class="wm-update-tech">
          <el-collapse-item name="technical" title="查看技术详情">
            <pre>{{ errorInfo.technicalDetail }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>

      <footer class="wm-update-footer">
        <span class="wm-update-footnote">详细记录已保存到本机日志</span>
        <div class="wm-update-actions">
          <el-button :disabled="retrying" @click="dismissFailure">稍后再试</el-button>
          <el-button type="primary" :loading="retrying" @click="retryFailure">{{ retryButtonLabel }}</el-button>
        </div>
      </footer>
    </section>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElNotification } from 'element-plus'
import { Download, Loading, Right, WarningFilled } from '@element-plus/icons-vue'
import {
  describeUpdateError,
  formatBytes,
  formatEta,
  formatSpeed,
  getUpdateCheckFailurePresentation,
  shouldShowUpdate
} from '../utils/appUpdate.js'

const visible = ref(false)
const mode = ref('offer')
const checkResult = ref(null)
const skipThisVersion = ref(false)
const progress = ref({
  phase: 'CONNECTING',
  downloadedBytes: 0,
  totalBytes: null,
  percent: null,
  bytesPerSecond: 0,
  etaSeconds: null
})
const technicalDetail = ref('')
const failureSource = ref('check')
const failurePhase = ref('CONNECTING')
const expandedPanels = ref([])
const postponingOperationId = ref(null)
const retryOperation = ref(null)
let checkRequestId = 0
let preferenceOperationId = 0

const phaseOrder = ['CONNECTING', 'DOWNLOADING', 'VERIFYING', 'INSTALLING']
const phaseLabels = {
  CONNECTING: '连接服务器',
  DOWNLOADING: '下载安装包',
  VERIFYING: '安全校验',
  INSTALLING: '安装重启'
}

const invoke = (...args) => {
  const tauriInvoke = window.__TAURI__?.core?.invoke || window.__TAURI__?.invoke
  if (!tauriInvoke) return Promise.reject(new Error('桌面更新服务不可用'))
  return tauriInvoke(...args)
}

const isTauriEnvironment = () => typeof window !== 'undefined' && !!window.__TAURI__
const currentVersion = computed(() => checkResult.value?.currentVersion || '-')
const targetVersion = computed(() => checkResult.value?.targetVersion || '-')
const releaseNotes = computed(() => {
  const lines = String(checkResult.value?.notes || '')
    .split(/\r?\n/)
    .map(line => line.trim().replace(/^[-*#]+\s*/, ''))
    .filter(Boolean)
  return lines.length ? lines : ['优化桌面端更新体验并修复已知问题。']
})
const errorInfo = computed(() => {
  if (failureSource.value === 'preference') {
    return {
      title: '提醒设置未保存',
      message: '本次提醒设置没有保存，请重新保存或稍后再试。',
      technicalDetail: technicalDetail.value
    }
  }
  if (failureSource.value === 'download') {
    const phaseMessages = {
      CONNECTING: ['暂时无法连接更新服务器', '当前网络未能连接 GitHub 更新服务器，请检查网络后重试。'],
      DOWNLOADING: ['更新包下载未完成', '网络连接可能中断，请重新检查后再次确认下载。'],
      VERIFYING: ['安装包未通过安全检查', '系统不会安装未通过检查的更新包，请稍后重试。'],
      INSTALLING: ['更新安装未完成', '现有版本仍可继续使用，请稍后重新检查更新。']
    }
    const [title, message] = phaseMessages[failurePhase.value] || phaseMessages.CONNECTING
    return { title, message, technicalDetail: technicalDetail.value }
  }
  return describeUpdateError(technicalDetail.value)
})
const failureSteps = computed(() => {
  if (failureSource.value === 'preference') {
    return [{ label: '启动提醒设置', status: '未保存', state: 'error' }]
  }

  if (failureSource.value === 'check') {
    return [{ label: '检查更新', status: '未完成', state: 'error' }]
  }

  const activeIndex = Math.max(0, phaseOrder.indexOf(failurePhase.value))
  const statusByPhase = {
    CONNECTING: '连接失败',
    DOWNLOADING: '下载未完成',
    VERIFYING: '安全检查未通过',
    INSTALLING: '安装未完成'
  }
  return phaseOrder.map((phase, index) => ({
    label: phaseLabels[phase],
    status: index < activeIndex ? '已完成' : index === activeIndex ? statusByPhase[phase] : '尚未开始',
    state: index < activeIndex ? 'success' : index === activeIndex ? 'error' : 'pending'
  }))
})
const retryButtonLabel = computed(() => {
  if (failureSource.value === 'preference') return '重新保存'
  if (failureSource.value === 'download') return '重新检查更新'
  return '重新检查'
})
const postponing = computed(() => postponingOperationId.value !== null)
const retrying = computed(() => retryOperation.value !== null)
const hasKnownTotal = computed(() => Number.isFinite(progress.value.totalBytes) && progress.value.totalBytes > 0 && Number.isFinite(progress.value.percent))
const downloadedSummary = computed(() => {
  const downloaded = formatBytes(progress.value.downloadedBytes)
  return hasKnownTotal.value ? `${downloaded} / ${formatBytes(progress.value.totalBytes)}` : `已下载 ${downloaded}`
})
const stages = computed(() => {
  const activeIndex = Math.max(0, phaseOrder.indexOf(progress.value.phase))
  return phaseOrder.map((phase, index) => ({
    phase,
    label: phaseLabels[phase],
    state: index < activeIndex ? 'is-done' : index === activeIndex ? 'is-active' : ''
  }))
})
const downloadHeading = computed(() => ({
  CONNECTING: '正在连接更新服务器',
  DOWNLOADING: '正在下载更新',
  VERIFYING: '正在进行安全校验',
  INSTALLING: '正在安装更新'
}[progress.value.phase] || '正在准备更新'))
const dialogAriaLabel = computed(() => ({
  offer: '发现新版本',
  download: downloadHeading.value,
  failure: errorInfo.value.title
}[mode.value]))

function resetProgress() {
  progress.value = {
    phase: 'CONNECTING',
    downloadedBytes: 0,
    totalBytes: null,
    percent: null,
    bytesPerSecond: 0,
    etaSeconds: null
  }
}

function invalidatePendingChecks() {
  checkRequestId += 1
  if (retryOperation.value?.kind === 'check') retryOperation.value = null
}

function beginCheckRequest() {
  checkRequestId += 1
  return checkRequestId
}

function isCurrentCheckRequest(requestId) {
  return requestId === checkRequestId && mode.value !== 'download'
}

function invalidatePendingPreferences() {
  preferenceOperationId += 1
  postponingOperationId.value = null
  if (retryOperation.value?.kind === 'preference') retryOperation.value = null
}

function beginPreferenceOperation() {
  preferenceOperationId += 1
  return {
    operationId: preferenceOperationId,
    version: targetVersion.value
  }
}

function isCurrentPreferenceOperation(operationId, version) {
  return operationId === preferenceOperationId && version === targetVersion.value
}

function openOffer(result) {
  if (mode.value === 'download' || !result?.targetVersion) return
  checkResult.value = result
  skipThisVersion.value = false
  technicalDetail.value = ''
  expandedPanels.value = []
  mode.value = 'offer'
  visible.value = true
}

function openFailure(error, source = 'check', phase = 'CONNECTING') {
  if (mode.value === 'download') return
  technicalDetail.value = String(error?.message || error || '')
  failureSource.value = source
  failurePhase.value = phase
  expandedPanels.value = []
  mode.value = 'failure'
  visible.value = true
}

function notifyStartupCheckFailure() {
  ElNotification({
    title: '暂时无法检查更新',
    message: '不影响正常使用，可到系统设置中重新检查。',
    type: 'warning',
    duration: 5000
  })
}

function openDownloadFailure(error) {
  technicalDetail.value = String(error?.message || error || '')
  failureSource.value = 'download'
  failurePhase.value = phaseOrder.includes(progress.value.phase) ? progress.value.phase : 'CONNECTING'
  expandedPanels.value = []
  mode.value = 'failure'
  visible.value = true
}

function beforeClose(done) {
  if (mode.value === 'download') return
  invalidatePendingChecks()
  invalidatePendingPreferences()
  done()
}

function dismissFailure() {
  closeDialog()
}

function closeDialog() {
  invalidatePendingChecks()
  invalidatePendingPreferences()
  visible.value = false
}

async function postpone() {
  invalidatePendingChecks()
  invalidatePendingPreferences()
  if (!skipThisVersion.value) {
    closeDialog()
    return
  }
  const { operationId, version } = beginPreferenceOperation()
  postponingOperationId.value = operationId
  try {
    await invoke('skip_app_update', { version })
    if (!isCurrentPreferenceOperation(operationId, version)) return
    closeDialog()
  } catch (error) {
    if (isCurrentPreferenceOperation(operationId, version)) {
      openFailure(error, 'preference', 'PREFERENCE')
    }
  } finally {
    if (postponingOperationId.value === operationId) postponingOperationId.value = null
  }
}

async function downloadUpdate() {
  invalidatePendingChecks()
  invalidatePendingPreferences()
  resetProgress()
  mode.value = 'download'
  try {
    await invoke('download_app_update', { expectedVersion: targetVersion.value })
  } catch (error) {
    openDownloadFailure(error)
  }
}

async function checkForUpdate(trigger) {
  return invoke('check_app_update', { trigger })
}

async function retryFailure() {
  if (failureSource.value === 'preference') {
    invalidatePendingChecks()
    invalidatePendingPreferences()
    const { operationId, version } = beginPreferenceOperation()
    retryOperation.value = { kind: 'preference', operationId }
    try {
      await invoke('skip_app_update', { version })
      if (!isCurrentPreferenceOperation(operationId, version)) return
      closeDialog()
    } catch (error) {
      if (isCurrentPreferenceOperation(operationId, version)) {
        openFailure(error, 'preference', 'PREFERENCE')
      }
    } finally {
      if (retryOperation.value?.kind === 'preference' && retryOperation.value.operationId === operationId) {
        retryOperation.value = null
      }
    }
    return
  }

  const requestId = beginCheckRequest()
  retryOperation.value = { kind: 'check', operationId: requestId }
  try {
    const result = await checkForUpdate('manual')
    if (!isCurrentCheckRequest(requestId)) return
    if (result?.targetVersion) openOffer(result)
    else closeDialog()
  } catch (error) {
    if (isCurrentCheckRequest(requestId)) openFailure(error, 'check', 'CONNECTING')
  } finally {
    if (retryOperation.value?.kind === 'check' && retryOperation.value.operationId === requestId) {
      retryOperation.value = null
    }
  }
}

function handleManualResult(event) {
  const result = event.detail
  if (!result || typeof result !== 'object') return
  if (typeof result.targetVersion !== 'string' || !result.targetVersion.trim()) return
  if (mode.value === 'download') return
  invalidatePendingChecks()
  invalidatePendingPreferences()
  openOffer(result)
}

function handleManualError(event) {
  if (mode.value === 'download') return
  invalidatePendingChecks()
  invalidatePendingPreferences()
  openFailure(event.detail, 'check', 'CONNECTING')
}

function handleProgress(event) {
  if (mode.value !== 'download' || !event.detail || typeof event.detail !== 'object') return
  progress.value = { ...progress.value, ...event.detail }
}

async function checkOnStartup() {
  if (!isTauriEnvironment()) return
  const requestId = beginCheckRequest()
  try {
    const result = await checkForUpdate('startup')
    if (!isCurrentCheckRequest(requestId)) return
    if (shouldShowUpdate(result)) openOffer(result)
  } catch {
    if (!isCurrentCheckRequest(requestId)) return
    if (getUpdateCheckFailurePresentation('startup') === 'notification') {
      notifyStartupCheckFailure()
    }
  }
}

onMounted(() => {
  window.addEventListener('wm-show-app-update', handleManualResult)
  window.addEventListener('wm-show-app-update-error', handleManualError)
  window.addEventListener('wm-update-progress', handleProgress)
  void checkOnStartup()
})

onBeforeUnmount(() => {
  invalidatePendingChecks()
  invalidatePendingPreferences()
  window.removeEventListener('wm-show-app-update', handleManualResult)
  window.removeEventListener('wm-show-app-update-error', handleManualError)
  window.removeEventListener('wm-update-progress', handleProgress)
})
</script>

<style>
.wm-update-overlay {
  background: rgba(4, 8, 14, 0.68);
  backdrop-filter: blur(2px);
}

.wm-visually-hidden {
  position: absolute !important;
  width: 1px !important;
  height: 1px !important;
  margin: -1px !important;
  padding: 0 !important;
  overflow: hidden !important;
  clip: rect(0 0 0 0) !important;
  white-space: nowrap !important;
  border: 0 !important;
}

.wm-update-dialog {
  --wm-update-border: #303844;
  --wm-update-muted: #a9b3c1;
  max-width: calc(100vw - 40px);
  overflow: hidden;
  border: 1px solid #3a4657;
  border-radius: 8px;
  background: #171b22;
  color: #eef2f7;
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.48);
  font-family: "Microsoft YaHei", "Microsoft YaHei UI", sans-serif;
}

.wm-update-dialog .el-dialog__header {
  margin: 0;
  padding: 0;
}

.wm-update-dialog .el-dialog__headerbtn {
  top: 14px;
  right: 14px;
  width: 44px;
  height: 44px;
  border-radius: 5px;
}

.wm-update-dialog .el-dialog__headerbtn:focus-visible {
  outline: 2px solid #62cae3;
  outline-offset: -2px;
}

.wm-update-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #aeb8c6;
  font-size: 20px;
}

.wm-update-dialog .el-dialog__body {
  padding: 0;
  color: inherit;
}

.wm-update-header {
  display: flex;
  gap: 14px;
  padding: 24px 64px 18px 26px;
}

.wm-update-icon {
  display: grid;
  place-items: center;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  border-radius: 6px;
  background: #123b48;
  color: #55c8e5;
  font-size: 22px;
}

.wm-update-icon--error {
  background: #402d16;
  color: #f1b35b;
}

.wm-update-heading {
  min-width: 0;
}

.wm-update-heading small {
  display: block;
  margin-bottom: 4px;
  color: #7f8da0;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.wm-update-heading h2 {
  margin: 0;
  color: #f8fafc;
  font-size: 21px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}

.wm-update-heading p {
  margin: 7px 0 0;
  color: var(--wm-update-muted);
  font-size: 13px;
  line-height: 1.65;
}

.wm-update-main {
  padding: 0 26px 24px;
}

.wm-update-versions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px minmax(0, 1fr);
  align-items: center;
  margin: 8px 0 20px;
  padding: 16px 0;
  border-top: 1px solid var(--wm-update-border);
  border-bottom: 1px solid var(--wm-update-border);
}

.wm-update-versions div {
  min-width: 0;
}

.wm-update-versions span {
  display: block;
  margin-bottom: 5px;
  color: #7f8da0;
  font-size: 11px;
}

.wm-update-versions strong {
  display: block;
  color: #eef2f7;
  font-size: 18px;
  overflow-wrap: anywhere;
}

.wm-update-versions .wm-update-target strong {
  color: #66d4ad;
}

.wm-update-arrow {
  justify-self: center;
  color: #677588;
  font-size: 18px;
}

.wm-update-section-title {
  margin: 0 0 8px;
  color: #dce3ec;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
}

.wm-update-notes {
  max-height: 132px;
  margin: 0;
  padding-left: 18px;
  overflow-y: auto;
  color: #aeb8c6;
  font-size: 13px;
  line-height: 1.85;
  overflow-wrap: anywhere;
}

.wm-update-skip {
  margin-top: 18px;
  padding: 12px 14px;
  border: 1px solid #343d49;
  border-radius: 5px;
  background: #1d222a;
}

.wm-update-skip .el-checkbox {
  min-height: 24px;
  white-space: normal;
}

.wm-update-skip .el-checkbox__label {
  color: #d7dee7;
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.wm-update-skip .el-checkbox__input.is-checked + .el-checkbox__label {
  color: #d7dee7;
}

.wm-update-skip small {
  display: block;
  margin: 3px 0 0 24px;
  color: #7f8da0;
  font-size: 11px;
  line-height: 1.55;
}

.wm-update-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  min-height: 74px;
  padding: 14px 26px;
  border-top: 1px solid var(--wm-update-border);
  background: #14181e;
}

.wm-update-footnote {
  color: #758294;
  font-size: 11px;
  line-height: 1.5;
}

.wm-update-actions {
  display: flex;
  gap: 9px;
}

.wm-update-dialog .el-button {
  min-width: 104px;
  min-height: 44px;
  height: auto;
  padding: 10px 16px;
  border-color: #414b59;
  border-radius: 5px;
  background: #202630;
  color: #dce3eb;
  font-weight: 700;
  white-space: normal;
}

.wm-update-dialog .el-button:not(.is-disabled):hover {
  border-color: #566274;
  background: #272f3a;
  color: #f2f6fb;
  transform: none;
}

.wm-update-dialog .el-button--primary {
  border-color: #2ca8c8;
  background: #2ca8c8;
  color: #06151a;
}

.wm-update-dialog .el-button--primary:not(.is-disabled):hover {
  border-color: #45bad6;
  background: #45bad6;
  color: #06151a;
}

.wm-update-dialog .el-button:focus-visible,
.wm-update-dialog .el-checkbox__input.is-focus .el-checkbox__inner,
.wm-update-dialog .el-collapse-item__header:focus-visible {
  outline: 2px solid #62cae3;
  outline-offset: 2px;
}

.wm-update-progress-area {
  min-height: 226px;
  padding-top: 4px;
}

.wm-update-progress-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  min-height: 44px;
  margin: 8px 0 12px;
}

.wm-update-progress-top span {
  color: #8f9bac;
  font-size: 12px;
}

.wm-update-progress-top strong {
  min-width: 96px;
  color: #f4f7fa;
  font-size: 34px;
  line-height: 1;
  text-align: right;
}

.wm-update-progress-top .wm-update-calculating {
  font-size: 14px;
  line-height: 1.5;
}

.wm-update-dialog .el-progress-bar__outer {
  border-radius: 3px;
  background: #303844;
}

.wm-update-dialog .el-progress-bar__inner {
  border-radius: 3px;
}

.wm-update-metrics {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-height: 36px;
  margin-top: 10px;
  color: #8895a7;
  font-size: 12px;
  line-height: 1.5;
}

.wm-update-metrics span:last-child {
  text-align: right;
}

.wm-update-stages {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.wm-update-stages li {
  position: relative;
  min-width: 0;
  padding: 22px 3px 0;
  color: #8895a7;
  font-size: 11px;
  line-height: 1.4;
  text-align: center;
}

.wm-update-stages li::before {
  content: "";
  position: absolute;
  top: 6px;
  right: 0;
  left: 0;
  height: 2px;
  background: #323c4a;
}

.wm-update-stages li:first-child::before {
  left: 50%;
}

.wm-update-stages li:last-child::before {
  right: 50%;
}

.wm-update-stages li > span {
  position: absolute;
  z-index: 1;
  top: 1px;
  left: calc(50% - 6px);
  width: 12px;
  height: 12px;
  border: 2px solid #3b4655;
  border-radius: 50%;
  background: #171b22;
}

.wm-update-stages li.is-done {
  color: #8fae9f;
}

.wm-update-stages li.is-done::before,
.wm-update-stages li.is-done > span {
  border-color: #50c799;
  background: #50c799;
}

.wm-update-stages li.is-active {
  color: #dce5ef;
}

.wm-update-stages li.is-active > span {
  border-color: #45bddb;
  box-shadow: 0 0 0 4px rgba(69, 189, 219, 0.14);
}

.wm-update-error {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 8px 0 18px;
  padding: 15px 16px;
  border-left: 3px solid #e4a34d;
  border-radius: 3px;
  background: #241f19;
}

.wm-update-error strong {
  color: #f1c783;
  font-size: 13px;
}

.wm-update-error span {
  color: #b7ad9d;
  font-size: 12px;
  line-height: 1.65;
}

.wm-update-trace {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  min-height: 42px;
  padding: 11px 0;
  border-top: 1px solid var(--wm-update-border);
  color: #7f8da0;
  font-size: 12px;
}

.wm-update-trace strong {
  color: #aeb8c5;
  font-weight: 600;
}

.wm-update-trace strong.is-success {
  color: #66d4ad;
}

.wm-update-trace strong.is-error {
  color: #f1c783;
}

.wm-update-tech {
  border-top: 1px solid var(--wm-update-border);
  border-bottom: 0;
}

.wm-update-tech .el-collapse-item__header,
.wm-update-tech .el-collapse-item__wrap {
  border: 0;
  background: transparent;
  color: #6ebad0;
  font-size: 12px;
}

.wm-update-tech .el-collapse-item__header {
  min-height: 44px;
}

.wm-update-tech .el-collapse-item__content {
  padding-bottom: 0;
}

.wm-update-tech pre {
  max-height: 104px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border-radius: 4px;
  background: #10141a;
  color: #aeb8c5;
  font-family: Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 767px) {
  .wm-update-dialog {
    max-width: calc(100vw - 24px);
    margin: 12px auto;
  }

  .wm-update-header {
    padding: 20px 54px 16px 18px;
  }

  .wm-update-main {
    padding-right: 18px;
    padding-left: 18px;
  }

  .wm-update-footer {
    align-items: stretch;
    flex-direction: column;
    padding: 14px 18px 18px;
  }

  .wm-update-actions {
    width: 100%;
  }

  .wm-update-actions .el-button {
    flex: 1 1 0;
    min-width: 0;
    margin: 0;
  }
}

@media (max-width: 420px) {
  .wm-update-icon {
    flex-basis: 38px;
    width: 38px;
    height: 38px;
  }

  .wm-update-heading h2 {
    font-size: 19px;
  }

  .wm-update-versions strong {
    font-size: 16px;
  }

  .wm-update-actions {
    flex-direction: column-reverse;
  }

  .wm-update-actions .el-button {
    width: 100%;
  }

  .wm-update-metrics {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .wm-update-metrics span:last-child {
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wm-update-dialog *,
  .wm-update-dialog *::before,
  .wm-update-dialog *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
