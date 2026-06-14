<template>
  <div style="display:flex;gap:20px;flex-wrap:wrap">
    <!-- 水价 & 阈值配置 -->
    <el-card header="⚙️ 业务参数配置" style="flex:1;min-width:350px">
      <el-form label-width="120px">
        <el-form-item label="水价（元/吨）">
          <el-input-number v-model="waterPrice" :precision="2" :min="0" :step="0.1" style="width:200px" />
          <span style="margin-left:8px;color:#909399">当前后端配置：¥{{ waterPrice }} / 吨</span>
        </el-form-item>
        <el-form-item label="异常阈值（吨）">
          <el-input-number v-model="threshold" :min="10" :step="10" style="width:200px" />
          <span style="margin-left:8px;color:#909399">月用水量超过此值标记为异常</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveConfig" :loading="savingConfig">💾 保存配置</el-button>
          <span style="margin-left:12px;color:#67C23A;font-size:12px">
            ✅ 保存后立即生效，无需重启
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据备份与恢复 -->
    <el-card header="💿 数据备份与恢复" style="flex:1;min-width:350px">
      <el-alert
        title="备份提示"
        type="info"
        description="点击下方按钮下载当前数据库文件，定期备份以防数据丢失。恢复时需停止应用后替换数据库文件。"
        show-icon
        :closable="false"
        style="margin-bottom:16px"
      />
      <div style="display:flex;flex-direction:column;gap:12px">
        <el-button type="primary" @click="downloadBackup" :loading="backingUp" size="large">
          📥 一键下载备份
        </el-button>
        <el-divider />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="数据库文件">data/water_meter.db</el-descriptions-item>
          <el-descriptions-item label="备份建议">每月备份一次，保留最近12个月</el-descriptions-item>
          <el-descriptions-item label="恢复方法">停止应用 → 用备份文件替换原文件 → 重启应用</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 系统信息 -->
    <el-card header="ℹ️ 系统信息" style="width:100%">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="后端框架">Spring Boot 4.0.6</el-descriptions-item>
        <el-descriptions-item label="前端框架">Vue 3 + Vite + Element Plus</el-descriptions-item>
        <el-descriptions-item label="数据库">SQLite 3.46.1</el-descriptions-item>
        <el-descriptions-item label="JDK 版本">25</el-descriptions-item>
        <el-descriptions-item label="API 文档">/swagger-ui.html</el-descriptions-item>
        <el-descriptions-item label="开发端口">后端 8080 / 前端 3000</el-descriptions-item>
      </el-descriptions>
    </el-card>
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

async function saveConfig() {
  savingConfig.value = true
  try {
    await readingApi.updateConfig({
      waterPrice: waterPrice.value,
      abnormalThreshold: threshold.value
    })
    ElMessage.success('配置已保存')
  } catch { ElMessage.error('保存失败') }
  finally { savingConfig.value = false }
}

onMounted(async () => {
  try {
    const config = await readingApi.getConfig()
    waterPrice.value = config.waterPrice || 1.8
    threshold.value = config.abnormalThreshold || 100
  } catch { /* 使用默认值 */ }
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
  } catch { ElMessage.error('备份下载失败') }
  finally { backingUp.value = false }
}
</script>
