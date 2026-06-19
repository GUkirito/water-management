<template>
  <div v-loading="statsLoading" element-loading-text="加载中...">
    <!-- 水费统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">总户数</div>
            <div style="font-size:32px;font-weight:bold;color:#409EFF;margin:10px 0">{{ stats.totalHouseholds }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">本月应收水费</div>
            <div style="font-size:32px;font-weight:bold;color:#E6A23C;margin:10px 0">¥{{ stats.monthlyCharge }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">本月实收水费</div>
            <div style="font-size:32px;font-weight:bold;color:#67C23A;margin:10px 0">¥{{ stats.monthlyPaid }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">本月收缴率</div>
            <div style="font-size:32px;font-weight:bold;color:#F56C6C;margin:10px 0">{{ stats.collectionRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 材料费统计卡片 -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">材料费应收总额</div>
            <div style="font-size:32px;font-weight:bold;color:#E6A23C;margin:10px 0">¥{{ matStats.totalFee }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">材料费实收总额</div>
            <div style="font-size:32px;font-weight:bold;color:#67C23A;margin:10px 0">¥{{ matStats.totalPaid }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center">
            <div style="font-size:14px;color:#909399">材料费收缴率</div>
            <div style="font-size:32px;font-weight:bold;color:#F56C6C;margin:10px 0">{{ matStats.collectionRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常抄表提醒 -->
    <el-card style="margin-top:20px" header="异常抄表提醒" shadow="hover">
      <el-table :data="abnormalReadings" stripe size="small" max-height="350">
        <el-table-column prop="readingDate" label="日期" width="120">
          <template #default="{ row }">{{ row.readingDate?.slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column prop="householdName" label="户名" width="100" />
        <el-table-column prop="villageName" label="村名" width="100" />
        <el-table-column prop="abnormalReason" label="异常原因" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!abnormalReadings.length" description="暂无异常" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { householdApi, reportApi, readingApi, materialRecordApi } from '@/api'

const statsLoading = ref(false)
const stats = reactive({
  totalHouseholds: 0,
  monthlyCharge: '0.00',
  monthlyPaid: '0.00',
  collectionRate: '0.0'
})
const matStats = reactive({
  totalFee: '0.00',
  totalPaid: '0.00',
  collectionRate: '0.0'
})
const abnormalReadings = ref([])

onMounted(async () => {
  statsLoading.value = true
  try {
    try {
      const result = await householdApi.list({ page: 0, size: 1 })
      stats.totalHouseholds = result?.totalElements || 0
    } catch { /* 数据为空时忽略 */ }

    const now = new Date()
    try {
      const rows = await reportApi.getWaterBillReport({ year: now.getFullYear(), month: now.getMonth() + 1 })
      if (rows?.length) {
        let charge = 0, paid = 0
        rows.forEach(r => { charge += r.waterCharge || 0; paid += r.actualWaterPaid || 0 })
        stats.monthlyCharge = charge.toFixed(2)
        stats.monthlyPaid = paid.toFixed(2)
        stats.collectionRate = charge > 0 ? ((paid / charge) * 100).toFixed(1) : '100.0'
      }
    } catch { /* 忽略 */ }

    try {
      const matResult = await materialRecordApi.list({ page: 0, size: 10000 })
      const matList = matResult?.content || []
      if (matList.length) {
        let totalFee = 0, totalPaid = 0
        matList.forEach(r => { totalFee += Number(r.totalFee || 0); totalPaid += Number(r.actualPaid || 0) })
        matStats.totalFee = totalFee.toFixed(2)
        matStats.totalPaid = totalPaid.toFixed(2)
        matStats.collectionRate = totalFee > 0 ? ((totalPaid / totalFee) * 100).toFixed(1) : '100.0'
      }
    } catch { /* 忽略 */ }

    try {
      const abnormal = await readingApi.getAbnormal({ limit: 20 })
      abnormalReadings.value = abnormal || []
    } catch { /* 忽略 */ }
  } finally {
    statsLoading.value = false
  }
})
</script>
