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
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { readingApi, settingsApi } from '@/api'

const waterPrice = ref(1.8)
const threshold = ref(100)
const savingConfig = ref(false)
const backingUp = ref(false)
const restoring = ref(false)
const dbFilePath = ref('加载中...')

async function saveConfig() {
  savingConfig.value = true
  try {
    await readingApi.updateConfig({
      waterPrice: waterPrice.value,
      abnormalThreshold: threshold.value
    })
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
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
    a.download = 'backup_' + new Date().toISOString().slice(0, 10) + '_water_meter.db'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('备份下载成功')
  } catch {
    ElMessage.error('备份下载失败')
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
  } catch {
    return
  }

  restoring.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.raw)
    const result = await settingsApi.restoreBackup(formData)
    ElMessage.success(`恢复成功，请重启应用。回滚备份：${result.rollbackFilePath || '-'}`)
    dbFilePath.value = result.dbFilePath || dbFilePath.value
  } catch {
    ElMessage.error('恢复失败')
  } finally {
    restoring.value = false
  }
}

onMounted(async () => {
  try {
    const config = await readingApi.getConfig()
    waterPrice.value = config.waterPrice || 1.8
    threshold.value = config.abnormalThreshold || 100
  } catch {}
  try {
    const info = await settingsApi.getInfo()
    dbFilePath.value = info.dbFilePath || '未知'
  } catch {
    dbFilePath.value = '未知'
  }
})
</script>

<style scoped>
.wm-section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}
</style>
