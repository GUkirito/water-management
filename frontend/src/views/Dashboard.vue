<template>
  <div class="wm-page">
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

    <template v-if="statsLoading">
      <div class="skeleton dashboard-skeleton-rate" />
      <section class="stats-grid">
        <div class="skeleton dashboard-skeleton-card" />
        <div class="sub-grid">
          <div v-for="i in 4" :key="i" class="skeleton dashboard-skeleton-card" />
        </div>
      </section>
      <div class="skeleton dashboard-skeleton-table" />
      <div class="skeleton dashboard-skeleton-table" />
    </template>

    <template v-else>
      <section class="wm-stat-card wm-rate-overview">
        <div class="wm-rate-overview-content">
          <div class="wm-rate-group">
            <div>
              <div class="wm-stat-label">水费收缴率</div>
              <div class="wm-stat-value wm-stat-value--compact" :class="waterRateColor">{{ waterRate }}%</div>
            </div>
            <div class="wm-rate-divider">|</div>
            <div>
              <div class="wm-stat-label">材料费收缴率</div>
              <div class="wm-stat-value wm-stat-value--compact" :class="materialRateColor">{{ materialRate }}%</div>
            </div>
          </div>
          <div class="wm-rate-total">
            <div class="wm-stat-label">综合收缴率</div>
            <div class="wm-stat-value wm-stat-value--large" :class="overallRateColor">{{ overallCollectionRate }}%</div>
          </div>
        </div>
      </section>

      <section class="stats-grid">
        <div class="wm-stat-card">
          <div class="wm-stat-label">村庄概况</div>
          <div class="wm-stat-meta">总户数</div>
          <div class="wm-stat-value is-primary">{{ stats.totalHouseholds }}</div>
          <div class="wm-stat-meta wm-stat-meta--spaced">本月用水总量</div>
          <div class="wm-stat-value wm-stat-value--compact">{{ stats.monthlyUsage }}</div>
        </div>
        <div class="sub-grid">
          <div class="wm-stat-card">
            <div class="wm-stat-label">水费应收</div>
            <div class="wm-stat-value is-warning">¥{{ stats.monthlyCharge }}</div>
          </div>
          <div class="wm-stat-card">
            <div class="wm-stat-label">水费实收</div>
            <div class="wm-stat-value is-success">¥{{ stats.monthlyPaid }}</div>
          </div>
          <div class="wm-stat-card">
            <div class="wm-stat-label">材料费应收</div>
            <div class="wm-stat-value is-warning">¥{{ matStats.totalFee }}</div>
          </div>
          <div class="wm-stat-card">
            <div class="wm-stat-label">材料费实收</div>
            <div class="wm-stat-value is-success">¥{{ matStats.totalPaid }}</div>
          </div>
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
          <el-empty v-if="!villageSummary.length" :image-size="72" class="wm-empty">
            <template #description>
              <p class="text-gray-500 text-sm">暂无村组收缴数据</p>
              <p class="text-gray-400 text-xs mt-1">请先在「抄表录入」中添加抄表记录</p>
            </template>
          </el-empty>
        </div>
      </section>

      <section class="wm-panel">
        <div class="wm-panel-body">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px">
            <div>
              <div style="font-size:16px;font-weight:600;color:var(--wm-text);display:flex;align-items:center">
                异常抄表提醒
                <el-tag size="small" type="danger" effect="dark" class="ml-2">
                  {{ abnormalReadings.length }}
                </el-tag>
              </div>
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
          <el-empty v-if="!abnormalReadings.length" :image-size="72" class="wm-empty">
            <template #description>
              <p class="text-gray-500 text-sm">暂无异常记录</p>
              <p class="text-gray-400 text-xs mt-1">暂无统计数据</p>
            </template>
          </el-empty>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { householdApi, reportApi, readingApi, materialRecordApi } from '@/api'

const statsLoading = ref(true)
const currentMonthLabel = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' })
const stats = reactive({
  totalHouseholds: 0,
  monthlyUsage: '0.00',
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

const waterRate = computed(() => stats.collectionRate)
const materialRate = computed(() => matStats.collectionRate)
const overallCollectionRate = computed(() => {
  const water = Number(stats.collectionRate) || 0
  const material = Number(matStats.collectionRate) || 0
  return ((water + material) / 2).toFixed(1)
})
const rateColor = (rate) => {
  if (rate >= 90) return 'text-emerald-600'
  if (rate >= 60) return 'text-amber-500'
  return 'text-red-500'
}
const overallRateColor = computed(() => rateColor(Number(overallCollectionRate.value)))
const waterRateColor = computed(() => rateColor(Number(stats.collectionRate)))
const materialRateColor = computed(() => rateColor(Number(matStats.collectionRate)))

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
        let usage = 0
        rows.forEach(r => {
          charge += Number(r.waterCharge || 0)
          paid += Number(r.actualWaterPaid || 0)
          usage += Number(r.waterAmount || r.usageAmount || 0)
        })
        stats.monthlyUsage = usage.toFixed(2)
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

<style scoped>
.wm-rate-overview {
  width: 100%;
  min-height: 112px;
  background: linear-gradient(135deg, #f8fafc 0%, #fff 100%);
}

.wm-rate-overview-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.wm-rate-group {
  display: flex;
  align-items: center;
  gap: 24px;
}

.wm-rate-divider {
  color: #d1d5db;
  font-size: 20px;
}

.wm-rate-total {
  text-align: right;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 18px;
}

.sub-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.wm-stat-meta {
  font-size: 12px;
  color: var(--wm-text-2);
  margin-bottom: 6px;
}

.wm-stat-meta--spaced {
  margin-top: 18px;
}

.wm-stat-value--compact {
  font-size: 24px;
}

.wm-stat-value--large {
  font-size: 32px;
}

.dashboard-skeleton-rate {
  min-height: 112px;
}

.dashboard-skeleton-card {
  min-height: 120px;
}

.dashboard-skeleton-table {
  min-height: 240px;
}

.text-emerald-600 {
  color: #059669;
}

.text-amber-500 {
  color: #f59e0b;
}

.text-red-500 {
  color: #ef4444;
}

.ml-2 {
  margin-left: 8px;
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .wm-rate-overview-content,
  .wm-rate-group {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .wm-rate-divider {
    display: none;
  }

  .wm-rate-total {
    text-align: left;
  }

  .sub-grid {
    grid-template-columns: 1fr;
  }
}
</style>
