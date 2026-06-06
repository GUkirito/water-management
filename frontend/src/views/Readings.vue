<template>
  <div>
    <!-- 操作栏 -->
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap;background:#fff;padding:16px;border-radius:8px">
      <el-date-picker v-model="month" type="month" placeholder="选择月份" value-format="YYYY-MM" />
      <el-select v-model="villages" multiple placeholder="按村筛选" clearable style="width:220px">
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
      <el-table :data="tableData" border stripe max-height="calc(100vh - 340px)" @cell-click="onCellClick">
        <el-table-column prop="waterMeterId" label="水表编号" width="120" />
        <el-table-column prop="householdName" label="户名" width="100" />
        <el-table-column prop="villageName" label="村名" width="100" />
        <el-table-column prop="previousReading" label="上月表底" width="100" />
        <el-table-column label="本次表底" width="150">
          <template #default="{ row }">
            <el-input v-model="row.currentReading" placeholder="输入表底" size="small"
              @change="calcRow(row)" :class="{ 'is-error': row.isAbnormal }" />
          </template>
        </el-table-column>
        <el-table-column label="用量(吨)" width="100">
          <template #default="{ row }">{{ row.usageAmount }}</template>
        </el-table-column>
        <el-table-column label="应收水费(元)" width="110">
          <template #default="{ row }">{{ row.expectedCharge }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="abnormalReason" label="异常原因" min-width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, readingApi } from '@/api'

const WATER_PRICE = 1.8
const THRESHOLD = 100

const month = ref(new Date().toISOString().slice(0, 7))
const villages = ref([])
const villageList = ref([])
const tableData = ref([])
const saving = ref(false)

// 加载活跃水表数据 → 表格行
async function loadTable() {
  // 获取所有活跃户
  const params = { page: 0, size: 1000 }
  if (villages.value.length) params.villageNames = villages.value.join(',')
  const result = await householdApi.list(params)
  const list = result?.content || []
  villageList.value = [...new Set(list.map(h => h.villageName))]

  // 获取最新抄表记录（上月表底）
  const meterIds = list.map(h => h.waterMeterId)
  let lastReadings = {}
  if (meterIds.length) {
    try {
      const readings = await readingApi.getByMonth({
        year: new Date().getFullYear(),
        month: new Date().getMonth() // 上个月
      })
      if (readings?.length) {
        readings.forEach(r => { lastReadings[r.waterMeterId] = r.currentReading })
      }
    } catch { /* 首次使用，没有历史抄表记录 */ }
  }

  tableData.value = list.map(h => ({
    ...h,
    previousReading: lastReadings[h.waterMeterId] || 0,
    currentReading: null,
    usageAmount: '-',
    expectedCharge: '-',
    isAbnormal: false,
    abnormalReason: ''
  }))
}

// 实时计算用量和费用
function calcRow(row) {
  if (!row.currentReading || isNaN(row.currentReading)) {
    row.usageAmount = '-'
    row.expectedCharge = '-'
    row.isAbnormal = false
    row.abnormalReason = ''
    return
  }
  const cur = parseFloat(row.currentReading)
  const prev = parseFloat(row.previousReading) || 0
  const usage = cur - prev
  row.usageAmount = usage.toFixed(2)

  if (cur < prev) {
    row.isAbnormal = true
    row.abnormalReason = '表底倒转'
    row.expectedCharge = '0.00'
  } else if (usage > THRESHOLD) {
    row.isAbnormal = true
    row.abnormalReason = `用量突增（${usage.toFixed(2)}吨 > ${THRESHOLD}吨）`
    row.expectedCharge = (usage * WATER_PRICE).toFixed(2)
  } else {
    row.isAbnormal = false
    row.abnormalReason = ''
    row.expectedCharge = (usage * WATER_PRICE).toFixed(2)
  }
}

function onCellClick(row, column) {
  if (column.label === '本次表底') return // 让 input 处理点击
}

// 导出空白模板
async function exportTemplate() {
  const params = {}
  if (villages.value.length) params.villageNames = villages.value.join(',')
  const blob = await readingApi.exportTemplate(params)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = '抄表模板.xlsx'; a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板已下载')
}

// 导入模板
async function importTemplate(file) {
  const formData = new FormData()
  formData.append('file', file)
  const [y, m] = month.value.split('-')
  formData.append('year', y)
  formData.append('month', m)
  const result = await readingApi.importReadings(formData)
  ElMessage.success(`导入完成：成功${result.total}条，异常${result.abnormal}条`)
  loadTable()
  return false // 阻止默认上传
}

// 批量保存
async function batchSave() {
  const items = tableData.value
    .filter(r => r.currentReading && !isNaN(r.currentReading))
    .map(r => ({ waterMeterId: r.waterMeterId, currentReading: parseFloat(r.currentReading) }))

  if (!items.length) { ElMessage.warning('请至少输入一个表底数'); return }

  saving.value = true
  try {
    const [y, m] = month.value.split('-')
    const result = await readingApi.batchSave(items, parseInt(y), parseInt(m))
    ElMessage.success(`保存完成：成功${result.total}条，异常${result.abnormal}条`)
    loadTable()
  } finally { saving.value = false }
}

onMounted(loadTable)
</script>

<style scoped>
.is-error .el-input__inner { border-color: #F56C6C !important; background: #fef0f0 !important; }
</style>
