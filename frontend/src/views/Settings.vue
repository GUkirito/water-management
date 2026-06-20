<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>系统设置</h1>
        <p>配置水价、异常阈值和备份信息。</p>
      </div>
    </section>

    <section class="wm-card-grid">
      <div class="wm-panel">
        <div class="wm-panel-body">
          <div style="font-size:16px;font-weight:600;margin-bottom:16px">业务参数</div>
          <el-form label-width="120px">
            <el-form-item label="水价（元/吨）">
              <el-input-number v-model="waterPrice" :precision="2" :min="0" :step="0.1" style="width:220px" />
            </el-form-item>
            <el-form-item label="异常阈值（吨）">
              <el-input-number v-model="threshold" :min="10" :step="10" style="width:220px" />
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
          <div style="font-size:16px;font-weight:600;margin-bottom:16px">数据备份</div>
          <el-alert
            title="建议定期备份"
            type="info"
            description="点击下方按钮下载当前数据库文件。恢复时停止应用后替换数据库文件即可。"
            show-icon
            :closable="false"
            style="margin-bottom:16px"
          />
          <div class="wm-table-actions" style="margin-bottom:16px">
            <el-button type="primary" @click="downloadBackup" :loading="backingUp">一键下载备份</el-button>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="数据库文件路径">{{ dbFilePath }}</el-descriptions-item>
            <el-descriptions-item label="建议">每月备份一次，保留最近 2 个月</el-descriptions-item>
            <el-descriptions-item label="恢复方法">停止应用 -> 替换数据库文件 -> 重启应用</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div style="font-size:16px;font-weight:600;margin-bottom:12px">系统信息</div>
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
import { ElMessage } from 'element-plus'
import { readingApi, settingsApi } from '@/api'

const waterPrice = ref(1.8)
const threshold = ref(100)
const savingConfig = ref(false)
const backingUp = ref(false)
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
</script>
