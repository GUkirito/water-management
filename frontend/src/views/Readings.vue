<template>
  <div>
    <!-- 操作栏 -->
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap;background:#fff;padding:16px;border-radius:8px">
      <span style="font-weight:500">抄表日期：</span>
      <el-date-picker v-model="readingDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD"
        @change="loadTable" style="width:180px" />
      <span style="font-weight:500;margin-left:8px">村组：</span>
      <el-select v-model="selectedVillage" placeholder="选择村组" @change="loadTable" style="width:180px">
        <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
      </el-select>
      <el-button type="success" @click="exportTemplate">📥 导出空白模板</el-button>
      <el-upload :before-upload="importTemplate" :show-file-list="false" accept=".xlsx">
        <el-button type="warning">📤 导入填好的模板</el-button>
      </el-upload>
      <el-button type="primary" @click="batchSave" :loading="saving">💾 批量保存</el-button>
    </div>

    <!-- 批量录入表格 -->
    <div style="background:#fff;padding:16px;border-radius:8px">
      <el-table :data="tableData" border stripe max-height="calc(100vh - 340px)" style="width:100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="householdName" label="户主姓名" width="100" />
        <el-table-column prop="waterMeterId" label="表号" width="120" />
        <el-table-column prop="phone" label="电话号码" width="130" />
        <el-table-column prop="previousReading" label="上月表底" width="100">
          <template #default="{ row }">{{ row.previousReading ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="本月表底" width="140">
          <template #default="{ row }">
            <el-input v-model="row.currentReading" placeholder="输入表底" size="small"
              @change="calcRow(row)" :class="{ 'is-error': row.isAbnormal }" />
          </template>
        </el-table-column>
        <el-table-column label="用水量(吨)" width="110">
          <template #default="{ row }">{{ row.usageAmount != null ? row.usageAmount : '-' }}</template>
        </el-table-column>
        <el-table-column label="计费用水量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.chargeableUsage" :precision="2" :min="0" size="small"
              controls-position="right" style="width:110px" @change="calcCharge(row)" />
          </template>
        </el-table-column>
        <el-table-column label="水价(元)" width="90">
          <template #default>{{ waterPrice }}</template>
        </el-table-column>
        <el-table-column label="水费(元)" width="110">
          <template #default="{ row }">{{ row.waterCharge != null ? row.waterCharge : '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.note" placeholder="备注" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
            <el-tag v-else-if="row.currentReading" type="success" size="small">正常</el-tag>
            <el-tag v-else type="info" size="small">未录入</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="abnormalReason" label="异常原因" min-width="160" />
      </el-table>
      <div v-if="!selectedVillage" style="text-align:center;padding:60px 0;color:#909399">
        <el-empty description="请选择村组后加载数据" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, readingApi } from '@/api'

const readingDate = ref(new Date().toISOString().slice(0, 10))
const selectedVillage = ref('')
const villageList = ref([])
const tableData = ref([])
const waterPrice = ref(1.8)
const saving = ref(false)

// 加载系统配置（水价）
onMounted(async () => {
  try {
    const config = await readingApi.getConfig()
    waterPrice.value = config.waterPrice || 1.8
  } catch { /* 使用默认值 */ }

  // 加载村组列表
  try {
    const result = await householdApi.list({ page: 0, size: 1000 })
    const list = result?.content || []
    villageList.value = [...new Set(list.map(h => h.villageName))].sort()
  } catch { /* ignore */ }
})

// 加载表格数据
async function loadTable() {
  if (!selectedVillage.value) {
    tableData.value = []
    return
  }

  // 1. 获取该村所有户
  let households = []
  try {
    const result = await householdApi.list({
      page: 0, size: 1000,
      villageNames: selectedVillage.value
    })
    households = result?.content || []
  } catch {
    households = []
  }

  // 2. 获取该日期该村的抄表数据（含上月表底、已保存的本月表底等）
  let readingsMap = {}
  try {
    const readings = await readingApi.getByDate({
      readingDate: readingDate.value,
      villageName: selectedVillage.value
    })
    if (readings?.length) {
      readings.forEach(r => { readingsMap[r.waterMeterId] = r })
    }
  } catch { /* 首次使用 */ }

  // 3. 构建表格行
  tableData.value = households.map(h => {
    const r = readingsMap[h.waterMeterId]
    if (r) {
      const chargeable = r.chargeableUsage != null ? r.chargeableUsage
        : (r.usageAmount != null ? r.usageAmount : null)
      return {
        waterMeterId: h.waterMeterId,
        householdName: h.householdName,
        phone: h.phone || '',
        previousReading: r.previousReading != null ? r.previousReading : 0,
        currentReading: r.currentReading != null ? String(r.currentReading) : null,
        usageAmount: r.usageAmount != null ? Number(r.usageAmount).toFixed(2) : null,
        chargeableUsage: chargeable != null ? Number(chargeable) : null,
        waterCharge: chargeable != null ? (Number(chargeable) * waterPrice.value).toFixed(2) : null,
        note: r.note || '',
        isAbnormal: r.isAbnormal || false,
        abnormalReason: r.abnormalReason || ''
      }
    }
    // 无抄表记录：仅填写上月表底
    const prev = r?.previousReading != null ? r.previousReading : 0
    return {
      waterMeterId: h.waterMeterId,
      householdName: h.householdName,
      phone: h.phone || '',
      previousReading: prev,
      currentReading: null,
      usageAmount: null,
      chargeableUsage: null,
      waterCharge: null,
      note: '',
      isAbnormal: false,
      abnormalReason: ''
    }
  })
}

// 实时计算：本月表底变化时
function calcRow(row) {
  if (!row.currentReading || isNaN(row.currentReading)) {
    row.usageAmount = null
    row.chargeableUsage = null
    row.waterCharge = null
    row.isAbnormal = false
    row.abnormalReason = ''
    return
  }
  const cur = parseFloat(row.currentReading)
  const prev = parseFloat(row.previousReading) || 0
  const usage = cur - prev
  row.usageAmount = usage.toFixed(2)

  // 异常检测（客户端预检）
  if (cur < prev) {
    row.isAbnormal = true
    row.abnormalReason = '表底倒转'
  } else {
    row.isAbnormal = false
    row.abnormalReason = ''
  }

  // 计费用水量默认等于用水量
  row.chargeableUsage = usage > 0 ? Number(usage.toFixed(2)) : 0
  calcCharge(row)
}

// 计费用水量变化时重算水费
function calcCharge(row) {
  const chargeable = row.chargeableUsage
  if (chargeable != null && !isNaN(chargeable) && chargeable >= 0) {
    row.waterCharge = (chargeable * waterPrice.value).toFixed(2)
  } else {
    row.waterCharge = null
  }
}

// 导出空白模板
async function exportTemplate() {
  const params = {}
  if (selectedVillage.value) params.villageNames = selectedVillage.value
  try {
    const blob = await readingApi.exportTemplate(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '抄表模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模板已下载')
  } catch { /* error handled by interceptor */ }
}

// 导入模板
async function importTemplate(file) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('readingDate', readingDate.value)
  try {
    const result = await readingApi.importReadings(formData)
    ElMessage.success(`导入完成：成功${result.total}条，异常${result.abnormal}条`)
    loadTable()
  } catch { /* error handled by interceptor */ }
  return false // 阻止默认上传
}

// 批量保存
async function batchSave() {
  if (!selectedVillage.value) {
    ElMessage.warning('请先选择村组')
    return
  }

  const items = tableData.value
    .filter(r => r.currentReading && !isNaN(r.currentReading))
    .map(r => {
      const item = {
        waterMeterId: r.waterMeterId,
        currentReading: parseFloat(r.currentReading)
      }
      // 如果计费用水量与用水量不同，才传 chargeableUsage
      if (r.chargeableUsage != null && !isNaN(r.chargeableUsage)) {
        item.chargeableUsage = parseFloat(r.chargeableUsage)
      }
      if (r.note) {
        item.note = r.note
      }
      return item
    })

  if (!items.length) {
    ElMessage.warning('请至少输入一个表底数')
    return
  }

  saving.value = true
  try {
    const result = await readingApi.batchSave(items, readingDate.value)
    ElMessage.success(`保存完成：成功${result.total}条，异常${result.abnormal}条`)
    await loadTable()
  } catch { /* error handled by interceptor */ }
  finally { saving.value = false }
}
</script>

<style scoped>
.is-error :deep(.el-input__inner) {
  border-color: #F56C6C !important;
  background: #fef0f0 !important;
}
</style>
