<template>
  <div style="display:flex;gap:16px;height:calc(100vh - 140px)">
    <!-- 左侧：村筛选 + 户列表树 -->
    <div style="width:280px;background:#fff;padding:16px;border-radius:8px;overflow-y:auto">
      <el-select v-model="selectedVillages" multiple placeholder="按村筛选" style="width:100%;margin-bottom:12px" @change="loadTree">
        <el-option v-for="v in villages" :key="v" :label="v" :value="v" />
      </el-select>
      <el-tree
        :data="treeData"
        :props="{ children: 'children', label: 'label' }"
        node-key="id"
        highlight-current
        @node-click="onNodeClick"
      />
    </div>

    <!-- 右侧：户详情表单 -->
    <div style="flex:1;background:#fff;padding:20px;border-radius:8px;overflow-y:auto">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h3 style="margin:0">{{ form.id ? '编辑村民' : '新增村民' }}</h3>
        <el-button type="primary" @click="resetForm">新增</el-button>
        <el-button type="success" @click="exportExcel">📥 导出Excel</el-button>
        <el-upload :before-upload="importExcel" :show-file-list="false" accept=".xlsx" style="display:inline-block;margin-left:8px">
          <el-button type="warning">📤 导入Excel</el-button>
        </el-upload>
      </div>
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="户主姓名" prop="householdName">
          <el-input v-model="form.householdName" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="所属村名" prop="villageName">
          <el-input v-model="form.villageName" />
        </el-form-item>
        <el-form-item label="水表编号" prop="waterMeterId">
          <el-input v-model="form.waterMeterId" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="材料费总额">
          <el-input-number v-model="form.materialFeeTotal" :precision="2" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save" :loading="saving">保存</el-button>
          <el-button v-if="form.id" type="danger" @click="del" :loading="deleting">删除（软删除）</el-button>
        </el-form-item>
      </el-form>

      <!-- 缴费历史 -->
      <div v-if="form.id" style="margin-top:20px">
        <h4>缴费历史</h4>
        <el-table :data="paymentHistory" stripe size="small" max-height="300">
          <el-table-column prop="paidDate" label="日期" width="100" />
          <el-table-column prop="billType" label="类型" width="80">
            <template #default="{ row }">
              <el-tag :type="row.billType === 'water' ? 'primary' : 'warning'" size="small">
                {{ row.billType === 'water' ? '水费' : '材料费' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="paymentMethod" label="方式" width="80" />
          <el-table-column prop="operator" label="操作员" width="80" />
          <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!paymentHistory.length" description="暂无缴费记录" :image-size="40" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { householdApi, paymentApi } from '@/api'

const villages = ref([])
const selectedVillages = ref([])
const treeData = ref([])
const form = reactive({
  id: null, householdName: '', phone: '', villageName: '', waterMeterId: '', materialFeeTotal: 1500.00
})
const formRef = ref(null)
const saving = ref(false)
const deleting = ref(false)
const paymentHistory = ref([])
const rules = {
  householdName: [{ required: true, message: '请输入户主姓名', trigger: 'blur' }],
  villageName: [{ required: true, message: '请输入村名', trigger: 'blur' }],
  waterMeterId: [{ required: true, message: '请输入水表编号', trigger: 'blur' }]
}

// 加载数据
async function loadTree() {
  const params = { page: 0, size: 1000 }
  if (selectedVillages.value.length) params.villageNames = selectedVillages.value.join(',')
  const result = await householdApi.list(params)
  const list = result?.content || []

  // 收集所有村名
  villages.value = [...new Set(list.map(h => h.villageName))]

  // 按村分组构建树
  const groupMap = {}
  list.forEach(h => {
    if (!groupMap[h.villageName]) groupMap[h.villageName] = []
    groupMap[h.villageName].push({ id: 'h_' + h.id, label: `${h.householdName} [${h.waterMeterId}]`, household: h })
  })
  treeData.value = Object.keys(groupMap).map(v => ({ id: 'v_' + v, label: `${v} (${groupMap[v].length}户)`, children: groupMap[v] }))
}

function onNodeClick(node) {
  if (node.household) {
    Object.assign(form, {
      id: node.household.id,
      householdName: node.household.householdName,
      phone: node.household.phone || '',
      villageName: node.household.villageName,
      waterMeterId: node.household.waterMeterId,
      materialFeeTotal: node.household.materialFeeTotal
    })
    loadPaymentHistory()
  }
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { id: null, householdName: '', phone: '', villageName: '', waterMeterId: '', materialFeeTotal: 1500.00 })
  paymentHistory.value = []
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) {
      await householdApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await householdApi.add({ ...form })
      ElMessage.success('新增成功')
    }
    resetForm()
    loadTree()
  } finally { saving.value = false }
}

async function del() {
  await ElMessageBox.confirm('确定要删除该户信息吗？（软删除，数据可恢复）', '确认删除', { type: 'warning' })
  deleting.value = true
  try {
    await householdApi.delete(form.id)
    ElMessage.success('删除成功')
    resetForm()
    loadTree()
  } finally { deleting.value = false }
}

async function loadPaymentHistory() {
  if (!form.id) { paymentHistory.value = []; return }
  try {
    paymentHistory.value = await paymentApi.getHistory(form.waterMeterId) || []
  } catch { paymentHistory.value = [] }
}

async function exportExcel() {
  const params = {}
  if (selectedVillages.value.length) params.villageNames = selectedVillages.value.join(',')
  const blob = await householdApi.exportExcel(params)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = '村民信息.xlsx'; a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

async function importExcel(file) {
  const formData = new FormData()
  formData.append('file', file)
  try {
    const result = await householdApi.importExcel(formData)
    ElMessage.success(result?.message || '导入成功')
    loadTree()
  } catch (e) {
    ElMessage.error(e.message || '导入失败，请检查Excel格式')
  }
  return false
}

onMounted(loadTree)
</script>
