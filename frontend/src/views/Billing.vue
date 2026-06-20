<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>缴费管理</h1>
        <p>选择住户后查看未缴账单，支持合并缴费和历史记录查询。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">多月合并缴费</span>
      </div>
    </section>

    <section class="wm-toolbar">
      <span style="font-weight:600;color:var(--wm-text)">选择住户</span>
      <el-select v-model="selectedMeter" filterable clearable placeholder="搜索水表编号或户名" style="width:340px" @change="loadPendingBills">
        <el-option
          v-for="h in households"
          :key="h.waterMeterId"
          :label="`${h.householdName} [${h.waterMeterId}] - ${h.villageName}`"
          :value="h.waterMeterId"
        />
      </el-select>
      <el-radio-group v-model="billType" @change="loadPendingBills">
        <el-radio-button value="water">水费</el-radio-button>
      </el-radio-group>
      <el-button v-if="selectedMeter" link type="primary" @click="openHistory">查看缴费历史</el-button>
    </section>

    <section v-if="selectedMeter" class="wm-panel">
      <div class="wm-panel-body">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <div>
            <div style="font-size:16px;font-weight:600;color:var(--wm-text)">未缴清账单</div>
            <div class="wm-muted" style="font-size:13px;margin-top:4px">勾选账单后输入实收金额完成支付。</div>
          </div>
          <span class="wm-chip">共 {{ pendingBills.length }} 笔</span>
        </div>

        <el-table :data="pendingBills" border stripe @selection-change="onSelectBills" ref="billTable">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="billYear" label="年份" width="90" />
          <el-table-column prop="billMonth" label="月份" width="90">
            <template #default="{ row }">{{ row.billMonth }}月</template>
          </el-table-column>
          <el-table-column label="应收水费" width="120">
            <template #default="{ row }">¥{{ Number(row.waterCharge || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="已缴金额" width="120">
            <template #default="{ row }">¥{{ Number(row.actualWaterPaid || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="欠费金额" width="120">
            <template #default="{ row }">
              <span style="color:var(--wm-danger);font-weight:600">¥{{ (Number(row.waterCharge || 0) - Number(row.actualWaterPaid || 0)).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="waterStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.waterStatus === '已收' ? 'success' : row.waterStatus === '部分收' ? 'warning' : 'danger'" size="small">
                {{ row.waterStatus }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pendingBills.length" description="该住户暂无未缴水费" :image-size="72" class="wm-empty" />

        <div v-if="pendingBills.length > 0" class="wm-toolbar" style="margin-top:16px">
          <div>
            <div class="wm-muted" style="font-size:12px">已选账单应收合计</div>
            <div style="font-size:22px;font-weight:700;color:var(--wm-warning)">¥{{ totalDue.toFixed(2) }}</div>
          </div>
          <el-input-number v-model="payAmount" :precision="2" :min="0" :max="totalDue" style="width:170px" />
          <div v-if="payAmount > 0 && payAmount === totalDue" style="color:var(--wm-success)">
            足额收款
          </div>
          <div v-if="payAmount > 0 && payAmount < totalDue" style="color:var(--wm-warning)">
            支持部分缴费
          </div>
          <el-select v-model="payMethod" style="width:120px">
            <el-option label="现金" value="现金" />
            <el-option label="微信" value="微信" />
            <el-option label="支付宝" value="支付宝" />
          </el-select>
          <el-button type="primary" size="large" @click="doPay" :loading="paying" :disabled="!selectedBills.length || payAmount <= 0">
            确认收款
          </el-button>
        </div>
      </div>
    </section>

    <el-empty v-else description="请选择一户查看未缴账单" :image-size="80" class="wm-empty" />

    <el-dialog v-model="historyVisible" title="缴费历史" width="720px" :close-on-click-modal="false">
      <div v-loading="historyLoading" style="min-height:120px">
        <el-table :data="historyList" border stripe size="small" max-height="420">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="账单月份" width="100">
            <template #default="{ row }">{{ billYearMonthMap[row.billId] || '-' }}</template>
          </el-table-column>
          <el-table-column label="缴费金额" width="120">
            <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="paidDate" label="缴费日期" width="120" />
          <el-table-column prop="paymentMethod" label="方式" width="100">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.paymentMethod }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作员" width="100" />
          <el-table-column prop="note" label="备注" min-width="100" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!historyList.length && !historyLoading" description="暂无缴费记录" :image-size="60" class="wm-empty" />
      </div>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { householdApi, paymentApi } from '@/api'

const households = ref([])
const selectedMeter = ref('')
const billType = ref('water')
const pendingBills = ref([])
const selectedBills = ref([])
const payAmount = ref(0)
const payMethod = ref('现金')
const paying = ref(false)
const billTable = ref(null)

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const billYearMonthMap = ref({})

const totalDue = computed(() =>
  selectedBills.value.reduce((sum, b) => sum + Number(b.waterCharge || 0) - Number(b.actualWaterPaid || 0), 0)
)

onMounted(async () => {
  const result = await householdApi.list({ page: 0, size: 10000 })
  households.value = result?.content || []
})

async function loadPendingBills() {
  if (!selectedMeter.value) {
    pendingBills.value = []
    selectedBills.value = []
    return
  }
  pendingBills.value = await paymentApi.getPendingWater(selectedMeter.value) || []
  selectedBills.value = []
  payAmount.value = 0
  billTable.value?.clearSelection()
}

function onSelectBills(rows) {
  selectedBills.value = rows
}

async function doPay() {
  if (payAmount.value <= 0) {
    ElMessage.warning('请输入实收金额')
    return
  }
  if (!selectedBills.value.length) {
    ElMessage.warning('请先勾选要缴费的账单')
    return
  }
  if (payAmount.value > totalDue.value) {
    ElMessage.warning(`实收金额不能超过欠费总额 ¥${totalDue.value.toFixed(2)}`)
    return
  }
  paying.value = true
  try {
    await paymentApi.pay({
      billType: 'water',
      billIds: selectedBills.value.map(b => b.id),
      amount: payAmount.value,
      paidDate: new Date().toISOString().slice(0, 10),
      paymentMethod: payMethod.value,
      operator: '管理员'
    })
    ElMessage.success('收款成功')
    payAmount.value = 0
    billTable.value?.clearSelection()
    loadPendingBills()
  } finally {
    paying.value = false
  }
}

async function openHistory() {
  if (!selectedMeter.value) return
  historyVisible.value = true
  historyLoading.value = true
  historyList.value = []
  billYearMonthMap.value = {}
  try {
    const [payments, allBills] = await Promise.all([
      paymentApi.getHistory(selectedMeter.value),
      paymentApi.getAllWaterBills?.(selectedMeter.value)
    ])
    const map = {}
    ;(allBills || []).forEach(b => { map[b.id] = `${b.billYear}年${b.billMonth}月` })
    billYearMonthMap.value = map
    historyList.value = payments || []
  } finally {
    historyLoading.value = false
  }
}
</script>
