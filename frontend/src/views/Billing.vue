<template>
  <div class="wm-page">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>缴费管理</h1>
        <p>从抄表生成的未缴账单中筛选住户，直接进入收款处理。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">抄表账单联动</span>
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
      <el-button v-if="selectedMeter" @click="printPage">打印</el-button>
      <el-button v-if="selectedMeter" link type="primary" @click="openHistory">查看缴费历史</el-button>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div class="wm-billing-list-header">
          <div>
            <div style="font-size:16px;font-weight:600;color:var(--wm-text)">未缴水费账单</div>
            <div class="wm-muted" style="font-size:13px;margin-top:4px">抄表保存后生成的未缴账单会显示在这里，可按村组、户名、表号和月份筛选。</div>
          </div>
          <div class="wm-table-actions">
            <span class="wm-chip">共 {{ pendingBillRows.length }} 笔</span>
            <span class="wm-chip">欠费 ¥{{ listTotalDue.toFixed(2) }}</span>
          </div>
        </div>

        <div class="wm-toolbar wm-toolbar--compact wm-billing-filterbar">
          <el-select v-model="pendingFilters.villageName" placeholder="全部村组" clearable filterable style="width:160px" @change="loadPendingBillRows">
            <el-option v-for="v in allVillages" :key="v" :label="v" :value="v" />
          </el-select>
          <el-input v-model="pendingFilters.keyword" placeholder="搜索户名/表号" clearable style="width:220px" @keyup.enter="loadPendingBillRows" @clear="loadPendingBillRows" />
          <el-input-number v-model="pendingFilters.billYear" :min="2000" :max="2100" :controls="false" placeholder="年份" style="width:110px" @change="loadPendingBillRows" />
          <el-select v-model="pendingFilters.billMonth" placeholder="月份" clearable style="width:110px" @change="loadPendingBillRows">
            <el-option v-for="m in 12" :key="m" :label="`${m}月`" :value="m" />
          </el-select>
          <el-button type="primary" @click="loadPendingBillRows" :loading="pendingListLoading">查询</el-button>
          <el-button @click="resetPendingFilters">重置</el-button>
        </div>

        <div v-if="pendingListLoading" class="skeleton wm-table-skeleton"></div>
        <el-table
          v-else
          :data="pendingBillRows"
          border
          stripe
          class="wm-billing-list-table"
          @row-dblclick="startPayFromBill"
        >
          <el-table-column prop="villageName" label="村组" width="110" resizable show-overflow-tooltip />
          <el-table-column prop="householdName" label="户主" width="100" resizable />
          <el-table-column prop="waterMeterId" label="表号" width="130" resizable show-overflow-tooltip />
          <el-table-column label="账单月份" width="110" resizable>
            <template #default="{ row }">{{ row.billYear }}年{{ row.billMonth }}月</template>
          </el-table-column>
          <el-table-column label="用水量" width="100" resizable>
            <template #default="{ row }">{{ Number(row.waterAmount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="应收" align="right" width="120" resizable>
            <template #default="{ row }"><span class="font-mono">¥{{ Number(row.waterCharge || 0).toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="已收" align="right" width="120" resizable>
            <template #default="{ row }"><span class="font-mono">¥{{ Number(row.actualWaterPaid || 0).toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="欠费" align="right" width="120" resizable>
            <template #default="{ row }">
              <span class="font-mono" style="color:var(--wm-danger);font-weight:600">¥{{ Number(row.dueAmount || 0).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="waterStatus" label="状态" width="100" resizable>
            <template #default="{ row }">
              <el-tag :type="row.waterStatus === '部分收' ? 'warning' : 'danger'" size="small">{{ row.waterStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="备注" width="160" resizable show-overflow-tooltip />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="startPayFromBill(row)">收款</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pendingBillRows.length && !pendingListLoading" :image-size="72" class="wm-empty">
          <template #description>
            <p style="color: var(--wm-success); font-size: 16px; font-weight: 600">本月账单已全部缴清 🎉</p>
            <p class="text-gray-400 text-xs mt-1">可选择住户查看历史缴费记录</p>
          </template>
        </el-empty>
      </div>
    </section>

    <section v-if="selectedMeter" class="wm-panel">
      <div class="wm-panel-body">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <div>
            <div style="font-size:16px;font-weight:600;color:var(--wm-text)">未缴清账单</div>
            <div class="wm-muted" style="font-size:13px;margin-top:4px">勾选账单后输入实收金额，多收部分自动转为水费预存。</div>
          </div>
          <div class="wm-table-actions">
            <span class="wm-chip">水费预存 ¥{{ prepaymentBalance.toFixed(2) }}</span>
            <span class="wm-chip">共 {{ pendingBills.length }} 笔</span>
          </div>
        </div>

        <el-table :data="pendingBills" border stripe @selection-change="onSelectBills" ref="billTable">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="billYear" label="年份" width="90" resizable />
          <el-table-column prop="billMonth" label="月份" width="90" resizable>
            <template #default="{ row }">{{ row.billMonth }}月</template>
          </el-table-column>
          <el-table-column label="应收水费" align="right" width="140" resizable>
            <template #default="{ row }"><span class="font-mono">¥{{ Number(row.waterCharge || 0).toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="已缴金额" align="right" width="140" resizable>
            <template #default="{ row }"><span class="font-mono">¥{{ Number(row.actualWaterPaid || 0).toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="欠费金额" align="right" width="140" resizable>
            <template #default="{ row }">
              <span class="font-mono" style="color:var(--wm-danger);font-weight:600">¥{{ (Number(row.waterCharge || 0) - Number(row.actualWaterPaid || 0)).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="waterStatus" label="状态" width="100" resizable>
            <template #default="{ row }">
              <el-tag :type="row.waterStatus === '已收' ? 'success' : row.waterStatus === '部分收' ? 'warning' : 'danger'" size="small">
                {{ row.waterStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="备注" width="200" resizable show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!pendingBills.length" description="该住户暂无未缴水费" :image-size="72" class="wm-empty" />

        <div v-if="pendingBills.length > 0" class="wm-toolbar" style="margin-top:16px">
          <div>
            <div class="wm-muted" style="font-size:12px">已选 {{ selectedBills.length }} 笔账单，应收合计</div>
            <div style="font-size:22px;font-weight:700;color:var(--wm-warning)">¥{{ totalDue.toFixed(2) }}</div>
            <div v-if="selectedPartialCount > 0" class="wm-muted" style="font-size:12px;margin-top:2px">
              其中 {{ selectedPartialCount }} 笔已缴 ¥{{ selectedPaidTotal.toFixed(2) }}
            </div>
          </div>
          <el-input-number v-model="payAmount" :precision="2" :min="0" style="width:170px" />
          <div v-if="payAmount > 0 && payAmount === totalDue" style="color:var(--wm-success)">
            足额收款
          </div>
          <div v-if="prepayAmount > 0" style="color:var(--wm-success)">
            转入预存 ¥{{ prepayAmount.toFixed(2) }}
          </div>
          <div v-if="payAmount > 0 && payAmount < totalDue" style="color:var(--wm-warning)">
            支持部分缴费
          </div>
          <div v-if="selectedBills.length && Math.abs(paymentGap) > 0.01" style="color:var(--wm-warning)">
            差额 ¥{{ Math.abs(paymentGap).toFixed(2) }}
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
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column label="账单月份" width="100">
            <template #default="{ row }">{{ billYearMonthMap[row.billId] || '-' }}</template>
          </el-table-column>
          <el-table-column label="缴费金额" align="right" width="140">
            <template #default="{ row }"><span class="font-mono">¥{{ Number(row.amount || 0).toFixed(2) }}</span></template>
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
        <div style="font-size:15px;font-weight:600;margin:18px 0 10px">水费预存流水</div>
        <el-table :data="prepaymentLogs" border stripe size="small" max-height="260">
          <el-table-column label="金额" align="right" width="130">
            <template #default="{ row }">
              <span class="font-mono" :style="{ color: Number(row.amount || 0) >= 0 ? 'var(--wm-success)' : 'var(--wm-warning)', fontWeight: 600 }">
                {{ Number(row.amount || 0) >= 0 ? '+' : '' }}¥{{ Number(row.amount || 0).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="130" />
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="180" />
        </el-table>
        <el-empty v-if="!prepaymentLogs.length && !historyLoading" description="暂无预存流水" :image-size="50" class="wm-empty" />
      </div>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { householdApi, paymentApi } from '@/api'
import { formatLocalDate } from '@/utils/localDate'

const households = ref([])
const allVillages = ref([])
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
const prepaymentBalance = ref(0)
const prepaymentLogs = ref([])
const pendingBillRows = ref([])
const pendingListLoading = ref(false)
const pendingFilters = reactive({
  villageName: '',
  keyword: '',
  billYear: new Date().getFullYear(),
  billMonth: null
})

const totalDue = computed(() =>
  selectedBills.value.reduce((sum, b) => sum + Number(b.waterCharge || 0) - Number(b.actualWaterPaid || 0), 0)
)
const prepayAmount = computed(() => Math.max(0, Number(payAmount.value || 0) - totalDue.value))
const selectedPaidTotal = computed(() =>
  selectedBills.value.reduce((sum, b) => sum + Number(b.actualWaterPaid || 0), 0)
)
const selectedPartialCount = computed(() =>
  selectedBills.value.filter(b => Number(b.actualWaterPaid || 0) > 0).length
)
const paymentGap = computed(() => Number(payAmount.value || 0) - totalDue.value)
const listTotalDue = computed(() =>
  pendingBillRows.value.reduce((sum, b) => sum + Number(b.dueAmount || 0), 0)
)

onMounted(async () => {
  try {
    const result = await householdApi.list({ page: 0, size: 10000 })
    households.value = result?.content || []
    allVillages.value = [...new Set(households.value.map(h => h.villageName).filter(Boolean))].sort()
  } catch (error) {
    console.warn('加载缴费住户失败', error)
  }
  loadPendingBillRows()
  window.addEventListener('wm-refresh', refreshBillingPage)
})

onBeforeUnmount(() => {
  window.removeEventListener('wm-refresh', refreshBillingPage)
})

async function loadPendingBillRows() {
  pendingListLoading.value = true
  try {
    const params = {}
    if (pendingFilters.villageName) params.villageName = pendingFilters.villageName
    if (pendingFilters.keyword?.trim()) params.keyword = pendingFilters.keyword.trim()
    if (pendingFilters.billYear) params.billYear = pendingFilters.billYear
    if (pendingFilters.billMonth) params.billMonth = pendingFilters.billMonth
    pendingBillRows.value = await paymentApi.listPendingWater(params) || []
  } catch (error) {
    console.warn('加载未缴账单列表失败', error)
  } finally {
    pendingListLoading.value = false
  }
}

function resetPendingFilters() {
  pendingFilters.villageName = ''
  pendingFilters.keyword = ''
  pendingFilters.billYear = new Date().getFullYear()
  pendingFilters.billMonth = null
  loadPendingBillRows()
}

async function loadPendingBills(preselectBillId = null) {
  if (!selectedMeter.value) {
    pendingBills.value = []
    selectedBills.value = []
    prepaymentBalance.value = 0
    return
  }
  try {
    const [bills, balance] = await Promise.all([
      paymentApi.getPendingWater(selectedMeter.value),
      paymentApi.getWaterPrepaymentBalance(selectedMeter.value)
    ])
    pendingBills.value = bills || []
    prepaymentBalance.value = Number(balance || 0)
    selectedBills.value = []
    payAmount.value = 0
    billTable.value?.clearSelection()
    if (preselectBillId) {
      await nextTick()
      const target = pendingBills.value.find(b => b.id === preselectBillId)
      if (target) {
        billTable.value?.toggleRowSelection(target, true)
        selectedBills.value = [target]
        payAmount.value = Number(target.waterCharge || 0) - Number(target.actualWaterPaid || 0)
      }
    }
  } catch (error) {
    pendingBills.value = []
    selectedBills.value = []
    prepaymentBalance.value = 0
    console.warn('加载待缴账单失败', error)
  }
}

function refreshBillingPage() {
  loadPendingBillRows()
  if (selectedMeter.value) loadPendingBills()
}

function printPage() {
  window.print()
}

function onSelectBills(rows) {
  selectedBills.value = rows
}

async function startPayFromBill(row) {
  selectedMeter.value = row.waterMeterId
  await loadPendingBills(row.id)
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
  paying.value = true
  try {
    await paymentApi.pay({
      billType: 'water',
      billIds: selectedBills.value.map(b => b.id),
      amount: payAmount.value,
      paidDate: formatLocalDate(),
      paymentMethod: payMethod.value,
      operator: '管理员'
    })
    const paidAmount = Number(payAmount.value || 0)
    const paidHousehold = households.value.find(h => h.waterMeterId === selectedMeter.value)
    ElNotification({
      title: '缴费成功',
      message: `户名：${paidHousehold?.householdName || selectedMeter.value}，金额：¥${paidAmount.toFixed(2)}`,
      type: 'success',
      duration: 4000
    })
    payAmount.value = 0
    billTable.value?.clearSelection()
    await Promise.all([loadPendingBills(), loadPendingBillRows()])
  } catch (error) {
    console.warn('水费缴费失败', error)
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
  prepaymentLogs.value = []
  try {
    const [payments, allBills, logs] = await Promise.all([
      paymentApi.getHistory(selectedMeter.value),
      paymentApi.getAllWaterBills?.(selectedMeter.value),
      paymentApi.getWaterPrepaymentLogs(selectedMeter.value)
    ])
    const map = {}
    ;(allBills || []).forEach(b => { map[b.id] = `${b.billYear}年${b.billMonth}月` })
    billYearMonthMap.value = map
    historyList.value = payments || []
    prepaymentLogs.value = logs || []
  } catch (error) {
    console.warn('加载缴费历史失败', error)
  } finally {
    historyLoading.value = false
  }
}
</script>

<style scoped>
.wm-billing-list-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.wm-billing-filterbar {
  margin-bottom: 12px;
  box-shadow: none;
}

.wm-billing-list-table {
  width: 100%;
}

@media (max-width: 1024px) {
  .wm-billing-list-header {
    flex-direction: column;
  }
}
</style>
