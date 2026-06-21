<template>
  <div class="wm-page" v-loading="statsLoading" element-loading-text="正在加载概览...">
    <section class="wm-page-header">
      <div class="wm-page-title">
        <h1>仪表盘</h1>
        <p>查看当前收缴、异常抄表和材料费收缴情况，帮助你快速掌握村务状态。</p>
      </div>
      <div class="wm-table-actions">
        <span class="wm-chip">本月：{{ currentMonthLabel }}</span>
        <span class="wm-chip">异常抄表 {{ abnormalReadings.length }} 条</span>
      </div>
    </section>

    <section class="wm-card-grid">
      <div class="wm-stat-card">
        <div class="wm-stat-label">总户数</div>
        <div class="wm-stat-value is-primary">{{ stats.totalHouseholds }}</div>
      </div>
      <div class="wm-stat-card">
        <div class="wm-stat-label">本月应收水费</div>
        <div class="wm-stat-value is-warning">¥{{ stats.monthlyCharge }}</div>
      </div>
      <div class="wm-stat-card">
        <div class="wm-stat-label">本月实收水费</div>
        <div class="wm-stat-value is-success">¥{{ stats.monthlyPaid }}</div>
      </div>
      <div class="wm-stat-card">
        <div class="wm-stat-label">水费收缴率</div>
        <div class="wm-stat-value is-danger">{{ stats.collectionRate }}%</div>
      </div>
    </section>

    <section class="wm-card-grid">
      <div class="wm-stat-card">
        <div class="wm-stat-label">材料费应收总额</div>
        <div class="wm-stat-value is-warning">¥{{ matStats.totalFee }}</div>
      </div>
      <div class="wm-stat-card">
        <div class="wm-stat-label">材料费实收总额</div>
        <div class="wm-stat-value is-success">¥{{ matStats.totalPaid }}</div>
      </div>
      <div class="wm-stat-card">
        <div class="wm-stat-label">材料费收缴率</div>
        <div class="wm-stat-value is-danger">{{ matStats.collectionRate }}%</div>
      </div>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px">
          <div>
            <div style="font-size:16px;font-weight:600;color:var(--wm-text)">村组收缴进度</div>
            <div class="wm-muted" style="font-size:13px;margin-top:4px">按村展示本月应收、实收、欠费户数、完成率和异常抄表数。</div>
          </div>
          <span class="wm-chip">共 {{ villageSummary.length }} 个村组</span>
        </div>
        <el-table :data="villageSummary" stripe size="small" max-height="320" border>
          <el-table-column prop="villageName" label="村组" min-width="120" />
          <el-table-column label="应收" width="120">
            <template #default="{ row }">¥{{ Number(row.waterCharge || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="实收" width="120">
            <template #default="{ row }">¥{{ Number(row.actualWaterPaid || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="unpaidHouseholdCount" label="欠费户数" width="100" />
          <el-table-column label="完成率" width="150">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.collectionRate || 0)" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column prop="abnormalReadingCount" label="异常户数" width="100" />
        </el-table>
        <el-empty v-if="!villageSummary.length" description="暂无村组收缴数据" :image-size="72" class="wm-empty" />
      </div>
    </section>

    <section class="wm-panel">
      <div class="wm-panel-body">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px">
          <div>
            <div style="font-size:16px;font-weight:600;color:var(--wm-text)">异常抄表提醒</div>
            <div class="wm-muted" style="font-size:13px;margin-top:4px">这里展示需要人工确认的抄表记录。</div>
          </div>
          <span class="wm-chip">共 {{ abnormalReadings.length }} 条</span>
        </div>
        <el-table :data="abnormalReadings" stripe size="small" max-height="350" border>
          <el-table-column prop="readingDate" label="日期" width="120">
            <template #default="{ row }">{{ row.readingDate?.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column prop="householdName" label="户名" width="120" />
          <el-table-column prop="villageName" label="村名" width="120" />
          <el-table-column prop="abnormalReason" label="异常原因" min-width="220" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!abnormalReadings.length" description="暂无异常记录" :image-size="72" class="wm-empty" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { householdApi, reportApi, readingApi, materialRecordApi } from '@/api'

const statsLoading = ref(false)
const currentMonthLabel = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' })
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
const villageSummary = ref([])

onMounted(async () => {
  statsLoading.value = true
  try {
    try {
      const result = await householdApi.list({ page: 0, size: 1 })
      stats.totalHouseholds = result?.totalElements || 0
    } catch {}

    const now = new Date()
    try {
      const rows = await reportApi.getWaterBillReport({ year: now.getFullYear(), month: now.getMonth() + 1 })
      if (rows?.length) {
        let charge = 0
        let paid = 0
        rows.forEach(r => {
          charge += Number(r.waterCharge || 0)
          paid += Number(r.actualWaterPaid || 0)
        })
        stats.monthlyCharge = charge.toFixed(2)
        stats.monthlyPaid = paid.toFixed(2)
        stats.collectionRate = charge > 0 ? ((paid / charge) * 100).toFixed(1) : '0.0'
      }
    } catch {}

    try {
      const matResult = await materialRecordApi.list({ page: 0, size: 10000 })
      const matList = matResult?.content || []
      let totalFee = 0
      let totalPaid = 0
      matList.forEach(r => {
        totalFee += Number(r.totalFee || 0)
        totalPaid += Number(r.actualPaid || 0)
      })
      matStats.totalFee = totalFee.toFixed(2)
      matStats.totalPaid = totalPaid.toFixed(2)
      matStats.collectionRate = totalFee > 0 ? ((totalPaid / totalFee) * 100).toFixed(1) : '100.0'
    } catch {}

    try {
      const abnormal = await readingApi.getAbnormal({ limit: 20 })
      abnormalReadings.value = abnormal || []
    } catch {}

    try {
      const summary = await reportApi.getVillageCollectionSummary({
        year: now.getFullYear(),
        month: now.getMonth() + 1
      })
      villageSummary.value = summary || []
    } catch {}
  } finally {
    statsLoading.value = false
  }
})
</script>
