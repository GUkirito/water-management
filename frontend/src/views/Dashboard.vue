<template>
  <div>
    <!-- 统计卡片 -->
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

    <el-row :gutter="20" style="margin-top:20px">
      <!-- 各村收缴率排行 -->
      <el-col :span="14">
        <el-card header="各村收缴率排行" shadow="hover">
          <el-table :data="villageRates" stripe size="small" max-height="350">
            <el-table-column prop="villageName" label="村名" width="100" />
            <el-table-column prop="totalCharge" label="应收" width="100" />
            <el-table-column prop="totalPaid" label="实收" width="100" />
            <el-table-column label="收缴率" min-width="160">
              <template #default="{ row }">
                <div style="display:flex;align-items:center;gap:8px">
                  <el-progress
                    :percentage="row.collectionRate"
                    :color="row.collectionRate >= 80 ? '#67C23A' : row.collectionRate >= 50 ? '#E6A23C' : '#F56C6C'"
                    :stroke-width="16"
                    style="flex:1"
                  />
                  <span style="font-size:12px;width:45px">{{ row.collectionRate }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 异常抄表提醒 -->
      <el-col :span="10">
        <el-card header="异常抄表提醒" shadow="hover">
          <el-table :data="abnormalReadings" stripe size="small" max-height="350">
            <el-table-column prop="readingDate" label="日期" width="100">
              <template #default="{ row }">{{ row.readingDate?.slice(0, 10) }}</template>
            </el-table-column>
            <el-table-column prop="householdName" label="户名" width="80" />
            <el-table-column prop="villageName" label="村名" width="80" />
            <el-table-column prop="abnormalReason" label="异常原因" min-width="120" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!abnormalReadings.length" description="暂无异常" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { householdApi, reportApi, readingApi } from '@/api'

const stats = reactive({
  totalHouseholds: 0,
  monthlyCharge: '0.00',
  monthlyPaid: '0.00',
  collectionRate: '0.0'
})
const villageRates = ref([])
const abnormalReadings = ref([])

onMounted(async () => {
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

  // 各村收缴率
  try {
    const rates = await reportApi.getVillageCollectionRates({ year: now.getFullYear(), month: now.getMonth() + 1 })
    villageRates.value = rates || []
  } catch { /* 忽略 */ }

  // 异常抄表
  try {
    const abnormal = await readingApi.getAbnormal({ limit: 20 })
    abnormalReadings.value = abnormal || []
  } catch { /* 忽略 */ }
})
</script>
