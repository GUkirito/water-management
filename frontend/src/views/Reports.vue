<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>报表中心</h1>
        <p>水费与材料费两套独立统计，支持筛选、汇总和导出。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">双报表模式</span>
      </div>
    </section>

    <section class="wm-panel">
      <el-tabs v-model="activeTab" class="wm-tabs" type="card" @tab-change="onTabChange">
        <el-tab-pane label="水费月报" name="water" />
        <el-tab-pane label="材料费统计" name="material" />
      </el-tabs>

      <div class="wm-panel-body">
        <!-- ============ 水费月报 ============ -->
        <div v-if="activeTab === 'water'" class="wm-page-shell">
          <div class="wm-toolbar wm-toolbar--compact">
            <el-date-picker v-model="waterMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" />
            <el-select v-model="waterVillages" multiple placeholder="按村筛选（可多选）" clearable style="width:260px">
              <el-option v-for="v in waterVillageList" :key="v" :label="v" :value="v" />
            </el-select>
            <el-button type="primary" @click="loadWaterReport" :loading="waterLoading">查询</el-button>
            <el-button type="success" @click="exportWaterReport">导出 Excel</el-button>
            <el-button @click="printPage">打印</el-button>
          </div>

          <!-- 水费汇总卡片 -->
          <div class="wm-kpi-grid">
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">户数</div>
              <div class="wm-kpi-value">{{ waterStats.count }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">应收总额</div>
              <div class="wm-kpi-value wm-text-warn">¥{{ waterStats.charge }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">实收总额</div>
              <div class="wm-kpi-value wm-text-ok">¥{{ waterStats.paid }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">欠费总额</div>
              <div class="wm-kpi-value wm-text-bad">¥{{ waterStats.unpaid }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">收缴率</div>
              <div class="wm-kpi-value">{{ waterStats.rate }}%</div>
            </div>
          </div>

          <div v-if="waterLoading" class="skeleton wm-table-skeleton"></div>
          <el-table v-else :data="waterData" border stripe max-height="calc(100vh - 460px)">
            <el-table-column prop="villageName" label="村名" width="120" resizable />
            <el-table-column prop="waterMeterId" label="水表编号" width="140" resizable />
            <el-table-column prop="householdName" label="户名" width="120" resizable />
            <el-table-column prop="waterAmount" label="用水量(吨)" width="120" resizable />
            <el-table-column prop="waterCharge" label="应收水费" width="120" resizable />
            <el-table-column prop="actualWaterPaid" label="实收水费" width="120" resizable />
            <el-table-column label="欠费" width="120" resizable>
              <template #default="{ row }">
                <span :style="{ color: unpaidWater(row) > 0 ? 'var(--wm-danger)' : 'var(--wm-success)', fontWeight: 600 }">
                  ¥{{ unpaidWater(row).toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="waterStatus" label="状态" width="100" resizable>
              <template #default="{ row }">
                <el-tag :type="tagType(row.waterStatus)" size="small">{{ row.waterStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="缴费方式" width="120" resizable>
              <template #default="{ row }">{{ row.paymentMethod || '-' }}</template>
            </el-table-column>
            <el-table-column prop="note" label="备注" width="200" resizable show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!waterData.length && !waterLoading" :image-size="72" class="wm-empty">
            <template #description>
              <p class="text-gray-500 text-sm">暂无符合条件的水费报表</p>
              <p class="text-gray-400 text-xs mt-1">请先完成抄表录入，或调整筛选条件后重试</p>
            </template>
          </el-empty>
        </div>

        <!-- ============ 材料费统计 ============ -->
        <div v-else class="wm-page-shell">
          <div class="wm-toolbar wm-toolbar--compact">
            <el-select v-model="matVillage" placeholder="按村筛选" clearable style="width:200px">
              <el-option v-for="v in matVillageList" :key="v" :label="v" :value="v" />
            </el-select>
            <el-select v-model="matStatus" placeholder="状态" clearable style="width:120px">
              <el-option label="已收" value="已收" />
              <el-option label="部分收" value="部分收" />
              <el-option label="未收" value="未收" />
            </el-select>
            <el-input v-model="matKeyword" placeholder="户名/表号" clearable style="width:180px" />
            <el-date-picker v-model="matDateRange" type="daterange" range-separator="至"
              start-placeholder="缴费日期起" end-placeholder="缴费日期止" value-format="YYYY-MM-DD" style="width:280px" />
            <el-button type="primary" @click="loadMaterialReport" :loading="matLoading">查询</el-button>
            <el-button @click="resetMaterialFilter">重置</el-button>
            <el-button type="success" @click="exportMaterialReport">导出 Excel</el-button>
            <el-button @click="printPage">打印</el-button>
          </div>

          <!-- 材料费汇总卡片 -->
          <div class="wm-kpi-grid">
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">户数</div>
              <div class="wm-kpi-value">{{ matStats.count }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">应收总额</div>
              <div class="wm-kpi-value wm-text-warn">¥{{ matStats.totalFee }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">实收总额</div>
              <div class="wm-kpi-value wm-text-ok">¥{{ matStats.actualPaid }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">欠费总额</div>
              <div class="wm-kpi-value wm-text-bad">¥{{ matStats.unpaid }}</div>
            </div>
            <div class="wm-kpi-card">
              <div class="wm-kpi-label">收缴率</div>
              <div class="wm-kpi-value">{{ matStats.rate }}%</div>
            </div>
          </div>

          <div v-if="matLoading" class="skeleton wm-table-skeleton"></div>
          <el-table v-else :data="materialData" border stripe max-height="calc(100vh - 460px)">
            <el-table-column prop="waterMeterId" label="水表编号" width="140" resizable />
            <el-table-column prop="householdName" label="户名" width="120" resizable />
            <el-table-column prop="villageName" label="村名" width="120" resizable />
            <el-table-column prop="totalFee" label="应收材料费" width="130" resizable />
            <el-table-column prop="actualPaid" label="实收材料费" width="130" resizable />
            <el-table-column label="欠费" width="120" resizable>
              <template #default="{ row }">
                <span :style="{ color: Number(row.unpaid) > 0 ? 'var(--wm-danger)' : 'var(--wm-success)', fontWeight: 600 }">
                  ¥{{ row.unpaid }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="paidAt" label="最近缴费" width="120" resizable />
            <el-table-column prop="status" label="状态" width="100" resizable>
              <template #default="{ row }">
                <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!materialData.length && !matLoading" :image-size="72" class="wm-empty">
            <template #description>
              <p class="text-gray-500 text-sm">暂无符合条件的材料费统计</p>
              <p class="text-gray-400 text-xs mt-1">请筛选日期和村组后查看统计数据</p>
            </template>
          </el-empty>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, reportApi, materialRecordApi } from '@/api'

const activeTab = ref('water')

// ===== 水费月报 =====
const waterMonth = ref(new Date().toISOString().slice(0, 7))
const waterVillages = ref([])
const waterVillageList = ref([])   // 来源：household 表
const waterData = ref([])
const waterLoading = ref(false)
const waterStats = reactive({ count: 0, charge: '0.00', paid: '0.00', unpaid: '0.00', rate: '0.0' })

// ===== 材料费统计 =====
const matVillage = ref('')
const matStatus = ref('')
const matKeyword = ref('')
const matDateRange = ref([])
const matVillageList = ref([])     // 来源：material_records 表（独立）
const materialData = ref([])
const matLoading = ref(false)
const matStats = reactive({ count: 0, totalFee: '0.00', actualPaid: '0.00', unpaid: '0.00', rate: '0.0' })
let matVillageLoaded = false

onMounted(async () => {
  await loadWaterVillages()
  await loadWaterReport()
  window.addEventListener('wm-refresh', refreshCurrentReport)
})

onBeforeUnmount(() => {
  window.removeEventListener('wm-refresh', refreshCurrentReport)
})

function refreshCurrentReport() {
  if (activeTab.value === 'water') {
    loadWaterReport()
  } else {
    loadMaterialReport()
  }
}

function printPage() {
  window.print()
}

async function onTabChange(tab) {
  if (tab === 'material' && !matVillageLoaded) {
    await loadMatVillages()
    await loadMaterialReport()
  }
}

// 水费 Tab 的村组：来自 household 表
async function loadWaterVillages() {
  try {
    const result = await householdApi.list({ page: 0, size: 10000 })
    const list = result?.content || []
    waterVillageList.value = [...new Set(list.map(h => h.villageName).filter(Boolean))].sort()
  } catch (error) {
    console.warn('加载水费报表村组失败', error)
    waterVillageList.value = []
  }
}

// 材料费 Tab 的村组：从材料费表自身聚合
async function loadMatVillages() {
  try {
    const result = await materialRecordApi.list({ page: 0, size: 10000 })
    const list = result?.content || []
    matVillageList.value = [...new Set(list.map(r => r.villageName).filter(Boolean))].sort()
    matVillageLoaded = true
  } catch (error) {
    console.warn('加载材料费报表村组失败', error)
    matVillageList.value = []
  }
}

function tagType(status) {
  return status === '已收' ? 'success' : status === '部分收' ? 'warning' : 'danger'
}

function unpaidWater(row) {
  return Math.max(0, Number(row.waterCharge || 0) - Number(row.actualWaterPaid || 0))
}

// ===== 水费报表 =====

async function loadWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value
  waterLoading.value = true
  try {
    waterData.value = await reportApi.getWaterBillReport(params) || []
    computeWaterStats()
  } catch (error) {
    console.warn('加载水费报表失败', error)
  } finally { waterLoading.value = false }
}

function computeWaterStats() {
  let charge = 0, paid = 0
  waterData.value.forEach(r => {
    charge += Number(r.waterCharge || 0)
    paid += Number(r.actualWaterPaid || 0)
  })
  const unpaid = Math.max(0, charge - paid)
  waterStats.count = waterData.value.length
  waterStats.charge = charge.toFixed(2)
  waterStats.paid = paid.toFixed(2)
  waterStats.unpaid = unpaid.toFixed(2)
  waterStats.rate = charge > 0 ? ((paid / charge) * 100).toFixed(1) : '100.0'
}

async function exportWaterReport() {
  const [y, m] = waterMonth.value.split('-')
  const params = { year: parseInt(y), month: parseInt(m) }
  if (waterVillages.value.length) params.villageNames = waterVillages.value
  try {
    const blob = await reportApi.exportWaterBillReport(params)
    downloadBlob(blob, `${y}年${m}月水费报表.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
    console.warn('导出水费报表失败', error)
  }
}

// ===== 材料费报表 =====

async function loadMaterialReport() {
  const params = { page: 0, size: 10000 }
  if (matVillage.value) params.villageName = matVillage.value
  if (matStatus.value) params.status = matStatus.value
  if (matKeyword.value) params.keyword = matKeyword.value
  if (matDateRange.value && matDateRange.value.length === 2) {
    params.paidDateFrom = matDateRange.value[0]
    params.paidDateTo = matDateRange.value[1]
  }
  matLoading.value = true
  try {
    const result = await materialRecordApi.list(params)
    const list = result?.content || []
    materialData.value = list.map(r => ({
      waterMeterId: r.waterMeterId,
      householdName: r.householdName,
      villageName: r.villageName,
      totalFee: Number(r.totalFee || 0).toFixed(2),
      actualPaid: Number(r.actualPaid || 0).toFixed(2),
      unpaid: (Number(r.totalFee || 0) - Number(r.actualPaid || 0)).toFixed(2),
      paidAt: r.paidAt || '-',
      status: r.status
    }))
    computeMatStats()
  } catch (error) {
    console.warn('加载材料费统计失败', error)
  } finally { matLoading.value = false }
}

function computeMatStats() {
  let totalFee = 0, actualPaid = 0
  materialData.value.forEach(r => {
    totalFee += Number(r.totalFee || 0)
    actualPaid += Number(r.actualPaid || 0)
  })
  const unpaid = Math.max(0, totalFee - actualPaid)
  matStats.count = materialData.value.length
  matStats.totalFee = totalFee.toFixed(2)
  matStats.actualPaid = actualPaid.toFixed(2)
  matStats.unpaid = unpaid.toFixed(2)
  matStats.rate = totalFee > 0 ? ((actualPaid / totalFee) * 100).toFixed(1) : '100.0'
}

function resetMaterialFilter() {
  matVillage.value = ''
  matStatus.value = ''
  matKeyword.value = ''
  matDateRange.value = []
  loadMaterialReport()
}

async function exportMaterialReport() {
  const params = {}
  if (matVillage.value) params.villageName = matVillage.value
  if (matStatus.value) params.status = matStatus.value
  if (matKeyword.value) params.keyword = matKeyword.value
  if (matDateRange.value && matDateRange.value.length === 2) {
    params.paidDateFrom = matDateRange.value[0]
    params.paidDateTo = matDateRange.value[1]
  }
  try {
    const blob = await materialRecordApi.exportExcel(params)
    downloadBlob(blob, '材料费统计表.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    console.warn('导出材料费统计失败', error)
  }
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

<style scoped>
.wm-kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 12px 0 16px;
}
.wm-kpi-card {
  background: linear-gradient(180deg, #ffffff 0%, #f8fcff 100%);
  border: 1px solid var(--wm-border);
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: var(--wm-shadow-soft);
}
.wm-kpi-label {
  font-size: 13px;
  color: var(--wm-text-2);
  margin-bottom: 6px;
}
.wm-kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--wm-text);
}
.wm-text-ok { color: var(--wm-success); }
.wm-text-warn { color: var(--wm-warning); }
.wm-text-bad { color: var(--wm-danger); }
@media (max-width: 1100px) {
  .wm-kpi-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
