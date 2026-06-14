<template>
  <div style="display:flex;gap:16px;height:calc(100vh - 140px)">

    <!-- ========== 左侧面板 300px ========== -->
    <div style="width:300px;flex-shrink:0;background:#fff;padding:14px;border-radius:8px;overflow-y:auto;display:flex;flex-direction:column;gap:10px">

      <!-- 村组快捷标签 -->
      <div style="display:flex;flex-wrap:wrap;gap:6px">
        <el-tag v-for="v in allVillages" :key="v" :type="selectedVillage===v?'primary':''"
          style="cursor:pointer" @click="selectVillage(v)" size="small">{{ v }}</el-tag>
        <el-tag :type="selectedVillage===''?'primary':''" style="cursor:pointer"
          @click="selectVillage('')" size="small">全部</el-tag>
      </div>

      <!-- 搜索 + 操作 -->
      <el-input v-model="filterKeyword" placeholder="搜索户名/表号" size="small" clearable
        @input="onSearchChange" @clear="onSearchChange" />
      <div style="display:flex;gap:6px">
        <el-button size="small" type="danger" @click="deleteVillage" :disabled="!selectedVillage">🗑 删除该村</el-button>
        <el-dropdown trigger="click" @command="handleExportCommand" style="margin-left:auto">
          <el-button size="small">📥 导出 ▼</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="current">导出当前村组</el-dropdown-item>
              <el-dropdown-item command="all">导出全部村组</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 村民列表 -->
      <div style="flex:1;overflow-y:auto;border:1px solid #ebeef5;border-radius:6px;padding:6px;min-height:120px">
        <div v-if="filteredHouseholdList.length===0" style="color:#999;text-align:center;padding:20px">无数据</div>
        <div v-for="h in filteredHouseholdList" :key="h.id"
          :style="{padding:'6px 8px',cursor:'pointer',borderRadius:'4px',marginBottom:'2px',
            background: selectedHousehold&&selectedHousehold.id===h.id?'#ecf5ff':''}"
          @click="onSelectHousehold(h)">
          👤 {{ h.householdName }} <span style="color:#999;font-size:12px">[{{ h.waterMeterId }}]</span>
        </div>
      </div>

      <!-- 选中户编辑表单 -->
      <div v-if="selectedHousehold" style="border:1px solid #ebeef5;border-radius:6px;padding:10px">
        <div style="font-weight:500;margin-bottom:8px;font-size:13px;color:#303133">编辑村民信息</div>
        <el-form :model="householdForm" label-width="70px" size="small">
          <el-form-item label="户主"><el-input v-model="householdForm.householdName" /></el-form-item>
          <el-form-item label="表号"><el-input v-model="householdForm.waterMeterId" :disabled="!!householdForm.id" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="householdForm.phone" /></el-form-item>
          <el-form-item label="村组">
            <el-select v-model="householdForm.villageName" filterable allow-create style="width:100%">
              <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
            </el-select>
          </el-form-item>
          <div style="display:flex;gap:6px">
            <el-button type="primary" size="small" @click="saveHousehold" :loading="savingHousehold">💾 保存</el-button>
            <el-button v-if="householdForm.id" type="danger" size="small" @click="deleteHousehold">🗑 永久删除</el-button>
          </div>
        </el-form>
      </div>

      <!-- 底部按钮 -->
      <el-button size="small" @click="addNewHousehold">➕ 新增村民</el-button>
    </div>

    <!-- ========== 右侧面板 ========== -->
    <div style="flex:1;display:flex;flex-direction:column;gap:12px;overflow:hidden">

      <!-- 操作栏 -->
      <div style="background:#fff;padding:12px 16px;border-radius:8px;display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <span style="font-weight:500;font-size:13px">抄表日期：</span>
        <el-date-picker v-model="readingDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD"
          @change="loadTable" style="width:160px" size="small" />
        <el-button size="small" @click="exportTemplate">📥 导出空白模板</el-button>
        <el-dropdown trigger="click" @command="handleImportCommand">
          <el-button size="small" type="warning">📤 导入模板 ▼</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="readings">📤 导入抄表数据</el-dropdown-item>
              <el-dropdown-item command="register">📥 导入村民信息</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" type="primary" @click="batchSave" :loading="saving">💾 批量保存</el-button>
        <el-button size="small" type="danger" @click="batchDeleteHouseholds" :disabled="selectedHouseholdIds.length===0">
          🗑 批量删除({{selectedHouseholdIds.length}})
        </el-button>
      </div>

      <!-- 批量改村组 -->
      <div v-if="selectedHouseholdIds.length>0" style="background:#fff;padding:6px 16px;border-radius:8px;display:flex;gap:10px;align-items:center">
        <span style="color:#409EFF;font-size:13px">已选 {{ selectedHouseholdIds.length }} 户</span>
        <span style="font-size:13px">批量改村组：</span>
        <el-select v-model="batchVillage" placeholder="选择村组" size="small" style="width:140px" filterable allow-create>
          <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
        </el-select>
        <el-button size="small" type="primary" @click="applyBatchVillage" :disabled="!batchVillage">应用</el-button>
      </div>

      <!-- 表格上方：更多列 + 搜索 -->
      <div style="display:flex;gap:10px;align-items:center">
        <el-checkbox v-model="showMoreColumns" size="small">更多列</el-checkbox>
        <el-input v-model="tableKeyword" placeholder="搜索户名/表号" size="small" style="width:200px;margin-left:auto" clearable
          @input="filterTable" @clear="filterTable" />
      </div>

      <!-- 表格 -->
      <div style="flex:1;background:#fff;padding:12px;border-radius:8px;display:flex;flex-direction:column;overflow:hidden">
        <el-table :data="pagedTableData" border stripe style="width:100%;flex:1"
          @row-click="onRowClick" highlight-current-row @selection-change="onSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column type="index" label="#" width="45" />
          <el-table-column prop="householdName" label="户主" width="80" />
          <el-table-column prop="waterMeterId" label="表号" width="110" />
          <el-table-column prop="previousReading" label="上月表底" width="85" />
          <el-table-column label="本月表底" width="120">
            <template #default="{ row }">
              <el-input v-model="row.currentReading" placeholder="输入" size="small" @change="calcRow(row)"
                :class="{ 'is-error': row.isAbnormal }" />
            </template>
          </el-table-column>
          <el-table-column label="用水量" width="75">
            <template #default="{ row }">{{ row.usageAmount != null ? row.usageAmount : '-' }}</template>
          </el-table-column>
          <el-table-column label="水费" width="75">
            <template #default="{ row }">{{ row.waterCharge != null ? row.waterCharge : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="65">
            <template #default="{ row }">
              <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
              <el-tag v-else-if="row.currentReading" type="success" size="small">正常</el-tag>
              <el-tag v-else type="info" size="small">-</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showMoreColumns" prop="phone" label="电话" width="120" />
          <el-table-column v-if="showMoreColumns" label="计费用水量" width="115">
            <template #default="{ row }">
              <el-input-number v-model="row.chargeableUsage" :precision="2" :min="0" size="small"
                controls-position="right" style="width:100px" @change="calcCharge(row)" />
            </template>
          </el-table-column>
          <el-table-column v-if="showMoreColumns" label="水价" width="65">
            <template #default>{{ waterPrice }}</template>
          </el-table-column>
          <el-table-column v-if="showMoreColumns" label="备注" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.note" placeholder="备注" size="small" />
            </template>
          </el-table-column>
          <el-table-column v-if="showMoreColumns" prop="abnormalReason" label="异常原因" min-width="140" />
        </el-table>
        <div style="display:flex;justify-content:flex-end;align-items:center;padding:8px 0;gap:8px">
          <el-pagination v-model:current-page="tablePage" v-model:page-size="tablePageSize"
            :page-sizes="[10,20,50,100]" :total="filteredTableData.length"
            layout="total,sizes,prev,pager,next" size="small" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { householdApi, readingApi } from '@/api'

const readingDate = ref(new Date().toISOString().slice(0,10))
const selectedVillage = ref('')
const allVillages = ref([])
const filterKeyword = ref('')
const waterPrice = ref(1.8)
const saving = ref(false)
const showMoreColumns = ref(false)
const tableKeyword = ref('')
const tablePage = ref(1)
const tablePageSize = ref(20)

// 村民列表
const householdList = ref([])
const selectedHousehold = ref(null)
const householdForm = reactive({ id:null, householdName:'', waterMeterId:'', phone:'', villageName:'' })
const savingHousehold = ref(false)

// 表格
const tableData = ref([])
const selectedHouseholdIds = ref([])
const batchVillage = ref('')

// 导出命令处理
function handleExportCommand(cmd) {
  const params = {}
  if (cmd==='current' && selectedVillage.value) params.villageNames = selectedVillage.value
  householdApi.exportExcel(params).then(blob => {
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = '村民信息.xlsx'; a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success('导出成功')
  }).catch(()=>{})
}

// 左侧村民列表搜索过滤
const filteredHouseholdList = computed(() => {
  if (!filterKeyword.value) return householdList.value
  const kw = filterKeyword.value.toLowerCase()
  return householdList.value.filter(h =>
    (h.householdName||'').toLowerCase().includes(kw) ||
    (h.waterMeterId||'').toLowerCase().includes(kw))
})

// 右侧表格搜索过滤
const filteredTableData = computed(() => {
  if (!tableKeyword.value) return tableData.value
  const kw = tableKeyword.value.toLowerCase()
  return tableData.value.filter(r =>
    (r.householdName||'').toLowerCase().includes(kw) ||
    (r.waterMeterId||'').toLowerCase().includes(kw))
})

// 分页后的表格数据
const pagedTableData = computed(() => {
  const start = (tablePage.value - 1) * tablePageSize.value
  return filteredTableData.value.slice(start, start + tablePageSize.value)
})

function onSearchChange() {} // computed handles filtering
function filterTable() {} // computed handles it

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
    const r = await householdApi.list({ page:0, size:10000 })
    allVillages.value = [...new Set((r?.content||[]).map(h=>h.villageName).filter(Boolean))].sort()
  } catch {}
}

async function loadHouseholdList() {
  try {
    const params = { page:0, size:10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    householdList.value = r?.content || []
  } catch { householdList.value = [] }
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
  selectedHousehold.value = { id:null, householdName:'', waterMeterId:'', phone:'' }
  householdForm.id = null
  householdForm.householdName = ''
  householdForm.waterMeterId = ''
  householdForm.phone = ''
  householdForm.villageName = selectedVillage.value || ''
}

async function saveHousehold() {
  if (!householdForm.householdName || !householdForm.waterMeterId) { ElMessage.warning('户主姓名和表号不能为空'); return }
  savingHousehold.value = true
  try {
    const data = { householdName:householdForm.householdName, waterMeterId:householdForm.waterMeterId,
      phone:householdForm.phone, villageName:householdForm.villageName||selectedVillage.value||'' }
    if (householdForm.id) { await householdApi.update(householdForm.id, data); ElMessage.success('更新成功') }
    else { await householdApi.add(data); ElMessage.success('新增成功') }
    loadHouseholdList(); loadTable(); loadAllVillages()
  } catch {} finally { savingHousehold.value = false }
}

async function deleteHousehold() {
  if (!householdForm.id) return
  try { await ElMessageBox.confirm('确定要永久删除该户及其所有关联数据吗？此操作不可恢复。','确认永久删除',{type:'warning'}) } catch { return }
  try { await householdApi.delete(householdForm.id); ElMessage.success('已永久删除'); selectedHousehold.value=null; loadHouseholdList(); loadTable(); loadAllVillages() } catch {}
}

async function deleteVillage() {
  if (!selectedVillage.value) return
  try { await ElMessageBox.confirm(`确定要永久删除「${selectedVillage.value}」全部村民及关联数据吗？`,'确认删除村组',{type:'warning'}) } catch { return }
  try { await householdApi.deleteByVillage(selectedVillage.value); ElMessage.success('已删除村组'); selectedHousehold.value=null; loadHouseholdList(); loadTable(); loadAllVillages() } catch {}
}

// ===== 右侧表格 =====

async function loadTable() {
  let households = []
  try {
    const params = { page:0, size:10000 }
    if (selectedVillage.value) params.villageNames = selectedVillage.value
    const r = await householdApi.list(params)
    households = r?.content || []
  } catch { households = [] }

  let readingsMap = {}
  try {
    const readings = await readingApi.getByDate({ readingDate: readingDate.value, villageName: selectedVillage.value })
    if (readings?.length) readings.forEach(r => { readingsMap[r.waterMeterId] = r })
  } catch {}

  tableData.value = households.map(h => {
    const r = readingsMap[h.waterMeterId]
    if (r) {
      const chargeable = r.chargeableUsage != null ? r.chargeableUsage : (r.usageAmount != null ? r.usageAmount : null)
      return { id:h.id, villageName:h.villageName, waterMeterId:h.waterMeterId, householdName:h.householdName,
        phone:h.phone||'', previousReading:r.previousReading!=null?r.previousReading:0,
        currentReading:r.currentReading!=null?String(r.currentReading):null,
        usageAmount:r.usageAmount!=null?Number(r.usageAmount).toFixed(2):null,
        chargeableUsage:chargeable!=null?Number(chargeable):null,
        waterCharge:chargeable!=null?(Number(chargeable)*waterPrice.value).toFixed(2):null,
        note:r.note||'', isAbnormal:r.isAbnormal||false, abnormalReason:r.abnormalReason||'' }
    }
    return { id:h.id, villageName:h.villageName, waterMeterId:h.waterMeterId, householdName:h.householdName,
      phone:h.phone||'', previousReading:0, currentReading:null, usageAmount:null, chargeableUsage:null,
      waterCharge:null, note:'', isAbnormal:false, abnormalReason:'' }
  })
}

function onRowClick(row) { onSelectHousehold(row) }
function onSelectionChange(rows) { selectedHouseholdIds.value = rows.map(r=>r.id).filter(Boolean) }

async function applyBatchVillage() {
  if (!batchVillage.value || !selectedHouseholdIds.value.length) return
  try { await householdApi.batchUpdateVillage(selectedHouseholdIds.value, batchVillage.value)
    ElMessage.success(`已更新 ${selectedHouseholdIds.value.length} 户 → ${batchVillage.value}`)
    batchVillage.value=''; loadTable(); loadHouseholdList(); loadAllVillages() } catch {}
}

async function batchDeleteHouseholds() {
  if (!selectedHouseholdIds.value.length) return
  try { await ElMessageBox.confirm(`确定要永久删除选中的 ${selectedHouseholdIds.value.length} 户及其关联数据吗？`,'确认批量删除',{type:'warning'}) } catch { return }
  try { await householdApi.batchDelete(selectedHouseholdIds.value); ElMessage.success('已删除'); selectedHouseholdIds.value=[]; loadTable(); loadHouseholdList(); loadAllVillages() } catch {}
}

function calcRow(row) {
  if (!row.currentReading||isNaN(row.currentReading)) { row.usageAmount=null; row.chargeableUsage=null; row.waterCharge=null; row.isAbnormal=false; row.abnormalReason=''; return }
  const cur=parseFloat(row.currentReading), prev=parseFloat(row.previousReading)||0
  row.usageAmount=(cur-prev).toFixed(2)
  row.isAbnormal=cur<prev; row.abnormalReason=cur<prev?'表底倒转':''
  row.chargeableUsage=cur-prev>0?Number((cur-prev).toFixed(2)):0
  calcCharge(row)
}
function calcCharge(row) {
  const c=row.chargeableUsage
  row.waterCharge=c!=null&&!isNaN(c)&&c>=0?(c*waterPrice.value).toFixed(2):null
}

// 导入模板下拉
function handleImportCommand(cmd) {
  const input=document.createElement('input'); input.type='file'; input.accept='.xlsx'
  input.onchange=async e=>{
    const file=e.target.files[0]; if(!file) return
    if(cmd==='readings') {
      const fd=new FormData(); fd.append('file',file); fd.append('readingDate',readingDate.value)
      try { const r=await readingApi.importReadings(fd); ElMessage.success(`成功${r.total}条`); loadTable() } catch {}
    } else {
      const fd=new FormData(); fd.append('file',file)
      try { const r=await householdApi.importFromRegister(fd); ElMessage.success(`新增${r.inserted}户`); loadTable(); loadHouseholdList(); loadAllVillages() } catch {}
    }
  }; input.click()
}

async function exportTemplate() {
  const params={}; if(selectedVillage.value) params.villageNames=selectedVillage.value
  try { const blob=await readingApi.exportTemplate(params); const a=document.createElement('a')
    a.href=URL.createObjectURL(blob); a.download='抄表模板.xlsx'; a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success('模板已下载') } catch {}
}

async function batchSave() {
  if(!selectedVillage.value){ElMessage.warning('请先选择村组');return}
  const items=tableData.value.filter(r=>r.currentReading&&!isNaN(r.currentReading)).map(r=>{
    const item={waterMeterId:r.waterMeterId,currentReading:parseFloat(r.currentReading)}
    if(r.chargeableUsage!=null&&!isNaN(r.chargeableUsage)) item.chargeableUsage=parseFloat(r.chargeableUsage)
    if(r.note) item.note=r.note; return item })
  if(!items.length){ElMessage.warning('请至少输入一个表底数');return}
  saving.value=true
  try { const r=await readingApi.batchSave(items,readingDate.value); ElMessage.success(`保存完成：成功${r.total}条`); loadTable() }
  catch {} finally { saving.value=false }
}

onMounted(async ()=>{
  try { const c=await readingApi.getConfig(); waterPrice.value=c.waterPrice||1.8 } catch {}
  await loadAllVillages()
})
</script>

<style scoped>
.is-error :deep(.el-input__inner) { border-color:#F56C6C!important; background:#fef0f0!important }
</style>
