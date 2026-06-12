<template>
  <div>
    <!-- 操作栏 -->
    <div style="background:#fff;padding:16px;border-radius:8px;margin-bottom:16px;display:flex;gap:12px;align-items:center">
      <span style="font-weight:500">村组：</span>
      <el-select v-model="selectedVillage" placeholder="选择村组" @change="loadData" style="width:200px">
        <el-option v-for="v in villageList" :key="v" :label="v" :value="v" />
      </el-select>
      <el-button type="primary" @click="loadData" :disabled="!selectedVillage">查询</el-button>
    </div>

    <!-- 材料费表格 -->
    <div style="background:#fff;padding:16px;border-radius:8px">
      <el-table :data="tableData" border stripe max-height="calc(100vh - 320px)" style="width:100%"
        v-loading="loading">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="householdName" label="户主姓名" width="100" />
        <el-table-column prop="waterMeterId" label="表号" width="120" />
        <el-table-column prop="phone" label="电话号码" width="130" />
        <el-table-column prop="totalFee" label="材料费(元)" width="110">
          <template #default="{ row }">{{ Number(row.totalFee || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="actualPaid" label="已缴" width="100">
          <template #default="{ row }">{{ Number(row.actualPaid || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="unpaid" label="欠费" width="100">
          <template #default="{ row }">
            <span :style="{ color: Number(row.unpaid) > 0 ? '#F56C6C' : '#67C23A', fontWeight: 'bold' }">
              {{ Number(row.unpaid || 0).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paidAt" label="最近收费日期" width="130">
          <template #default="{ row }">{{ row.paidAt || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== '已收'" type="primary" size="small"
              @click="openCollectDialog(row)">收费</el-button>
            <el-tag v-else type="success" size="small">已收</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>

      <el-empty v-if="!selectedVillage" description="请选择村组后查询" />
    </div>

    <!-- 收费对话框 -->
    <el-dialog v-model="dialogVisible" title="收取材料费" width="450px" :close-on-click-modal="false">
      <el-form :model="collectForm" label-width="100px">
        <el-form-item label="户主">
          <span style="font-weight:bold">{{ collectForm.householdName }}</span>
        </el-form-item>
        <el-form-item label="表号">
          <span>{{ collectForm.waterMeterId }}</span>
        </el-form-item>
        <el-form-item label="应收材料费">
          <span>¥{{ Number(collectForm.totalFee || 0).toFixed(2) }}</span>
        </el-form-item>
        <el-form-item label="已缴">
          <span>¥{{ Number(collectForm.actualPaid || 0).toFixed(2) }}</span>
        </el-form-item>
        <el-divider />
        <el-form-item label="实收金额" required>
          <el-input-number v-model="collectForm.amount" :precision="2" :min="0.01"
            :max="Number(collectForm.unpaid || 0)" style="width:100%"
            placeholder="请输入实收金额" />
        </el-form-item>
        <el-form-item label="收款日期" required>
          <el-date-picker v-model="collectForm.paidDate" type="date"
            value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="收款人" required>
          <el-input v-model="collectForm.collector" placeholder="收款人姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="collectForm.note" type="textarea" :rows="2"
            placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doCollect" :loading="collecting">确认收费</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, materialFeeApi } from '@/api'

const selectedVillage = ref('')
const villageList = ref([])
const tableData = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 收费对话框
const dialogVisible = ref(false)
const collecting = ref(false)
const collectForm = ref({
  waterMeterId: '',
  householdName: '',
  totalFee: 0,
  actualPaid: 0,
  unpaid: 0,
  amount: null,
  paidDate: new Date().toISOString().slice(0, 10),
  collector: '管理员',
  note: ''
})

// 加载村组列表
onMounted(async () => {
  try {
    const result = await householdApi.list({ page: 0, size: 1000 })
    const list = result?.content || []
    villageList.value = [...new Set(list.map(h => h.villageName))].sort()
  } catch { /* ignore */ }
})

// 加载材料费数据
async function loadData() {
  if (!selectedVillage.value) {
    tableData.value = []
    return
  }

  loading.value = true
  try {
    const params = {
      villageName: selectedVillage.value,
      page: currentPage.value - 1,
      size: pageSize.value
    }
    const result = await materialFeeApi.list(params)
    tableData.value = result?.content || []
    total.value = result?.totalElements || 0
  } catch { /* error handled by interceptor */ }
  finally { loading.value = false }
}

// 状态 → el-tag type
function statusType(status) {
  if (status === '已收') return 'success'
  if (status === '部分收') return 'warning'
  return 'danger'
}

// 打开收费对话框
function openCollectDialog(row) {
  collectForm.value = {
    waterMeterId: row.waterMeterId,
    householdName: row.householdName,
    totalFee: row.totalFee,
    actualPaid: row.actualPaid,
    unpaid: row.unpaid,
    amount: Number(row.unpaid) || null,
    paidDate: new Date().toISOString().slice(0, 10),
    collector: '管理员',
    note: ''
  }
  dialogVisible.value = true
}

// 确认收费
async function doCollect() {
  if (!collectForm.value.amount || collectForm.value.amount <= 0) {
    ElMessage.warning('请输入实收金额')
    return
  }
  if (!collectForm.value.paidDate) {
    ElMessage.warning('请选择收款日期')
    return
  }
  if (!collectForm.value.collector) {
    ElMessage.warning('请输入收款人')
    return
  }

  collecting.value = true
  try {
    await materialFeeApi.collect({
      waterMeterId: collectForm.value.waterMeterId,
      amount: collectForm.value.amount,
      paidDate: collectForm.value.paidDate,
      collector: collectForm.value.collector,
      note: collectForm.value.note || undefined
    })
    ElMessage.success('收费成功')
    dialogVisible.value = false
    await loadData()
  } catch { /* error handled by interceptor */ }
  finally { collecting.value = false }
}
</script>
