<template>
  <div>
    <!-- 选择户 -->
    <div style="background:#fff;padding:16px;border-radius:8px;margin-bottom:16px;display:flex;gap:12px;align-items:center">
      <span>选择户：</span>
      <el-select v-model="selectedMeter" filterable placeholder="搜索水表编号或户名" style="width:300px"
        @change="loadPendingBills">
        <el-option v-for="h in households" :key="h.waterMeterId"
          :label="`${h.householdName} [${h.waterMeterId}] - ${h.villageName}`"
          :value="h.waterMeterId" />
      </el-select>
      <el-radio-group v-model="billType" @change="loadPendingBills">
        <el-radio-button value="water">水费</el-radio-button>
        <el-radio-button value="material">材料费</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 未缴清账单 -->
    <div style="background:#fff;padding:16px;border-radius:8px;margin-bottom:16px" v-if="selectedMeter">
      <h4 style="margin-top:0">{{ billType === 'water' ? '水费' : '材料费' }}未缴清账单</h4>
      <el-table :data="pendingBills" border @selection-change="onSelectBills" ref="billTable">
        <el-table-column type="selection" width="50" />
        <el-table-column v-if="billType === 'water'" prop="billYear" label="年份" width="80" />
        <el-table-column v-if="billType === 'water'" prop="billMonth" label="月份" width="80" />
        <el-table-column
          :prop="billType === 'water' ? 'waterCharge' : 'totalFee'"
          :label="billType === 'water' ? '应收水费' : '应收材料费'" width="120" />
        <el-table-column
          :prop="billType === 'water' ? 'actualWaterPaid' : 'actualPaid'"
          :label="billType === 'water' ? '已缴金额' : '已缴金额'" width="120" />
        <el-table-column label="欠费金额" width="120">
          <template #default="{ row }">
            {{ billType === 'water'
                ? ((row.waterCharge || 0) - (row.actualWaterPaid || 0)).toFixed(2)
                : ((row.totalFee || 0) - (row.actualPaid || 0)).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column
          :prop="billType === 'water' ? 'waterStatus' : 'status'" label="状态" width="100" />
      </el-table>

      <!-- 缴费汇总 -->
      <div style="margin-top:16px;padding:16px;background:#f5f7fa;border-radius:8px;display:flex;gap:20px;align-items:center;flex-wrap:wrap">
        <span><strong>应收总额：</strong>¥{{ totalDue.toFixed(2) }}</span>
        <span>已选中 {{ selectedBills.length }} 笔</span>
        <el-input-number v-model="payAmount" :precision="2" :min="0" placeholder="实收金额" style="width:180px" />
        <span v-if="payAmount > 0" style="color:#67C23A">
          <strong>找零：</strong>¥{{ Math.max(0, payAmount - totalDue).toFixed(2) }}
        </span>
        <el-button type="primary" size="large" @click="doPay" :loading="paying"
          :disabled="!selectedBills.length || payAmount <= 0">💳 确认支付</el-button>
      </div>
    </div>

    <el-empty v-else description="请选择一户查看其未缴清账单" />
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
const paying = ref(false)
const billTable = ref(null)

const totalDue = computed(() => {
  return selectedBills.value.reduce((sum, b) => {
    if (billType.value === 'water') {
      return sum + (b.waterCharge || 0) - (b.actualWaterPaid || 0)
    } else {
      return sum + (b.totalFee || 0) - (b.actualPaid || 0)
    }
  }, 0)
})

onMounted(async () => {
  const result = await householdApi.list({ page: 0, size: 1000 })
  households.value = result?.content || []
})

async function loadPendingBills() {
  if (!selectedMeter.value) { pendingBills.value = []; return }
  if (billType.value === 'water') {
    pendingBills.value = await paymentApi.getPendingWater(selectedMeter.value) || []
  } else {
    const bill = await paymentApi.getPendingMaterial(selectedMeter.value)
    pendingBills.value = bill ? [bill] : []
  }
  selectedBills.value = []
  payAmount.value = 0
}

function onSelectBills(rows) { selectedBills.value = rows }

async function doPay() {
  if (payAmount.value <= 0) { ElMessage.warning('请输入实收金额'); return }
  paying.value = true
  try {
    await paymentApi.pay({
      billType: billType.value,
      billIds: selectedBills.value.map(b => b.id),
      amount: payAmount.value,
      paidDate: new Date().toISOString().slice(0, 10),
      paymentMethod: '现金',
      operator: '管理员'
    })
    ElMessage.success('缴费成功')
    payAmount.value = 0
    loadPendingBills()
  } finally { paying.value = false }
}
</script>
