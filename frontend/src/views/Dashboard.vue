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

    <!-- 最近缴费记录 -->
    <el-card style="margin-top:20px" header="系统概览">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="数据库位置">data/water_meter.db</el-descriptions-item>
        <el-descriptions-item label="水价">¥1.80 / 吨</el-descriptions-item>
        <el-descriptions-item label="后端端口">8080</el-descriptions-item>
        <el-descriptions-item label="前端端口">3000（开发模式）</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { householdApi, reportApi } from '@/api'

const stats = reactive({
  totalHouseholds: 0,
  monthlyCharge: '0.00',
  monthlyPaid: '0.00',
  collectionRate: '0.0'
})

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
})
</script>
