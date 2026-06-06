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
          <span style="margin-left:12px;color:#909399;font-size:12px">
            ⚠ 修改后需在后端 application.yml 中同步更新永久生效
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据备份与恢复 -->
    <el-card header="💿 数据备份与恢复" style="flex:1;min-width:350px">
      <el-alert
        title="备份提示"
        type="info"
        description="SQLite 数据库文件位于项目 data/water_meter.db，备份即复制该文件。恢复即用备份文件替换当前文件。"
        show-icon
        :closable="false"
        style="margin-bottom:16px"
      />
      <div style="display:flex;flex-direction:column;gap:12px">
        <div>
          <p style="color:#909399;font-size:13px">📋 手动备份：直接复制 data/water_meter.db 文件到安全位置</p>
          <p style="color:#909399;font-size:13px">🔄 手动恢复：将备份的 .db 文件覆盖到 data/ 目录，重启应用</p>
        </div>
        <el-divider />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="数据库文件">data/water_meter.db</el-descriptions-item>
          <el-descriptions-item label="备份建议">每月自动备份一次，保留最近12个月</el-descriptions-item>
          <el-descriptions-item label="恢复注意事项">恢复后会覆盖当前数据，请先备份当前数据库</el-descriptions-item>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const waterPrice = ref(1.8)
const threshold = ref(100)
const savingConfig = ref(false)

async function saveConfig() {
  savingConfig.value = true
  try {
    // 模拟保存（前端当前仅做内存存储）
    await new Promise(resolve => setTimeout(resolve, 500))
    ElMessage.success(`配置已更新：水价 ¥${waterPrice.value} / 吨，异常阈值 ${threshold.value} 吨`)
  } finally { savingConfig.value = false }
}
</script>
