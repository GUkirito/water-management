<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>报表中心</h1>
        <p>按月查看水费与材料费统计，支持筛选和导出。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">双报表模式</span>
      </div>
    </section>

    <section class="wm-panel">
      <el-tabs v-model="activeTab" class="wm-tabs" type="card">
        <el-tab-pane label="水费月报" name="water" />
        <el-tab-pane label="材料费统计" name="material" />
      </el-tabs>

      <div class="wm-panel-body">
        <div v-if="activeTab === 'water'" class="wm-page-shell">
          <div class="wm-toolbar wm-toolbar--compact">
            <el-date-picker v-model="waterMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" />
            <el-select v-model="waterVillages" multiple placeholder="按村筛选" clearable style="width:240px">
              <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
            </el-select>
            <el-button type="primary" @click="loadWaterReport">查询</el-button>
            <el-button type="success" @click="exportWaterReport">导出 Excel</el-button>
          </div>

          <el-table :data="waterData" border stripe max-height="calc(100vh - 380px)">
            <el-table-column prop="villageName" label="村名" width="120" />
            <el-table-column prop="waterMeterId" label="水表编号" width="140" />
            <el-table-column prop="householdName" label="户名" width="120" />
            <el-table-column prop="waterAmount" label="用水量(吨)" width="120" />
            <el-table-column prop="waterCharge" label="应收水费" width="120" />
            <el-table-column prop="actualWaterPaid" label="实收水费" width="120" />
            <el-table-column prop="waterStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="tagType(row.waterStatus)" size="small">{{ row.waterStatus }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!waterData.length" description="暂无符合条件的水费报表" :image-size="72" class="wm-empty" />
        </div>

        <div v-else class="wm-page-shell">
          <div class="wm-toolbar wm-toolbar--compact">
            <el-select v-model="matVillages" placeholder="按村筛选" clearable style="width:240px">
              <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
            </el-select>
            <el-button type="primary" @click="loadMaterialReport">查询</el-button>
            <el-button type="success" @click="exportMaterialReport">导出 Excel</el-button>
          </div>

          <el-table :data="materialData" border stripe max-height="calc(100vh - 380px)">
            <el-table-column prop="waterMeterId" label="水表编号" width="140" />
            <el-table-column prop="householdName" label="户名" width="120" />
            <el-table-column prop="villageName" label="村名" width="120" />
            <el-table-column prop="totalFee" label="应收材料费" width="130" />
            <el-table-column prop="actualPaid" label="实收材料费" width="130" />
            <el-table-column prop="unpaid" label="欠费" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!materialData.length" description="暂无符合条件的材料费统计" :image-size="72" class="wm-empty" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, reportApi, materialRecordApi } from '@/api'

const activeTab = ref('water')
const waterMonth = ref(new Date().toISOString().slice(0, 7))
const waterVillages = ref([])
const matVillages = ref('')
const villageList = ref([])
const waterData = ref([])
const materialData = ref([])

onMounted(async () => {
  const result = await householdApi.list({ page: 0, size: 1000 })
  const list = result?.content || []
  villageList.value = [...new Set(list.map(h => h.villageName).filter(Boolean))].sort()
})

function tagType(status) {
  return status === '已收' ? 'success' : status === '部分收' ? 'warning' : 'danger'
}

async function loadWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value
  waterData.value = await reportApi.getWaterBillReport(params) || []
}

async function exportWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value
  const blob = await reportApi.exportWaterBillReport(params)
  downloadBlob(blob, `${y}年${m}月水费报表.xlsx`)
  ElMessage.success('导出成功')
}

async function loadMaterialReport() {
  const result = await materialRecordApi.list({ page: 0, size: 10000 })
  let list = result?.content || []
  if (matVillages.value) {
    list = list.filter(r => r.villageName === matVillages.value)
  }
  materialData.value = list.map(r => ({
    waterMeterId: r.waterMeterId,
    householdName: r.householdName,
    villageName: r.villageName,
    totalFee: Number(r.totalFee || 0).toFixed(2),
    actualPaid: Number(r.actualPaid || 0).toFixed(2),
    unpaid: (Number(r.totalFee || 0) - Number(r.actualPaid || 0)).toFixed(2),
    status: r.status
  }))
}

async function exportMaterialReport() {
  const params = {}
  if (matVillages.value) params.villageName = matVillages.value
  const blob = await materialRecordApi.exportExcel(params)
  downloadBlob(blob, '材料费统计表.xlsx')
  ElMessage.success('导出成功')
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
</script>
