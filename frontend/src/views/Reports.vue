<template>
  <div>
    <el-tabs v-model="activeTab" type="card" style="background:#fff;padding:4px 16px 0;border-radius:8px 8px 0 0">
      <el-tab-pane label="水费月报表" name="water" />
      <el-tab-pane label="材料费统计表" name="material" />
    </el-tabs>

    <div style="background:#fff;padding:16px;border-radius:0 0 8px 8px">
      <!-- 水费月报表 -->
      <div v-if="activeTab === 'water'">
        <div style="display:flex;gap:12px;margin-bottom:16px;align-items:center">
          <el-date-picker v-model="waterMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" />
          <el-select v-model="waterVillages" multiple placeholder="按村筛选" clearable style="width:200px">
            <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
          </el-select>
          <el-button type="primary" @click="loadWaterReport">查询</el-button>
          <el-button type="success" @click="exportWaterReport">📥 导出 Excel</el-button>
        </div>
        <el-table :data="waterData" border stripe max-height="calc(100vh - 380px)">
          <el-table-column prop="villageName" label="村名" width="100" />
          <el-table-column prop="waterMeterId" label="水表编号" width="120" />
          <el-table-column prop="householdName" label="户名" width="100" />
          <el-table-column prop="waterAmount" label="用水量(吨)" width="110" />
          <el-table-column prop="waterCharge" label="应收水费" width="110" />
          <el-table-column prop="actualWaterPaid" label="实收水费" width="110" />
          <el-table-column prop="waterStatus" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.waterStatus === '已收' ? 'success' : row.waterStatus === '部分收' ? 'warning' : 'danger'" size="small">
                {{ row.waterStatus }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 材料费统计表 -->
      <div v-if="activeTab === 'material'">
        <div style="display:flex;gap:12px;margin-bottom:16px;align-items:center">
          <el-select v-model="matVillages" placeholder="按村筛选" clearable style="width:200px">
            <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
          </el-select>
          <el-button type="primary" @click="loadMaterialReport">查询</el-button>
          <el-button type="success" @click="exportMaterialReport">📥 导出 Excel</el-button>
        </div>
        <el-table :data="materialData" border stripe max-height="calc(100vh - 380px)">
          <el-table-column prop="waterMeterId" label="水表编号" width="120" />
          <el-table-column prop="householdName" label="户名" width="100" />
          <el-table-column prop="villageName" label="村名" width="100" />
          <el-table-column prop="totalFee" label="应收材料费" width="120" />
          <el-table-column prop="actualPaid" label="已缴材料费" width="120" />
          <el-table-column prop="unpaid" label="欠费金额" width="120" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === '已收' ? 'success' : row.status === '部分收' ? 'warning' : 'danger'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
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
  villageList.value = [...new Set(list.map(h => h.villageName))]
})

async function loadWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value.join(',')
  waterData.value = await reportApi.getWaterBillReport(params) || []
}

async function exportWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value.join(',')
  const blob = await reportApi.exportWaterBillReport(params)
  downloadBlob(blob, `${y}年${m}月_水费报表.xlsx`)
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
    totalFee: r.totalFee,
    actualPaid: r.actualPaid,
    unpaid: (r.totalFee || 0) - (r.actualPaid || 0),
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
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}
</script>
