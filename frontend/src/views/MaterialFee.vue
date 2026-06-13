<template>
  <div>
    <!-- 操作栏 -->
    <div style="background:#fff;padding:16px;border-radius:8px;margin-bottom:16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap">
      <span style="font-weight:500">村组：</span>
      <el-select v-model="selectedVillage" placeholder="选择村组" @change="handleFilterChange" style="width:150px" clearable>
        <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
      </el-select>
      <span style="font-weight:500">状态：</span>
      <el-select v-model="filterStatus" placeholder="全部" @change="handleFilterChange" style="width:120px" clearable>
        <el-option label="全部" value="" />
        <el-option label="已收" value="已收" />
        <el-option label="未收" value="未收" />
        <el-option label="部分收" value="部分收" />
      </el-select>
      <span style="font-weight:500">收费日期：</span>
      <el-date-picker v-model="paidDateRange" type="daterange" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
        @change="handleFilterChange" style="width:250px" />
      <span style="font-weight:500">🔍</span>
      <el-input v-model="filterKeyword" placeholder="户名/表号" @keyup.enter="loadData" style="width:160px" clearable />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="handleCreate">➕ 新增</el-button>
      <el-upload :before-upload="importExcelFile" :show-file-list="false" accept=".xlsx" style="margin-left:auto">
        <el-button type="warning">📥 导入材料费</el-button>
      </el-upload>
      <el-button type="info" @click="exportExcel">📤 导出Excel</el-button>
      <el-button type="danger" @click="handleBatchDelete" :disabled="selectedRows.length===0">🗑 批量删除</el-button>
    </div>

    <!-- 表格 -->
    <div style="background:#fff;padding:16px;border-radius:8px">
      <el-table :data="tableData" border stripe max-height="calc(100vh - 340px)" style="width:100%"
        v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="householdName" label="户主姓名" width="100" />
        <el-table-column prop="waterMeterId" label="表号" width="120" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="villageName" label="村组" width="100" />
        <el-table-column prop="totalFee" label="材料费(元)" width="110">
          <template #default="{ row }">{{ Number(row.totalFee || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="actualPaid" label="已缴" width="100">
          <template #default="{ row }">{{ Number(row.actualPaid || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="欠费" width="100">
          <template #default="{ row }">
            <span :style="{color:unpaid(row)>0?'#F56C6C':'#67C23A',fontWeight:'bold'}">{{ unpaid(row).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近收费日期" width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.paidAt" type="date" value-format="YYYY-MM-DD"
              size="small" style="width:150px" placeholder="选择日期"
              @change="(val) => updateField(row.id, 'paidAt', val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">✏️</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">🗑</el-button>
            <el-button v-if="row.status!=='已收'" type="success" size="small" @click="openCollectDialog(row)">💰</el-button>
            <el-tag v-else type="success" size="small">已收</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
          :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="formVisible" :title="formMode==='create'?'新增材料费':'编辑材料费'" width="450px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="户主姓名" required><el-input v-model="form.householdName" /></el-form-item>
        <el-form-item label="表号" required><el-input v-model="form.waterMeterId" :disabled="formMode==='edit'" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="村组" required>
          <el-select v-model="form.villageName" filterable allow-create style="width:100%">
            <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="材料费(元)"><el-input-number v-model="form.totalFee" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="最近收费日期"><el-date-picker v-model="form.paidAt" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible=false">取消</el-button>
        <el-button type="primary" @click="saveForm" :loading="savingForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="550px" :close-on-click-modal="false">
      <div style="font-size:16px;margin-bottom:8px">✅ 成功：<strong>{{ importResult.inserted||0 }}</strong> 条</div>
      <div v-if="importResult.skipped>0" style="font-size:16px;margin-bottom:8px">⚠️ 跳过：<strong>{{ importResult.skipped }}</strong> 条（已存在）</div>
      <div v-if="importResult.errors?.length" style="margin-top:12px">
        <strong>❌ 错误明细 ({{ importResult.errors.length }}条)：</strong>
        <div style="max-height:250px;overflow-y:auto;background:#f5f5f5;padding:8px;border-radius:4px;font-size:13px;margin-top:4px;line-height:1.8">
          <div v-for="(e,i) in importResult.errors" :key="i" style="color:#F56C6C">{{ e }}</div>
        </div>
      </div>
      <template #footer><el-button type="primary" @click="importResultVisible=false">关闭</el-button></template>
    </el-dialog>

    <!-- 收费对话框 -->
    <el-dialog v-model="collectVisible" title="收取材料费" width="450px" :close-on-click-modal="false">
      <el-form :model="collectForm" label-width="100px">
        <el-form-item label="户主"><span style="font-weight:bold">{{ collectForm.householdName }}</span></el-form-item>
        <el-form-item label="表号"><span>{{ collectForm.waterMeterId }}</span></el-form-item>
        <el-form-item label="应收材料费"><span>¥{{ Number(collectForm.totalFee||0).toFixed(2) }}</span></el-form-item>
        <el-form-item label="已缴"><span>¥{{ Number(collectForm.actualPaid||0).toFixed(2) }}</span></el-form-item>
        <el-divider />
        <el-form-item label="实收金额" required><el-input-number v-model="collectForm.amount" :precision="2" :min="0.01" :max="Number(collectForm.unpaid||0)" style="width:100%" /></el-form-item>
        <el-form-item label="收款日期" required><el-date-picker v-model="collectForm.paidDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="收款人" required><el-input v-model="collectForm.collector" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="collectForm.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collectVisible=false">取消</el-button>
        <el-button type="primary" @click="doCollect" :loading="collecting">确认收费</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { householdApi, materialRecordApi } from '@/api'

const selectedVillage = ref('')
const filterStatus = ref('')
const filterKeyword = ref('')
const paidDateRange = ref(null)
const villageList = ref([])
const tableData = ref([])
const selectedRows = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const formVisible = ref(false)
const formMode = ref('create')
const savingForm = ref(false)
const form = ref({ id: null, householdName: '', waterMeterId: '', phone: '', villageName: '', totalFee: 1500, paidAt: '', note: '' })

const importResultVisible = ref(false)
const importResult = ref({ inserted: 0, skipped: 0, errors: [] })

const collectVisible = ref(false)
const collecting = ref(false)
const collectForm = ref({ recordId: null, householdName: '', waterMeterId: '', totalFee: 0, actualPaid: 0, unpaid: 0, amount: null, paidDate: new Date().toISOString().slice(0,10), collector: '管理员', note: '' })

function unpaid(row) { return Number((row.totalFee||0)-(row.actualPaid||0)) }
function statusType(s) { return s==='已收'?'success':s==='部分收'?'warning':'danger' }

async function loadVillages() {
  try {
    const r = await materialRecordApi.list({ page:0, size:10000 })
    villageList.value = [...new Set((r?.content||[]).map(x=>x.villageName).filter(Boolean))].sort()
  } catch {}
}
onMounted(loadVillages)

async function loadData() {
  loading.value = true
  try {
    const p = { page: currentPage.value-1, size: pageSize.value }
    if(selectedVillage.value) p.villageName = selectedVillage.value
    if(filterStatus.value) p.status = filterStatus.value
    if(filterKeyword.value) p.keyword = filterKeyword.value
    if(paidDateRange.value?.length===2) { p.paidDateFrom=paidDateRange.value[0]; p.paidDateTo=paidDateRange.value[1] }
    const r = await materialRecordApi.list(p)
    tableData.value = r?.content || []
    total.value = r?.totalElements || 0
  } catch {} finally { loading.value = false }
}

function handleFilterChange() { currentPage.value = 1 }
function onSelectionChange(rows) { selectedRows.value = rows }

async function updateField(id, field, value) {
  if (value == null) return
  try {
    await materialRecordApi.update(id, { [field]: value })
  } catch {}
}

function handleCreate() {
  formMode.value = 'create'; formVisible.value = true
  form.value = { id:null, householdName:'', waterMeterId:'', phone:'', villageName:selectedVillage.value||'', totalFee:1500, paidAt:'', note:'' }
}
function handleEdit(row) {
  formMode.value = 'edit'; formVisible.value = true
  form.value = { id:row.id, householdName:row.householdName, waterMeterId:row.waterMeterId, phone:row.phone||'', villageName:row.villageName||'', totalFee:Number(row.totalFee)||1500, paidAt:row.paidAt||'', note:row.note||'' }
}
async function saveForm() {
  if(!form.value.householdName){ElMessage.warning('请输入户主姓名');return}
  if(!form.value.waterMeterId){ElMessage.warning('请输入表号');return}
  if(!form.value.villageName){ElMessage.warning('请选择村组');return}
  savingForm.value = true
  try {
    if(formMode.value==='create') {
      await materialRecordApi.create({householdName:form.value.householdName,waterMeterId:form.value.waterMeterId,phone:form.value.phone,villageName:form.value.villageName,totalFee:form.value.totalFee,paidAt:form.value.paidAt||undefined,note:form.value.note})
    } else {
      await materialRecordApi.update(form.value.id,{householdName:form.value.householdName,phone:form.value.phone,villageName:form.value.villageName,totalFee:form.value.totalFee,paidAt:form.value.paidAt||undefined,note:form.value.note})
    }
    ElMessage.success(formMode.value==='create'?'新增成功':'更新成功')
    formVisible.value = false; await loadData(); await loadVillages()
  } catch {} finally { savingForm.value = false }
}

async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定要删除 ${row.householdName} 的记录吗？`,'确认删除',{type:'warning'}) } catch { return }
  try { await materialRecordApi.delete(row.id); ElMessage.success('已删除'); await loadData(); await loadVillages() } catch {}
}
async function handleBatchDelete() {
  if(!selectedRows.value.length){ElMessage.warning('请先选择记录');return}
  try { await ElMessageBox.confirm(`确定要删除 ${selectedRows.value.length} 条记录吗？`,'确认批量删除',{type:'warning'}) } catch { return }
  try {
    await materialRecordApi.batchDelete(selectedRows.value.map(r=>r.id))
    ElMessage.success(`已删除 ${selectedRows.value.length} 条`)
    selectedRows.value = []; await loadData(); await loadVillages()
  } catch {}
}

async function importExcelFile(file) {
  const fd = new FormData(); fd.append('file', file)
  try {
    const r = await materialRecordApi.importExcel(fd)
    importResult.value = { inserted: r.inserted||0, skipped: r.skipped||0, errors: r.errors||[] }
    importResultVisible.value = true
    await loadData(); await loadVillages()
  } catch {}
  return false
}

async function exportExcel() {
  const p = {}
  if(selectedVillage.value) p.villageName = selectedVillage.value
  if(filterStatus.value) p.status = filterStatus.value
  if(filterKeyword.value) p.keyword = filterKeyword.value
  if(paidDateRange.value?.length===2){p.paidDateFrom=paidDateRange.value[0];p.paidDateTo=paidDateRange.value[1]}
  try {
    const blob = await materialRecordApi.exportExcel(p)
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = '材料费管理_'+new Date().toISOString().slice(0,10)+'.xlsx'
    a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success('导出成功')
  } catch {}
}

function openCollectDialog(row) {
  collectForm.value = { recordId:row.id, householdName:row.householdName, waterMeterId:row.waterMeterId, totalFee:row.totalFee, actualPaid:row.actualPaid, unpaid:unpaid(row), amount:unpaid(row)>0?unpaid(row):null, paidDate:new Date().toISOString().slice(0,10), collector:'管理员', note:'' }
  collectVisible.value = true
}
async function doCollect() {
  if(!collectForm.value.amount||collectForm.value.amount<=0){ElMessage.warning('请输入实收金额');return}
  if(!collectForm.value.paidDate){ElMessage.warning('请选择收款日期');return}
  collecting.value = true
  try {
    await materialRecordApi.collect(collectForm.value.recordId,{amount:collectForm.value.amount,paidDate:collectForm.value.paidDate,collector:collectForm.value.collector||'管理员',note:collectForm.value.note||undefined})
    ElMessage.success('收费成功'); collectVisible.value = false; await loadData()
  } catch {} finally { collecting.value = false }
}
</script>
