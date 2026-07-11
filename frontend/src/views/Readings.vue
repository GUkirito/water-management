<template>
  <div class="wm-page wm-readings-page">
    <el-button class="wm-reading-sidebar-toggle" type="primary" circle @click="sidebarOpen = true">
      <el-icon><Menu /></el-icon>
    </el-button>
    <div v-if="sidebarOpen" class="wm-reading-sidebar-mask" @click="sidebarOpen = false"></div>
    <div class="wm-reading-workspace">
      <aside class="wm-panel wm-reading-sidebar" :class="{ 'is-open': sidebarOpen }">
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

          <div v-if="unpaidHouseholdShortcuts.length" class="wm-reading-quick-list">
            <div class="wm-reading-quick-title">常用户</div>
            <button
              v-for="row in unpaidHouseholdShortcuts"
              :key="row.waterMeterId"
              type="button"
              class="wm-reading-quick-item"
              @click="jumpToReadingRow(row)"
            >
              <span>{{ row.householdName }}</span>
              <el-tag type="warning" size="small">欠费</el-tag>
            </button>
          </div>

          <div v-if="recentReadingShortcuts.length" class="wm-reading-quick-list">
            <div class="wm-reading-quick-title">最近录入</div>
            <button
              v-for="row in recentReadingShortcuts"
              :key="row.waterMeterId"
              type="button"
              class="wm-reading-quick-item"
              @click="jumpToReadingRow(row)"
            >
              <span>{{ row.householdName }}</span>
              <small>{{ row.waterMeterId }}</small>
            </button>
          </div>

          <div v-if="unpaidHouseholdShortcuts.length || recentReadingShortcuts.length" class="wm-divider"></div>

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
                  <el-button v-if="householdForm.id" type="danger" size="small" @click="deleteHousehold">删除或停用</el-button>
                </div>
              </el-form>
            </div>
          </div>

          <el-button size="small" @click="addNewHousehold">新增住户</el-button>
        </div>
      </aside>

      <main class="wm-page-shell wm-reading-main">
        <section class="wm-toolbar wm-reading-toolbar">
          <div class="wm-toolbar-group">
            <span class="wm-toolbar-label">抄表日期</span>
            <el-date-picker v-model="readingDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" @change="changeReadingDate" style="width:160px" size="small" />
          </div>
          <div class="wm-toolbar-group wm-reading-progress-inline">
            <span>已完成 <strong>{{ completedCount }}</strong> / {{ totalCount }}</span>
            <el-progress
              :percentage="progressPercent"
              :stroke-width="6"
              :show-text="false"
              class="wm-reading-progress-bar"
              color="#0284C7"
            />
            <span class="font-mono">{{ progressPercent }}%</span>
          </div>
          <div class="wm-toolbar-group">
            <el-button size="small" @click="exportTemplate">导出空白模板</el-button>
            <el-button size="small" @click="exportHistoryTemplate">历史表底模板</el-button>
            <el-button size="small" type="warning" @click="triggerReadingImport">导入抄表</el-button>
            <el-button size="small" type="warning" plain @click="triggerHistoryImport">导入历史表底</el-button>
            <el-button size="small" @click="triggerRegisterImport">导入住户</el-button>
          </div>
          <div class="wm-toolbar-spacer"></div>
          <el-input v-model="tableKeyword" placeholder="搜索户名/表号" size="small" class="wm-reading-search" clearable />
        </section>

        <input ref="readingImportInput" class="wm-hidden-file-input" type="file" accept=".xlsx" @change="onReadingImportFileChange" />
        <input ref="historyImportInput" class="wm-hidden-file-input" type="file" accept=".xlsx" @change="onHistoryImportFileChange" />
        <input ref="registerImportInput" class="wm-hidden-file-input" type="file" accept=".xlsx" @change="onRegisterImportFileChange" />

        <section class="wm-panel wm-reading-table-panel">
          <div class="wm-table-shell wm-reading-table-shell">
            <el-table
              v-if="tableData.length > 0"
              :data="pagedTableData"
              border
              stripe
              style="width:100%"
              :height="tableHeight"
              :style="{ minWidth: '1320px' }"
              :row-class-name="rowClassName"
              @row-click="onRowClick"
              highlight-current-row
              :current-row-key="selectedHousehold?.id || null"
              @selection-change="onSelectionChange"
            >
              <el-table-column type="selection" width="42" />
              <el-table-column type="index" label="序号" fixed="left" width="60" />
              <el-table-column prop="householdName" label="户主" width="120" resizable />
              <el-table-column prop="waterMeterId" label="表号" width="120" resizable />
              <el-table-column prop="previousReading" label="上次表底" width="90" resizable />
              <el-table-column label="本次表底" align="right" width="140" resizable>
                <template #default="{ row }">
                  <el-input
                    v-model="row.currentReading"
                    placeholder="输入"
                    size="small"
                    :data-water-meter-id="row.waterMeterId"
                    :class="['wm-current-reading-input', { 'is-error': row.isAbnormal }]"
                    @change="calcRow(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="用水量" align="right" width="100" resizable>
                <template #default="{ row }"><span class="font-mono">{{ row.usageAmount != null ? row.usageAmount : '-' }}</span></template>
              </el-table-column>
              <el-table-column label="水费" align="right" width="100" resizable>
                <template #default="{ row }"><span class="font-mono">{{ row.waterCharge != null ? row.waterCharge : '-' }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="78" resizable>
                <template #default="{ row }">
                  <el-tag v-if="isRowDirty(row)" type="warning" size="small">未保存</el-tag>
                  <el-tag v-else-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
                  <el-tag v-else-if="hasValidCurrentReading(row)" type="success" size="small">正常</el-tag>
                  <el-tag v-else type="info" size="small">-</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="phone" label="电话" width="120" resizable />
              <el-table-column label="计费用水量" width="120" resizable>
                <template #default="{ row }">
                  <el-input-number v-model="row.chargeableUsage" :precision="2" :min="0" size="small" controls-position="right" style="width:100px" @change="calcCharge(row)" />
                </template>
              </el-table-column>
              <el-table-column label="水价" width="80" resizable>
                <template #default>{{ waterPrice }}</template>
              </el-table-column>
              <el-table-column label="备注" width="140" resizable>
                <template #default="{ row }">
                  <el-input v-model="row.note" placeholder="备注" size="small" />
                </template>
              </el-table-column>
              <el-table-column prop="abnormalReason" label="异常原因" width="160" resizable show-overflow-tooltip />
            </el-table>

            <el-empty v-else :image-size="84" class="wm-empty">
              <template #description>
                <p class="text-gray-500 text-sm">该村组暂无住户</p>
                <p class="text-gray-400 text-xs mt-1">点击下方按钮新增第一个住户</p>
              </template>
              <el-button type="primary" @click="addNewHousehold">新增住户</el-button>
            </el-empty>

            <div v-if="tableData.length > 0" class="wm-reading-pagination">
              <el-pagination v-model:current-page="tablePage" v-model:page-size="tablePageSize" :page-sizes="[10,20,50,100]" :total="filteredTableData.length" layout="total,sizes,prev,pager,next" size="small" />
            </div>
          </div>
        </section>

        <section class="wm-toolbar wm-household-batch-actions">
          <strong>住户资料批量操作</strong>
          <span class="wm-chip">已选 {{ selectedHouseholdIds.length }} 户</span>
          <span class="wm-batch-label">修改所选住户的村组</span>
          <el-select v-model="batchVillage" placeholder="选择村组" size="small" class="wm-batch-select" filterable allow-create>
            <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
          </el-select>
          <el-button size="small" type="primary" plain @click="applyBatchVillage" :disabled="!batchVillage || selectedHouseholdIds.length===0">应用</el-button>
          <div class="wm-toolbar-spacer"></div>
          <el-button size="small" type="danger" @click="batchDeleteHouseholds" :disabled="selectedHouseholdIds.length===0">
            删除或停用所选住户
          </el-button>
        </section>

        <section class="batch-actions">
          <span class="wm-chip" :class="{ 'is-pending': unsavedCount > 0 }">未保存 {{ unsavedCount }} 户</span>
          <span class="wm-save-hint">保存后将生成或更新对应月份的水费账单</span>
          <div class="wm-toolbar-spacer"></div>
          <el-button size="small" type="primary" @click="batchSave" :loading="saving" :disabled="unsavedCount===0">保存本次抄表</el-button>
        </section>

        <el-dialog v-model="importResultVisible" :title="importResultTitle" width="640px" :close-on-click-modal="false">
          <div class="mb-4">
            <div class="flex gap-4 text-sm">
              <span>共处理 <strong>{{ importResult.total }}</strong> 条</span>
              <span class="text-emerald-600">成功 <strong>{{ importResult.success }}</strong> 条</span>
              <span v-if="importResult.abnormal" class="text-orange-500">异常 <strong>{{ importResult.abnormal }}</strong> 条</span>
              <span class="text-red-500">失败 <strong>{{ importResult.fail }}</strong> 条</span>
              <span class="text-gray-400">跳过 <strong>{{ importResult.skip }}</strong> 条</span>
            </div>
          </div>
          <el-table :data="importResult.details" max-height="400" border size="small">
            <el-table-column prop="index" label="序号" width="70" />
            <el-table-column prop="householdName" label="户名" width="110" />
            <el-table-column prop="waterMeterId" label="水表编号" width="130" />
            <el-table-column prop="villageName" label="村名" width="110" />
            <el-table-column v-if="importResultMode === 'history'" prop="readingDate" label="抄表日期" width="120" />
            <el-table-column prop="previousReading" label="上次表底" width="110" align="right" />
            <el-table-column prop="currentReading" label="本次表底" width="110" align="right" />
            <el-table-column v-if="importResultMode === 'reading'" prop="waterPrice" label="水价" width="90" align="right" />
            <el-table-column v-if="importResultMode === 'reading'" prop="waterCharge" label="水费" width="100" align="right" />
            <el-table-column v-if="importResultMode === 'history'" prop="usageAmount" label="用水量" width="100" align="right" />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'success'" type="success" size="small">成功</el-tag>
                <el-tag v-else-if="row.status === 'abnormal'" type="warning" size="small">异常</el-tag>
                <el-tag v-else-if="row.status === 'skip'" type="info" size="small">跳过</el-tag>
                <el-tag v-else type="danger" size="small">失败</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
          </el-table>
          <template #footer>
            <el-button v-if="pendingImportFile" type="primary" :loading="confirmingImport" @click="confirmPreviewImport">确认导入</el-button>
            <el-button @click="importResultVisible = false">关闭</el-button>
          </template>
        </el-dialog>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Menu } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { onBeforeRouteLeave } from 'vue-router'
import { householdApi, readingApi, paymentApi } from '@/api'
import { formatLocalDate } from '@/utils/localDate'
import { createEditableSnapshot, dirtyRows, isRowDirty } from '@/utils/dirtyRows'
import {
  applyReadingSaveResult,
  buildReadingSaveItems,
  hasValidCurrentReading,
  summarizeHouseholdRemovals
} from '@/utils/readingSave'

const route = useRoute()
const router = useRouter()
const readingDate = ref(formatLocalDate())
const loadedReadingDate = ref(readingDate.value)
const selectedVillage = ref('')
const sidebarOpen = ref(false)
const allVillages = ref([])
const filterKeyword = ref('')
const waterPrice = ref(1.8)
const abnormalThreshold = ref(100)
const completedCount = computed(() => {
  return tableData.value.filter(hasValidCurrentReading).length
})
const totalCount = computed(() => tableData.value.length)
const unsavedCount = computed(() => dirtyRows(tableData.value).length)
const progressPercent = computed(() => {
  return totalCount.value > 0 ? Math.round((completedCount.value / totalCount.value) * 100) : 0
})
const saving = ref(false)
const tableKeyword = ref('')
const tablePage = ref(1)
const tablePageSize = ref(20)
const tableHeight = ref(420)

const householdList = ref([])
const selectedHousehold = ref(null)
const householdForm = reactive({ id: null, householdName: '', waterMeterId: '', phone: '', villageName: '' })
const savingHousehold = ref(false)

const tableData = ref([])
const recentReadingMeters = ref([])
const selectedHouseholdIds = ref([])
const batchVillage = ref('')
const readingImportInput = ref(null)
const historyImportInput = ref(null)
const registerImportInput = ref(null)
const importResultVisible = ref(false)
const importResultTitle = ref('导入结果')
const importResultMode = ref('reading')
const pendingImportFile = ref(null)
const pendingImportMode = ref('')
const confirmingImport = ref(false)
const importResult = ref({
  total: 0,
  success: 0,
  fail: 0,
  skip: 0,
  abnormal: 0,
  details: []
})

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

const unpaidHouseholdShortcuts = computed(() => tableData.value.filter(row => row.hasUnpaidBill).slice(0, 6))
const recentReadingShortcuts = computed(() => recentReadingMeters.value
  .map(waterMeterId => tableData.value.find(row => row.waterMeterId === waterMeterId))
  .filter(Boolean)
  .slice(0, 6))

function rowClassName({ row }) {
  const classes = []
  if (row.isAbnormal) classes.push('row-abnormal')
  if (selectedHousehold.value?.id && selectedHousehold.value.id === row.id) classes.push('is-selected-row')
  return classes.join(' ')
}

function updateTableHeight() {
  const shell = document.querySelector('.wm-reading-table-shell')
  if (!shell) return
  const pagination = shell.querySelector('.wm-reading-pagination')
  const paginationHeight = pagination ? pagination.getBoundingClientRect().height : 0
  tableHeight.value = Math.max(320, Math.floor(shell.clientHeight - paginationHeight - 6))
}

async function selectVillage(v) {
  if (v === selectedVillage.value) return
  if (!(await confirmDiscardChanges())) return
  selectedVillage.value = v
  filterKeyword.value = ''
  tableKeyword.value = ''
  tablePage.value = 1
  selectedHousehold.value = null
  sidebarOpen.value = false
  loadHouseholdList()
  loadTable()
}

async function changeReadingDate(value) {
  if (await confirmDiscardChanges()) {
    loadedReadingDate.value = value
    await loadTable()
  } else {
    readingDate.value = loadedReadingDate.value
  }
}

async function loadAllVillages() {
  try {
    const r = await householdApi.list({ page: 0, size: 10000 })
    allVillages.value = [...new Set((r?.content || []).map(h => h.villageName).filter(Boolean))].sort()
  } catch (error) {
    console.warn('加载村组失败', error)
    ElMessage.warning('加载村组列表失败，请检查网络连接')
  }
}

async function loadHouseholdList() {
  try {
    const params = { page: 0, size: 10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    householdList.value = r?.content || []
  } catch (error) {
    console.warn('加载住户列表失败', error)
    ElMessage.warning('加载住户列表失败，请检查网络连接')
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
  sidebarOpen.value = false
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
  } catch (error) {
    console.warn('保存住户失败', error)
  } finally {
    savingHousehold.value = false
  }
}

async function deleteHousehold() {
  if (!householdForm.id) return
  try {
    await ElMessageBox.confirm('确认处理该住户吗？无历史数据时删除，存在抄表或账务历史时将停用并保留记录。', '确认删除或停用', { type: 'warning' })
  } catch (error) {
    console.warn('取消删除住户:', error)
    return
  }
  try {
    const result = await householdApi.delete(householdForm.id)
    ElMessage.success(summarizeHouseholdRemovals(result).message)
    selectedHousehold.value = null
    loadHouseholdList()
    loadTable()
    loadAllVillages()
  } catch (error) {
    console.warn('删除住户失败', error)
  }
}

async function loadTable() {
  let households = []
  try {
    const params = { page: 0, size: 10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    households = r?.content || []
  } catch (error) {
    console.warn('加载抄表住户失败', error)
    ElMessage.warning('加载抄表住户失败，请检查网络连接')
    households = []
  }

  let readingsMap = {}
  try {
    const readings = await readingApi.getByDate({ readingDate: readingDate.value, villageName: selectedVillage.value })
    if (readings?.length) readings.forEach(r => { readingsMap[r.waterMeterId] = r })
  } catch (error) {
    console.warn('加载当天抄表记录失败', error)
    ElMessage.warning('加载当天抄表记录失败，请检查网络连接')
  }

  const unpaidMeterIds = new Set()
  try {
    const date = new Date(`${readingDate.value}T00:00:00`)
    const params = { billYear: date.getFullYear(), billMonth: date.getMonth() + 1 }
    if (selectedVillage.value) params.villageName = selectedVillage.value
    const pendingBills = await paymentApi.listPendingWater(params)
    ;(pendingBills || []).forEach(row => {
      if (row.waterMeterId) unpaidMeterIds.add(row.waterMeterId)
    })
  } catch (error) {
    console.warn('加载未缴水费列表失败', error)
  }

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
        hasUnpaidBill: unpaidMeterIds.has(h.waterMeterId),
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
      hasUnpaidBill: unpaidMeterIds.has(h.waterMeterId),
      isAbnormal: false,
      abnormalReason: ''
    }
  }).sort((a, b) => Number(b.hasUnpaidBill) - Number(a.hasUnpaidBill))
  tableData.value.forEach(row => { row.originalSnapshot = createEditableSnapshot(row) })
  loadedReadingDate.value = readingDate.value
  const hasRouteTarget = applyRouteTarget()
  nextTick(() => {
    updateTableHeight()
    if (hasRouteTarget) {
      focusReadingInput(route.query.waterMeterId)
    } else {
      focusFirstEmptyReading()
    }
  })
}

function applyRouteTarget() {
  const waterMeterId = route.query.waterMeterId
  if (!waterMeterId) return false
  const target = tableData.value.find(row => row.waterMeterId === waterMeterId)
  if (!target) return false
  tableKeyword.value = waterMeterId
  tablePage.value = 1
  onSelectHousehold(target)
  return true
}

function rememberRecentReading(row) {
  recentReadingMeters.value = [
    row.waterMeterId,
    ...recentReadingMeters.value.filter(waterMeterId => waterMeterId !== row.waterMeterId)
  ].slice(0, 8)
}

function forgetRecentReading(row) {
  recentReadingMeters.value = recentReadingMeters.value.filter(waterMeterId => waterMeterId !== row.waterMeterId)
}

function jumpToReadingRow(row) {
  tableKeyword.value = row.waterMeterId
  tablePage.value = 1
  onSelectHousehold(row)
  nextTick(() => focusReadingInput(row.waterMeterId))
  sidebarOpen.value = false
}

function focusReadingInput(waterMeterId) {
  const inputWrap = Array.from(document.querySelectorAll('.wm-current-reading-input'))
    .find(element => element.dataset.waterMeterId === String(waterMeterId))
  const input = inputWrap?.querySelector('input')
  input?.focus()
  input?.select?.()
}

function focusFirstEmptyReading() {
  const index = filteredTableData.value.findIndex(item => !item.currentReading)
  if (index < 0) return
  const row = filteredTableData.value[index]
  tablePage.value = Math.floor(index / tablePageSize.value) + 1
  onSelectHousehold(row)
  nextTick(() => focusReadingInput(row.waterMeterId))
}

function onRowClick(row) { onSelectHousehold(row) }
function onSelectionChange(rows) {
  selectedHouseholdIds.value = rows.map(r => r.id).filter(Boolean)
  nextTick(updateTableHeight)
}

async function applyBatchVillage() {
  if (!batchVillage.value || !selectedHouseholdIds.value.length) return
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${selectedHouseholdIds.value.length} 户移动到「${batchVillage.value}」吗？`,
      '确认批量改村',
      { type: 'warning' }
    )
    await householdApi.batchUpdateVillage(selectedHouseholdIds.value, batchVillage.value)
    ElMessage.success(`已更新 ${selectedHouseholdIds.value.length} 户到 ${batchVillage.value}`)
    batchVillage.value = ''
    await Promise.all([loadTable(), loadHouseholdList(), loadAllVillages()])
  } catch (error) {
    if (error !== 'cancel') console.warn('批量改村失败', error)
  }
}

function refreshReadingsPage() {
  loadTable()
  loadHouseholdList()
  loadAllVillages()
}

async function batchDeleteHouseholds() {
  if (!selectedHouseholdIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认处理选中的 ${selectedHouseholdIds.value.length} 户吗？无历史数据时删除，存在历史数据时停用并保留记录。`, '确认批量删除或停用', { type: 'warning' })
  } catch (error) {
    console.warn('取消批量删除住户:', error)
    return
  }
  try {
    const results = await householdApi.batchDelete(selectedHouseholdIds.value)
    const summary = summarizeHouseholdRemovals(results)
    ElNotification({
      title: '处理完成',
      message: summary.message,
      type: 'success',
      duration: 3000
    })
    selectedHouseholdIds.value = []
    loadTable()
    loadHouseholdList()
    loadAllVillages()
  } catch (error) {
    console.warn('批量删除住户失败', error)
  }
}

function calcRow(row) {
  if (!row.currentReading || isNaN(row.currentReading)) {
    row.usageAmount = null
    row.chargeableUsage = null
    row.waterCharge = null
    row.isAbnormal = false
    row.abnormalReason = ''
    forgetRecentReading(row)
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
  rememberRecentReading(row)
}

function calcCharge(row) {
  const c = row.chargeableUsage
  row.waterCharge = c != null && !isNaN(c) && c >= 0 ? (c * waterPrice.value).toFixed(2) : null
}

function triggerReadingImport() {
  readingImportInput.value?.click()
}

function triggerHistoryImport() {
  historyImportInput.value?.click()
}

function triggerRegisterImport() {
  registerImportInput.value?.click()
}

function buildImportFormData(file) {
  const fd = new FormData()
  fd.append('file', file)
  return fd
}

async function onReadingImportFileChange(event) {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  const fd = buildImportFormData(file)
  fd.append('readingDate', readingDate.value)
  try {
    const result = await readingApi.previewImportReadings(fd)
    importResultTitle.value = '抄表导入预览'
    importResultMode.value = 'reading'
    importResult.value = normalizeReadingImportResult(result || {})
    pendingImportFile.value = file
    pendingImportMode.value = 'reading'
    importResultVisible.value = true
    ElMessage.success('预览完成，请确认后导入')
  } catch (error) {
    console.warn('抄表数据预览失败', error)
  }
}

async function onHistoryImportFileChange(event) {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  try {
    const result = await readingApi.previewHistoryReadings(buildImportFormData(file))
    importResultTitle.value = '历史表底导入预览'
    importResultMode.value = 'history'
    importResult.value = normalizeReadingImportResult(result || {})
    pendingImportFile.value = file
    pendingImportMode.value = 'history'
    importResultVisible.value = true
    ElMessage.success('预览完成，请确认后导入')
  } catch (error) {
    console.warn('历史表底预览失败', error)
  }
}

async function confirmPreviewImport() {
  if (!pendingImportFile.value) return
  confirmingImport.value = true
  try {
    let result
    if (pendingImportMode.value === 'history') {
      result = await readingApi.importHistoryReadings(buildImportFormData(pendingImportFile.value))
      importResultTitle.value = '历史表底导入结果'
      importResultMode.value = 'history'
    } else {
      const fd = buildImportFormData(pendingImportFile.value)
      fd.append('readingDate', readingDate.value)
      result = await readingApi.importReadings(fd)
      importResultTitle.value = '抄表导入结果'
      importResultMode.value = 'reading'
    }
    importResult.value = normalizeReadingImportResult(result || {})
    pendingImportFile.value = null
    pendingImportMode.value = ''
    ElNotification({
      title: '导入完成',
      message: `成功 ${importResult.value.success} 条，异常 ${importResult.value.abnormal} 条，失败 ${importResult.value.fail} 条，跳过 ${importResult.value.skip} 条`,
      type: importResult.value.fail > 0 ? 'warning' : 'success',
      duration: 4000
    })
    await loadTable()
  } catch (error) {
    console.warn('导入失败', error)
  } finally {
    confirmingImport.value = false
  }
}

async function onRegisterImportFileChange(event) {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  const fd = new FormData()
  fd.append('file', file)
  try {
    const result = await householdApi.importFromRegister(fd)
    importResultTitle.value = '住户导入结果'
    importResultMode.value = 'register'
    pendingImportFile.value = null
    pendingImportMode.value = ''
    importResult.value = normalizeRegisterImportResult(result || {})
    importResultVisible.value = true
    ElNotification({
      title: '住户导入完成',
      message: `新增 ${importResult.value.success} 户，跳过 ${importResult.value.skip} 户，错误 ${importResult.value.fail} 条`,
      type: importResult.value.fail ? 'warning' : 'success',
      duration: 4000
    })
    await loadTable()
    await loadHouseholdList()
    await loadAllVillages()
  } catch (error) {
    console.warn('住户信息导入失败', error)
  }
}

function normalizeReadingImportResult(result) {
  if (Array.isArray(result.details)) {
    return {
      total: Number(result.total || result.details.length || 0),
      success: Number(result.success || 0),
      fail: Number(result.fail || 0),
      skip: Number(result.skip || 0),
      abnormal: Number(result.abnormal || 0),
      details: result.details
    }
  }
  const errors = Array.isArray(result.errors) ? result.errors : []
  const success = Number(result.total || 0)
  const abnormal = Number(result.abnormal || 0)
  const fail = errors.length
  const details = errors.map((message, index) => ({
    type: '抄表',
    waterMeterId: extractWaterMeterId(message),
    status: 'fail',
    message: String(message || `第 ${index + 1} 条处理失败`)
  }))

  if (!details.length && success > 0) {
    details.push({
      type: '抄表',
      waterMeterId: '-',
      status: 'success',
      message: abnormal > 0 ? `成功导入 ${success} 条，其中 ${abnormal} 条被标记为异常` : `成功导入 ${success} 条抄表记录`
    })
  }

  return {
    total: success + fail,
    success,
    fail,
    skip: 0,
    abnormal,
    details
  }
}

function normalizeRegisterImportResult(result) {
  const errors = Array.isArray(result.errors) ? result.errors : []
  const success = Number(result.inserted || 0)
  const skip = Number(result.skipped || 0)
  const details = errors.map((message, index) => ({
    type: '住户',
    waterMeterId: extractWaterMeterId(message),
    status: String(message).startsWith('跳过') ? 'skip' : 'fail',
    message: String(message || `第 ${index + 1} 条处理失败`)
  }))

  if (!details.length && success > 0) {
    details.push({
      type: '住户',
      waterMeterId: '-',
      status: 'success',
      message: `成功导入 ${success} 户`
    })
  }

  return {
    total: success + errors.length,
    success,
    fail: Math.max(0, errors.length - skip),
    skip,
    abnormal: 0,
    details
  }
}

function extractWaterMeterId(message) {
  const text = String(message || '')
  const explicitMatch = text.match(/表号\s*([^，,\s]+)/)
  if (explicitMatch) return explicitMatch[1]
  const prefixMatch = text.match(/^([^:：]+)[:：]/)
  return prefixMatch ? prefixMatch[1] : '-'
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
  } catch (error) {
    console.warn('下载抄表模板失败', error)
  }
}

async function batchSave() {
  const changedRows = dirtyRows(tableData.value)
  const items = buildReadingSaveItems(tableData.value)
  if (!items.length) {
    ElMessage.warning(changedRows.length ? '修改行需要填写有效表底' : '没有需要保存的修改')
    return
  }
  saving.value = true
  try {
    const r = await readingApi.batchSave(items, readingDate.value)
    const savedCount = r?.total ?? 0
    const abnormalCount = r?.abnormal ?? 0
    const failedCount = r?.fail ?? 0
    applyReadingSaveResult(tableData.value, r)
    const failedDetails = (r?.details || []).filter(detail => detail.status === 'fail')
    ElNotification({
      title: failedCount ? (savedCount ? '部分保存失败' : '保存失败') : '保存成功',
      message: failedCount
        ? `成功 ${savedCount} 条，异常 ${abnormalCount} 条，失败 ${failedCount} 条：${failedDetails.map(detail => `${detail.householdName || detail.waterMeterId}（${detail.message}）`).join('；')}`
        : `共更新 ${savedCount} 条抄表记录，其中异常 ${abnormalCount} 条`,
      type: failedCount ? (savedCount ? 'warning' : 'error') : 'success',
      duration: failedCount ? 8000 : 3000
    })
    if (savedCount > 0 && !failedCount) {
      try {
        await ElMessageBox.confirm('本次抄表已保存并生成或更新账单，是否前往缴费页面查看？', '保存完成', {
          type: 'success',
          confirmButtonText: '前往缴费',
          cancelButtonText: '继续抄表'
        })
        await router.push('/billing')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') console.warn('前往缴费页面失败', error)
      }
    }
  } catch (error) {
    console.warn('保存本次抄表失败', error)
    ElMessage.error('保存本次抄表失败：' + (error?.message || error))
  } finally {
    saving.value = false
  }
}

async function exportHistoryTemplate() {
  const params = {}
  if (selectedVillage.value) params.villageNames = selectedVillage.value
  try {
    const blob = await readingApi.exportHistoryTemplate(params)
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = '历史抄表模板.xlsx'
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('历史表底模板已下载')
  } catch (error) {
    console.warn('下载历史表底模板失败', error)
  }
}

onMounted(async () => {
  try {
    const c = await readingApi.getConfig()
    waterPrice.value = c.waterPrice || 1.8
    abnormalThreshold.value = c.abnormalThreshold || 100
  } catch (error) {
    console.warn('加载抄表配置失败', error)
  }
  await loadAllVillages()
  await loadTable()
  nextTick(updateTableHeight)
  window.addEventListener('resize', updateTableHeight, { passive: true })
  window.addEventListener('wm-refresh', refreshReadingsPage)
  window.addEventListener('wm-new', addNewHousehold)
  window.addEventListener('beforeunload', handleBeforeUnload)
})

function hasUnsavedChanges() {
  return dirtyRows(tableData.value).length > 0
}

async function confirmDiscardChanges() {
  if (!hasUnsavedChanges()) return true
  try {
    await ElMessageBox.confirm('当前有未保存的抄表修改，离开后将丢失。是否继续？', '未保存修改', {
      type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑'
    })
    return true
  } catch {
    return false
  }
}

function handleBeforeUnload(event) {
  if (!hasUnsavedChanges()) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => confirmDiscardChanges())

watch([selectedVillage, tablePage, tablePageSize], () => {
  nextTick(updateTableHeight)
})

watch(() => route.query.waterMeterId, async () => {
  if (route.path === '/readings') await loadTable()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableHeight)
  window.removeEventListener('wm-refresh', refreshReadingsPage)
  window.removeEventListener('wm-new', addNewHousehold)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.wm-click-tag {
  cursor: pointer;
}

.wm-readings-page {
  max-width: none;
  width: 100%;
  margin: 0;
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 8px;
}

.wm-reading-workspace {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 6px;
  align-items: stretch;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.wm-reading-sidebar {
  position: sticky;
  top: 0;
  max-height: calc(100dvh - 72px);
  overflow: auto;
  min-height: 0;
}

.wm-reading-sidebar-toggle,
.wm-reading-sidebar-mask {
  display: none;
}

.wm-reading-sidebar :deep(.wm-panel-body),
.wm-reading-sidebar .wm-panel-body {
  padding: 12px;
}

.wm-reading-sidebar :deep(.el-form-item) {
  margin-bottom: 10px;
}

.wm-reading-quick-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wm-reading-quick-title {
  color: var(--wm-text-2);
  font-size: 12px;
  font-weight: 600;
}

.wm-reading-quick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 30px;
  padding: 5px 8px;
  border: 1px solid var(--wm-border);
  border-radius: 8px;
  background: var(--wm-surface);
  color: var(--wm-text);
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

.wm-reading-quick-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wm-reading-quick-item small {
  color: var(--wm-text-2);
  font-size: 11px;
}

.wm-reading-quick-item:hover {
  border-color: rgba(2, 132, 199, 0.35);
  background: #e0f2fe;
}

.wm-reading-main {
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wm-reading-toolbar {
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
}

.wm-reading-progress-inline {
  min-width: 260px;
  color: var(--wm-text-2);
  font-size: 12px;
  white-space: nowrap;
}

.wm-reading-progress-inline strong {
  color: var(--wm-primary);
}

.wm-reading-search {
  width: 190px;
}

.wm-hidden-file-input {
  display: none;
}

.wm-reading-table-panel {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.wm-reading-progress-bar {
  flex: 1;
  min-width: 110px;
}

.batch-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 8px;
  min-height: 42px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border: 1px solid #dbe7ee;
  border-radius: 10px;
  padding: 6px 10px;
  z-index: 10;
}

.wm-household-batch-actions {
  flex: 0 0 auto;
  min-height: 40px;
  padding: 6px 10px;
  gap: 8px;
}

.wm-household-batch-actions strong {
  font-size: 13px;
  white-space: nowrap;
}

.wm-save-hint {
  color: var(--wm-text-2);
  font-size: 12px;
  white-space: nowrap;
}

.wm-chip.is-pending {
  border-color: #f59e0b;
  background: #fffbeb;
  color: #b45309;
}

.wm-batch-label {
  color: var(--wm-text-2);
  font-size: 12px;
  white-space: nowrap;
}

.wm-batch-select {
  width: 150px;
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
  background-color: #e0f2fe !important;
}

.wm-reading-table-shell :deep(.el-table__body tr.is-selected-row > td) {
  background-color: #bae6fd !important;
  box-shadow: inset 0 0 0 9999px rgba(2, 132, 199, 0.16);
  font-weight: 600;
}

.wm-reading-table-shell :deep(.el-table__body tr.is-selected-row:hover > td) {
  background-color: #7dd3fc !important;
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
    grid-template-columns: 212px minmax(0, 1fr);
  }

  .wm-reading-progress-inline {
    min-width: 210px;
  }

  .wm-reading-search {
    width: 160px;
  }
}

@media (max-width: 1024px) {
  .wm-readings-page {
    overflow: visible;
  }

  .wm-reading-workspace {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .wm-reading-sidebar {
    position: fixed;
    top: 84px;
    bottom: 12px;
    left: 12px;
    z-index: 40;
    width: min(320px, calc(100vw - 24px));
    max-height: none;
    transform: translateX(calc(-100% - 24px));
    transition: transform 0.2s ease;
  }

  .wm-reading-sidebar.is-open {
    transform: translateX(0);
  }

  .wm-reading-sidebar-toggle {
    position: fixed;
    right: 16px;
    bottom: 72px;
    z-index: 42;
    display: inline-flex;
    box-shadow: var(--wm-shadow);
  }

  .wm-reading-sidebar-mask {
    position: fixed;
    inset: 0;
    z-index: 39;
    display: block;
    background: rgba(15, 23, 42, 0.32);
  }

  .wm-reading-toolbar,
  .wm-household-batch-actions,
  .batch-actions {
    flex-wrap: wrap;
  }

  .wm-reading-search,
  .wm-batch-select {
    width: 100%;
  }

  .wm-reading-table-shell {
    max-height: none;
  }
}

@media (max-width: 768px) {
  .wm-reading-table-shell :deep(.el-table) {
    min-width: 1120px;
  }
}
</style>
