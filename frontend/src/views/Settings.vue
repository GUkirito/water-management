<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>系统设置</h1>
        <p>配置水价、异常阈值，并管理本地 SQLite 数据备份。</p>
      </div>
    </section>

    <section class="wm-card-grid">
      <div class="wm-panel">
        <div class="wm-panel-body">
          <div class="wm-section-title">业务参数</div>
          <el-form label-width="130px">
            <el-form-item label="水价（元/吨）">
              <el-input-number v-model="waterPrice" :precision="2" :min="0.01" :step="0.1" style="width:220px" />
            </el-form-item>
            <el-form-item label="异常阈值（吨）">
              <el-input-number v-model="threshold" :min="0" :step="10" style="width:220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig" :loading="savingConfig">保存配置</el-button>
              <span class="wm-muted" style="margin-left:12px;font-size:12px">保存后立即生效</span>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="wm-panel">
        <div class="wm-panel-body">
          <div class="wm-section-title">数据备份与恢复</div>
          <el-alert
            title="建议定期备份"
            type="info"
            description="备份会下载当前数据库文件；恢复会先自动创建回滚备份，再替换当前数据库。恢复后建议重启应用。"
            show-icon
            :closable="false"
            style="margin-bottom:16px"
          />
          <div class="wm-table-actions" style="margin-bottom:16px">
            <el-button type="primary" @click="downloadBackup" :loading="backingUp">下载备份</el-button>
            <el-upload :auto-upload="false" :show-file-list="false" accept=".db" :on-change="restoreBackup">
              <el-button type="warning" :loading="restoring">恢复备份</el-button>
            </el-upload>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="数据库文件路径">{{ dbFilePath }}</el-descriptions-item>
            <el-descriptions-item label="恢复说明">上传 .db 文件后，系统会保留一份恢复前回滚备份。</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div class="wm-section-title">系统信息</div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="后端框架">Spring Boot 4.0.6</el-descriptions-item>
          <el-descriptions-item label="前端框架">Vue 3 + Vite + Element Plus</el-descriptions-item>
          <el-descriptions-item label="数据库">SQLite 3.46.1</el-descriptions-item>
          <el-descriptions-item label="JDK 版本">25</el-descriptions-item>
          <el-descriptions-item label="API 文档">/swagger-ui.html</el-descriptions-item>
          <el-descriptions-item label="开发端口">后端 8080 / 前端 3000</el-descriptions-item>
        </el-descriptions>
        <div v-if="isTauriEnv" class="wm-table-actions" style="margin-top:16px">
          <el-button type="primary" @click="checkAppUpdate" :loading="checkingUpdate">检查更新</el-button>
        </div>
      </div>
    </section>

    <el-alert v-if="lanAccessEnabled" title="局域网访问已开启" type="warning"
      description="当前后端监听非回环地址。备份恢复、批量删除、解锁和调账仍只允许在本机执行。"
      show-icon :closable="false" style="margin-bottom:16px" />

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div class="wm-section-title">账务健康检查</div>
        <div class="wm-table-actions" style="margin-bottom:12px">
          <el-button type="primary" @click="runHealthCheck" :loading="checkingHealth">开始检查</el-button>
          <span class="wm-muted" style="font-size:12px">检查重复账单、超收、负数用水量，以及找不到对应住户或账单的记录。</span>
        </div>
        <el-empty v-if="healthChecked && !healthIssues.length" description="暂无账务异常" :image-size="60" class="wm-empty" />
        <el-table v-if="healthIssues.length" :data="healthIssues" border stripe size="small" max-height="320">
          <el-table-column label="严重程度" width="90">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'ERROR' ? 'danger' : 'warning'" size="small">{{ healthSeverityLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="问题类型" min-width="170">
            <template #default="{ row }">{{ healthTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="waterMeterId" label="表号" width="130">
            <template #default="{ row }">{{ row.waterMeterId || '-' }}</template>
          </el-table-column>
          <el-table-column label="关联对象" width="140">
            <template #default="{ row }">{{ healthReferenceLabel(row.refType, row.refId) }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ healthMessage(row) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div class="wm-section-title">月结锁定与调账</div>
        <el-form inline label-width="80px" class="wm-compact-form">
          <el-form-item label="锁定月份">
            <el-date-picker v-model="monthLockValue" type="month" value-format="YYYY-MM" placeholder="选择月份" />
          </el-form-item>
          <el-form-item label="操作人">
            <el-input v-model="monthLockOperator" placeholder="管理员" style="width:120px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="monthLockNote" placeholder="月结说明" style="width:220px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="lockingMonth" @click="lockSelectedMonth">锁定月份</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="monthLocks" border stripe size="small" max-height="180" style="margin-bottom:16px">
          <el-table-column label="年月" width="100">
            <template #default="{ row }">{{ row.billYear }}-{{ String(row.billMonth).padStart(2, '0') }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="120" />
          <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="lockedAt" label="锁定时间" width="180" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="unlockMonth(row)">解锁</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form inline label-width="80px" class="wm-compact-form">
          <el-form-item label="调账类型">
            <el-select v-model="adjustmentForm.targetType" style="width:140px">
              <el-option label="水费账单" value="WATER_BILL" />
              <el-option label="材料费" value="MATERIAL_RECORD" />
            </el-select>
          </el-form-item>
          <el-form-item label="记录ID">
            <el-input-number v-model="adjustmentForm.targetId" :min="1" :precision="0" style="width:120px" />
          </el-form-item>
          <el-form-item label="调整后">
            <el-input-number v-model="adjustmentForm.afterAmount" :min="0" :precision="2" style="width:140px" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="adjustmentForm.reason" placeholder="必须填写" style="width:220px" />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="adjusting" @click="submitAdjustment">提交调账</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="adjustments" border stripe size="small" max-height="240">
          <el-table-column prop="targetType" label="类型" width="130" />
          <el-table-column prop="targetId" label="记录ID" width="90" />
          <el-table-column label="原金额" width="110" align="right">
            <template #default="{ row }">¥{{ row.beforeAmount?.toFixed?.(2) || row.beforeAmount }}</template>
          </el-table-column>
          <el-table-column label="新金额" width="110" align="right">
            <template #default="{ row }">¥{{ row.afterAmount?.toFixed?.(2) || row.afterAmount }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="operator" label="操作人" width="120" />
          <el-table-column prop="createdAt" label="时间" width="180" />
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { accountingApi, readingApi, settingsApi } from '@/api'
import {
  healthMessage,
  healthReferenceLabel,
  healthSeverityLabel,
  healthTypeLabel
} from '@/utils/accountingHealthDisplay'
import { formatLocalMonth, formatLocalTimestamp } from '@/utils/localDate'

const waterPrice = ref(1.8)
const threshold = ref(100)
const savingConfig = ref(false)
const backingUp = ref(false)
const restoring = ref(false)
const checkingUpdate = ref(false)
const checkingHealth = ref(false)
const healthChecked = ref(false)
const healthIssues = ref([])
const dbFilePath = ref('加载中...')
const lanAccessEnabled = ref(false)
const monthLockValue = ref(formatLocalMonth())
const monthLockOperator = ref('管理员')
const monthLockNote = ref('')
const monthLocks = ref([])
const lockingMonth = ref(false)
const adjustments = ref([])
const adjusting = ref(false)
const adjustmentForm = ref({
  targetType: 'WATER_BILL',
  targetId: 1,
  afterAmount: 0,
  reason: '',
  operator: '管理员'
})
const isTauriEnv = typeof window !== 'undefined' && !!window.__TAURI__

async function saveConfig() {
  savingConfig.value = true
  try {
    await readingApi.updateConfig({
      waterPrice: waterPrice.value,
      abnormalThreshold: threshold.value
    })
    ElMessage.success('配置已保存')
  } catch (error) {
    console.warn('保存配置失败', error)
  } finally {
    savingConfig.value = false
  }
}

async function downloadBackup() {
  backingUp.value = true
  try {
    const blob = await settingsApi.downloadBackup()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'backup_' + formatLocalTimestamp() + '_water_meter.db'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('备份下载成功')
  } catch (error) {
    console.warn('备份下载失败', error)
  } finally {
    backingUp.value = false
  }
}

async function restoreBackup(uploadFile) {
  if (!uploadFile?.raw) return
  try {
    await ElMessageBox.confirm(
      '恢复会替换当前数据库。系统会先创建回滚备份，恢复后建议重启应用。是否继续？',
      '确认恢复备份',
      { type: 'warning', confirmButtonText: '继续恢复', cancelButtonText: '取消' }
    )
  } catch (error) {
    console.warn('取消恢复备份:', error)
    return
  }

  restoring.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.raw)
    const staged = await settingsApi.restoreBackup(formData, isTauriEnv)
    if (!isTauriEnv) {
      ElMessage.warning(staged.message)
      return
    }
    const invoke = window.__TAURI__?.core?.invoke || window.__TAURI__?.invoke
    const result = await invoke('restore_database', { token: staged.token })
    const targetUrl = `http://127.0.0.1:${result.port}/settings`
    if (result.status === 'COMPLETED') {
      ElMessage.success(result.message)
    } else {
      ElMessage.warning(result.message)
    }
    window.location.replace(targetUrl)
  } catch (error) {
    console.warn('恢复备份失败', error)
    ElMessage.error('恢复备份失败：' + (error?.message || error))
  } finally {
    restoring.value = false
  }
}

async function checkAppUpdate() {
  const invoke = window.__TAURI__?.core?.invoke || window.__TAURI__?.invoke
  if (!invoke) {
    ElMessage.warning('当前环境不支持检查更新')
    return
  }
  checkingUpdate.value = true
  try {
    const result = await invoke('check_app_update', { trigger: 'manual' })
    if (result?.status === 'UP_TO_DATE') {
      ElMessage.success('当前已是最新版本')
    } else {
      window.dispatchEvent(new CustomEvent('wm-show-app-update', { detail: result }))
    }
  } catch (error) {
    console.warn('检查更新失败', error)
    window.dispatchEvent(new CustomEvent('wm-show-app-update-error', { detail: error }))
  } finally {
    checkingUpdate.value = false
  }
}

async function runHealthCheck() {
  checkingHealth.value = true
  try {
    healthIssues.value = await accountingApi.healthCheck() || []
    healthChecked.value = true
    ElMessage.success(healthIssues.value.length ? `发现 ${healthIssues.value.length} 条账务问题` : '账务健康检查通过')
  } catch (error) {
    console.warn('账务健康检查失败', error)
  } finally {
    checkingHealth.value = false
  }
}

async function loadAccountingControls() {
  monthLocks.value = await accountingApi.listMonthLocks() || []
  adjustments.value = await accountingApi.listAdjustments() || []
}

async function lockSelectedMonth() {
  if (!monthLockValue.value) {
    ElMessage.warning('请选择锁定月份')
    return
  }
  const [billYear, billMonth] = monthLockValue.value.split('-').map(Number)
  lockingMonth.value = true
  try {
    await accountingApi.lockMonth({
      billYear,
      billMonth,
      operator: monthLockOperator.value,
      note: monthLockNote.value
    })
    ElMessage.success('月结锁定成功')
    await loadAccountingControls()
  } catch (error) {
    console.warn('月结锁定失败', error)
  } finally {
    lockingMonth.value = false
  }
}

async function unlockMonth(row) {
  let reason
  try {
    const result = await ElMessageBox.prompt(
      `解除 ${row.billYear}-${String(row.billMonth).padStart(2, '0')} 月结锁定后，可重新修改该月数据。请输入解锁原因。`,
      '确认解锁',
      { type: 'warning', confirmButtonText: '解除锁定', cancelButtonText: '取消', inputValidator: value => !!value?.trim() || '必须填写解锁原因' }
    )
    reason = result.value.trim()
  } catch (error) {
    console.warn('取消解除月结锁定:', error)
    return
  }
  try {
    await accountingApi.unlockMonth({ billYear: row.billYear, billMonth: row.billMonth, operator: monthLockOperator.value, reason })
    ElMessage.success('已解除月结锁定')
    await loadAccountingControls()
  } catch (error) {
    console.warn('解除月结锁定失败', error)
  }
}

async function submitAdjustment() {
  if (!adjustmentForm.value.reason?.trim()) {
    ElMessage.warning('请填写调账原因')
    return
  }
  adjusting.value = true
  try {
    const payload = {
      afterAmount: adjustmentForm.value.afterAmount,
      reason: adjustmentForm.value.reason,
      operator: adjustmentForm.value.operator
    }
    if (adjustmentForm.value.targetType === 'WATER_BILL') {
      await accountingApi.adjustWaterBill(adjustmentForm.value.targetId, payload)
    } else {
      await accountingApi.adjustMaterialRecord(adjustmentForm.value.targetId, payload)
    }
    ElMessage.success('调账成功')
    adjustmentForm.value.reason = ''
    await loadAccountingControls()
  } catch (error) {
    console.warn('调账失败', error)
  } finally {
    adjusting.value = false
  }
}

onMounted(async () => {
  try {
    const config = await readingApi.getConfig()
    waterPrice.value = config.waterPrice || 1.8
    threshold.value = config.abnormalThreshold || 100
  } catch (error) {
    console.warn('加载系统配置失败', error)
  }
  try {
    const info = await settingsApi.getInfo()
    dbFilePath.value = info.dbFilePath || '未知'
    lanAccessEnabled.value = info.lanAccessEnabled === 'true'
  } catch (error) {
    console.warn('加载系统信息失败', error)
    dbFilePath.value = '未知'
  }
  try {
    await loadAccountingControls()
  } catch (error) {
    console.warn('加载账务控制数据失败', error)
  }
})
</script>

<style scoped>
.wm-section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}
.wm-compact-form {
  margin-bottom: 12px;
}
</style>
