<template>
  <div class="wm-page wm-readings-page">
    <section class="wm-page-header wm-page-header--compact">
      <div class="wm-page-title">
        <h1>抄表录入</h1>
        <p>按村管理住户抄表数据，支持批量录入、导入和异常提醒。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">抄表进度 {{ readingDoneCount }} / {{ tableData.length }}</span>
      </div>
    </section>

    <div class="wm-reading-workspace">
      <aside class="wm-panel wm-reading-sidebar">
        <div class="wm-panel-body wm-page-shell">
          <div>
            <div style="font-size:15px;font-weight:600;margin-bottom:10px">村组</div>
            <div style="display:flex;flex-wrap:wrap;gap:6px">
              <el-tag v-for="v in allVillages" :key="v" :type="selectedVillage===v?'primary':'info'" class="wm-click-tag" @click="selectVillage(v)" size="small">
                {{ v }}
              </el-tag>
              <el-tag :type="selectedVillage===''?'primary':'info'" class="wm-click-tag" @click="selectVillage('')" size="small">全部</el-tag>
            </div>
          </div>

          <el-input v-model="filterKeyword" placeholder="搜索户名/表号" size="small" clearable />

          <div class="wm-divider"></div>

          <div v-if="selectedHousehold" class="wm-panel" style="box-shadow:none">
            <div class="wm-panel-body">
              <div style="font-size:14px;font-weight:600;margin-bottom:10px">编辑住户</div>
              <el-form :model="householdForm" label-width="72px" size="small">
                <el-form-item label="户主">
                  <el-input v-model="householdForm.householdName" />
                </el-form-item>
                <el-form-item label="表号">
                  <el-input v-model="householdForm.waterMeterId" :disabled="!!householdForm.id" />
                </el-form-item>
                <el-form-item label="电话">
                  <el-input v-model="householdForm.phone" />
                </el-form-item>
                <el-form-item label="村组">
                  <el-select v-model="householdForm.villageName" filterable allow-create style="width:100%">
                    <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
                  </el-select>
                </el-form-item>
                <div class="wm-table-actions">
                  <el-button type="primary" size="small" @click="saveHousehold" :loading="savingHousehold">保存</el-button>
                  <el-button v-if="householdForm.id" type="danger" size="small" @click="deleteHousehold">永久删除</el-button>
                </div>
              </el-form>
            </div>
          </div>

          <el-button size="small" @click="addNewHousehold">新增住户</el-button>
        </div>
      </aside>

      <main class="wm-page-shell wm-reading-main">
        <section class="wm-toolbar">
          <span style="font-weight:600;color:var(--wm-text)">抄表日期</span>
          <el-date-picker v-model="readingDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" @change="loadTable" style="width:160px" size="small" />
          <el-button size="small" @click="exportTemplate">导出空白模板</el-button>
          <el-dropdown trigger="click" @command="handleImportCommand">
            <el-button size="small" type="warning">导入模板</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="readings">导入抄表数据</el-dropdown-item>
                <el-dropdown-item command="register">导入住户信息</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" type="primary" @click="batchSave" :loading="saving">批量保存</el-button>
          <el-button size="small" type="danger" @click="batchDeleteHouseholds" :disabled="selectedHouseholdIds.length===0">
            批量删除({{selectedHouseholdIds.length}})
          </el-button>
          <div style="margin-left:auto" class="wm-chip">已完成 {{ readingDoneCount }} / {{ tableData.length }}</div>
        </section>

        <section v-if="selectedHouseholdIds.length>0" class="wm-toolbar wm-toolbar--compact">
          <span class="wm-chip">已选 {{ selectedHouseholdIds.length }} 户</span>
          <span style="font-size:13px;color:var(--wm-text-2)">批量改村组</span>
          <el-select v-model="batchVillage" placeholder="选择村组" size="small" style="width:160px" filterable allow-create>
            <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
          </el-select>
          <el-button size="small" type="primary" @click="applyBatchVillage" :disabled="!batchVillage">应用</el-button>
        </section>

        <section class="wm-toolbar wm-toolbar--compact">
          <el-checkbox v-model="showMoreColumns" size="small">显示更多列</el-checkbox>
          <el-input v-model="tableKeyword" placeholder="搜索户名/表号" size="small" style="width:200px;margin-left:auto" clearable />
        </section>

        <section class="wm-panel wm-reading-table-panel">
          <div class="wm-table-shell wm-reading-table-shell">
            <el-table
              v-if="tableData.length > 0 || selectedVillage !== ''"
              :data="pagedTableData"
              border
              stripe
              style="width:100%"
              :height="tableHeight"
              :style="{ minWidth: showMoreColumns ? '1380px' : '1060px' }"
              :row-class-name="rowClassName"
              @row-click="onRowClick"
              highlight-current-row
              :current-row-key="selectedHousehold?.id || null"
              @selection-change="onSelectionChange"
            >
              <el-table-column type="selection" width="42" />
              <el-table-column type="index" label="#" width="48" />
              <el-table-column prop="householdName" label="户主" width="90" />
              <el-table-column prop="waterMeterId" label="表号" width="120" />
              <el-table-column prop="previousReading" label="上次表底" width="90" />
              <el-table-column label="本次表底" width="140">
                <template #default="{ row }">
                  <el-input v-model="row.currentReading" placeholder="输入" size="small" @change="calcRow(row)" :class="{ 'is-error': row.isAbnormal }" />
                </template>
              </el-table-column>
              <el-table-column label="用水量" width="90">
                <template #default="{ row }">{{ row.usageAmount != null ? row.usageAmount : '-' }}</template>
              </el-table-column>
              <el-table-column label="水费" width="90">
                <template #default="{ row }">{{ row.waterCharge != null ? row.waterCharge : '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="78">
                <template #default="{ row }">
                  <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
                  <el-tag v-else-if="row.currentReading" type="success" size="small">正常</el-tag>
                  <el-tag v-else type="info" size="small">-</el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="showMoreColumns" prop="phone" label="电话" width="120" />
              <el-table-column v-if="showMoreColumns" label="计费用水量" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.chargeableUsage" :precision="2" :min="0" size="small" controls-position="right" style="width:100px" @change="calcCharge(row)" />
                </template>
              </el-table-column>
              <el-table-column v-if="showMoreColumns" label="水价" width="80">
                <template #default>{{ waterPrice }}</template>
              </el-table-column>
              <el-table-column v-if="showMoreColumns" label="备注" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.note" placeholder="备注" size="small" />
                </template>
              </el-table-column>
              <el-table-column v-if="showMoreColumns" prop="abnormalReason" label="异常原因" min-width="140" show-overflow-tooltip />
            </el-table>

            <el-empty v-else description="请选择左侧村组开始录入抄表" :image-size="84" class="wm-empty" />

            <div v-if="tableData.length > 0 || selectedVillage !== ''" class="wm-reading-pagination">
              <el-pagination v-model:current-page="tablePage" v-model:page-size="tablePageSize" :page-sizes="[10,20,50,100]" :total="filteredTableData.length" layout="total,sizes,prev,pager,next" size="small" />
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { householdApi, readingApi } from '@/api'

const readingDate = ref(new Date().toISOString().slice(0, 10))
const selectedVillage = ref('')
const allVillages = ref([])
const filterKeyword = ref('')
const waterPrice = ref(1.8)
const abnormalThreshold = ref(100)
const readingDoneCount = computed(() => tableData.value.filter(r => r.currentReading && !isNaN(r.currentReading)).length)
const saving = ref(false)
const showMoreColumns = ref(false)
const tableKeyword = ref('')
const tablePage = ref(1)
const tablePageSize = ref(20)
const tableHeight = ref(420)

const householdList = ref([])
const selectedHousehold = ref(null)
const householdForm = reactive({ id: null, householdName: '', waterMeterId: '', phone: '', villageName: '' })
const savingHousehold = ref(false)

const tableData = ref([])
const selectedHouseholdIds = ref([])
const batchVillage = ref('')

const filteredHouseholdList = computed(() => {
  if (!filterKeyword.value) return householdList.value
  const kw = filterKeyword.value.toLowerCase()
  return householdList.value.filter(h =>
    (h.householdName || '').toLowerCase().includes(kw) ||
    (h.waterMeterId || '').toLowerCase().includes(kw)
  )
})

const filteredTableData = computed(() => {
  if (!tableKeyword.value) return tableData.value
  const kw = tableKeyword.value.toLowerCase()
  return tableData.value.filter(r =>
    (r.householdName || '').toLowerCase().includes(kw) ||
    (r.waterMeterId || '').toLowerCase().includes(kw)
  )
})

const pagedTableData = computed(() => {
  const start = (tablePage.value - 1) * tablePageSize.value
  return filteredTableData.value.slice(start, start + tablePageSize.value)
})

function rowClassName({ row }) {
  const classes = []
  if (row.isAbnormal) classes.push('row-abnormal')
  if (selectedHousehold.value?.id && selectedHousehold.value.id === row.id) classes.push('is-selected-row')
  return classes.join(' ')
}

function updateTableHeight() {
  const panel = document.querySelector('.wm-reading-table-panel')
  if (!panel) return
  const rect = panel.getBoundingClientRect()
  tableHeight.value = Math.max(300, Math.floor(window.innerHeight - rect.top - 72))
}

function selectVillage(v) {
  selectedVillage.value = v
  filterKeyword.value = ''
  tableKeyword.value = ''
  tablePage.value = 1
  selectedHousehold.value = null
  loadHouseholdList()
  loadTable()
}

async function loadAllVillages() {
  try {
    const r = await householdApi.list({ page: 0, size: 10000 })
    allVillages.value = [...new Set((r?.content || []).map(h => h.villageName).filter(Boolean))].sort()
  } catch {}
}

async function loadHouseholdList() {
  try {
    const params = { page: 0, size: 10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    householdList.value = r?.content || []
  } catch {
    householdList.value = []
  }
}

function onSelectHousehold(h) {
  selectedHousehold.value = h
  householdForm.id = h.id || null
  householdForm.householdName = h.householdName
  householdForm.waterMeterId = h.waterMeterId
  householdForm.phone = h.phone || ''
  householdForm.villageName = h.villageName || selectedVillage.value || ''
}

function addNewHousehold() {
  selectedHousehold.value = { id: null, householdName: '', waterMeterId: '', phone: '' }
  householdForm.id = null
  householdForm.householdName = ''
  householdForm.waterMeterId = ''
  householdForm.phone = ''
  householdForm.villageName = selectedVillage.value || ''
}

async function saveHousehold() {
  if (!householdForm.householdName || !householdForm.waterMeterId) {
    ElMessage.warning('户主姓名和表号不能为空')
    return
  }
  savingHousehold.value = true
  try {
    const data = {
      householdName: householdForm.householdName,
      waterMeterId: householdForm.waterMeterId,
      phone: householdForm.phone,
      villageName: householdForm.villageName || selectedVillage.value || ''
    }
    if (householdForm.id) {
      await householdApi.update(householdForm.id, data)
      ElMessage.success('更新成功')
    } else {
      await householdApi.add(data)
      ElMessage.success('新增成功')
    }
    loadHouseholdList()
    loadTable()
    loadAllVillages()
  } catch {} finally {
    savingHousehold.value = false
  }
}

async function deleteHousehold() {
  if (!householdForm.id) return
  try {
    await ElMessageBox.confirm('确认永久删除该住户及其关联数据吗？此操作不可恢复。', '确认永久删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await householdApi.delete(householdForm.id)
    ElMessage.success('已永久删除')
    selectedHousehold.value = null
    loadHouseholdList()
    loadTable()
    loadAllVillages()
  } catch {}
}

async function loadTable() {
  let households = []
  try {
    const params = { page: 0, size: 10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    households = r?.content || []
  } catch {
    households = []
  }

  let readingsMap = {}
  try {
    const readings = await readingApi.getByDate({ readingDate: readingDate.value, villageName: selectedVillage.value })
    if (readings?.length) readings.forEach(r => { readingsMap[r.waterMeterId] = r })
  } catch {}

  tableData.value = households.map(h => {
    const r = readingsMap[h.waterMeterId]
    if (r) {
      const chargeable = r.chargeableUsage != null ? r.chargeableUsage : (r.usageAmount != null ? r.usageAmount : null)
      return {
        id: h.id,
        villageName: h.villageName,
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
    return {
      id: h.id,
      villageName: h.villageName,
      waterMeterId: h.waterMeterId,
      householdName: h.householdName,
      phone: h.phone || '',
      previousReading: r?.previousReading ?? 0,
      currentReading: null,
      usageAmount: null,
      chargeableUsage: null,
      waterCharge: null,
      note: '',
      isAbnormal: false,
      abnormalReason: ''
    }
  })
  nextTick(updateTableHeight)
}

function onRowClick(row) { onSelectHousehold(row) }
function onSelectionChange(rows) { selectedHouseholdIds.value = rows.map(r => r.id).filter(Boolean) }

async function applyBatchVillage() {
  if (!batchVillage.value || !selectedHouseholdIds.value.length) return
  try {
    await householdApi.batchUpdateVillage(selectedHouseholdIds.value, batchVillage.value)
    ElMessage.success(`已更新 ${selectedHouseholdIds.value.length} 户到 ${batchVillage.value}`)
    batchVillage.value = ''
    loadTable()
    loadHouseholdList()
    loadAllVillages()
  } catch {}
}

async function batchDeleteHouseholds() {
  if (!selectedHouseholdIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认永久删除选中的 ${selectedHouseholdIds.value.length} 户及其关联数据吗？`, '确认批量删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await householdApi.batchDelete(selectedHouseholdIds.value)
    ElMessage.success('已删除')
    selectedHouseholdIds.value = []
    loadTable()
    loadHouseholdList()
    loadAllVillages()
  } catch {}
}

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
  const reverseAbnormal = cur < prev
  const spikeAbnormal = usage > abnormalThreshold.value
  row.isAbnormal = reverseAbnormal || spikeAbnormal
  row.abnormalReason = reverseAbnormal ? '表底倒转' : spikeAbnormal ? `用量突增：${usage.toFixed(2)} 吨` : ''
  row.chargeableUsage = usage > 0 ? Number(usage.toFixed(2)) : 0
  calcCharge(row)
}

function calcCharge(row) {
  const c = row.chargeableUsage
  row.waterCharge = c != null && !isNaN(c) && c >= 0 ? (c * waterPrice.value).toFixed(2) : null
}

function handleImportCommand(cmd) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx'
  input.onchange = async e => {
    const file = e.target.files[0]
    if (!file) return
    if (cmd === 'readings') {
      const fd = new FormData()
      fd.append('file', file)
      fd.append('readingDate', readingDate.value)
      try {
        const r = await readingApi.importReadings(fd)
        ElMessage.success(`成功 ${r.total} 条`)
        loadTable()
      } catch {}
    } else {
      const fd = new FormData()
      fd.append('file', file)
      try {
        const r = await householdApi.importFromRegister(fd)
        ElMessage.success(`新增 ${r.inserted} 户`)
        loadTable()
        loadHouseholdList()
        loadAllVillages()
      } catch {}
    }
  }
  input.click()
}

async function exportTemplate() {
  const params = {}
  if (selectedVillage.value) params.villageNames = selectedVillage.value
  try {
    const blob = await readingApi.exportTemplate(params)
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = '抄表模板.xlsx'
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('模板已下载')
  } catch {}
}

async function batchSave() {
  if (!selectedVillage.value) {
    ElMessage.warning('请先选择村组')
    return
  }
  const items = tableData.value.filter(r => r.currentReading && !isNaN(r.currentReading)).map(r => {
    const item = { waterMeterId: r.waterMeterId, currentReading: parseFloat(r.currentReading) }
    if (r.chargeableUsage != null && !isNaN(r.chargeableUsage)) item.chargeableUsage = parseFloat(r.chargeableUsage)
    if (r.note) item.note = r.note
    return item
  })
  if (!items.length) {
    ElMessage.warning('请至少输入一个表底')
    return
  }
  saving.value = true
  try {
    const r = await readingApi.batchSave(items, readingDate.value)
    ElMessage.success(`保存完成：成功 ${r.total} 条`)
    loadTable()
  } catch {} finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const c = await readingApi.getConfig()
    waterPrice.value = c.waterPrice || 1.8
    abnormalThreshold.value = c.abnormalThreshold || 100
  } catch {}
  await loadAllVillages()
  nextTick(updateTableHeight)
  window.addEventListener('resize', updateTableHeight, { passive: true })
})

watch([selectedVillage, showMoreColumns, tablePage, tablePageSize], () => {
  nextTick(updateTableHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableHeight)
})
</script>

<style scoped>
.wm-click-tag {
  cursor: pointer;
}

.wm-page-header--compact {
  padding-top: 8px;
  padding-bottom: 8px;
}

.wm-page-header--compact .wm-page-title h1 {
  font-size: 15px;
}

.wm-page-header--compact .wm-page-title p {
  font-size: 11px;
}

.wm-readings-page {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 10px;
}

.wm-reading-workspace {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.wm-reading-sidebar {
  position: sticky;
  top: 0;
  max-height: calc(100dvh - 184px);
  overflow: auto;
  min-height: 0;
}

.wm-reading-main {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.wm-reading-table-panel {
  min-height: 0;
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.wm-reading-table-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  flex: 1;
}

.wm-reading-table-shell :deep(.el-table) {
  min-width: 1020px;
}

.wm-reading-table-shell :deep(.el-table__inner-wrapper) {
  overflow-x: auto;
  overflow-y: hidden;
}

.wm-reading-table-shell :deep(.el-table__body-wrapper) {
  max-height: none;
}

.wm-reading-table-shell :deep(.el-table__body tr.current-row > td) {
  background-color: #dbeafe !important;
}

.wm-reading-table-shell :deep(.el-table__body tr.is-selected-row > td) {
  background-color: #bfdbfe !important;
  box-shadow: inset 0 0 0 9999px rgba(37, 99, 235, 0.18);
  font-weight: 600;
}

.wm-reading-table-shell :deep(.el-table__body tr.is-selected-row:hover > td) {
  background-color: #93c5fd !important;
}

.wm-reading-table-shell :deep(.el-table__body tr.row-abnormal > td) {
  background-color: #fff7ed !important;
}

.wm-reading-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 0;
  flex: 0 0 auto;
}

.is-error :deep(.el-input__inner) {
  border-color: var(--wm-danger) !important;
  background: #fef2f2 !important;
}

@media (max-width: 1280px) {
  .wm-reading-workspace {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .wm-reading-workspace {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .wm-reading-sidebar {
    position: static;
    max-height: none;
  }

  .wm-reading-table-shell {
    max-height: none;
  }
}

@media (max-width: 768px) {
  .wm-page-header--compact {
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .wm-reading-table-shell :deep(.el-table) {
    min-width: 1120px;
  }
}
</style>
