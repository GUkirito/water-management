<template>
  <el-container class="wm-layout">
    <el-aside class="wm-sidebar" :width="isCollapsed ? '76px' : '236px'">
      <div class="wm-brand">
        <div class="wm-brand-mark">水</div>
        <div v-if="!isCollapsed" class="wm-brand-text">
          <strong>水务管理</strong>
          <span>Village Water System</span>
        </div>
      </div>

      <el-menu
        class="wm-menu"
        :default-active="activeMenu"
        background-color="transparent"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
        router
        :collapse="isCollapsed"
        :collapse-transition="false"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/readings">
          <el-icon><EditPen /></el-icon>
          <template #title>抄表录入</template>
        </el-menu-item>
        <el-menu-item index="/billing">
          <el-icon><Money /></el-icon>
          <template #title>缴费管理</template>
        </el-menu-item>
        <el-menu-item index="/material-fee">
          <el-icon><Coin /></el-icon>
          <template #title>材料费</template>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <template #title>报表中心</template>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="wm-main">
      <el-header class="wm-topbar">
        <div class="wm-topbar-left">
          <el-button class="wm-icon-button" circle plain @click="isCollapsed = !isCollapsed">
            <el-icon>
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
          </el-button>
          <div class="wm-page-title">
            <h1>{{ currentTitle }}</h1>
            <p>抄表、缴费、材料费与报表的统一管理中心</p>
          </div>
        </div>
        <div class="wm-topbar-right">
          <span class="wm-chip">稳定运行</span>
        </div>
      </el-header>

      <el-main :class="contentClass">
        <div class="wm-mobile-brand">
          <span class="wm-mobile-brand-mark">水</span>
          <div class="wm-mobile-brand-text">
            <strong>水务管理</strong>
            <span>稳定运行</span>
          </div>
        </div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, EditPen, Money, Coin, Document, Setting, Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const isCollapsed = ref(false)
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
const contentClass = computed(() => ['wm-content', route.path === '/readings' ? 'wm-content--readings' : ''])
</script>

<style scoped>
.wm-layout {
  min-height: 100dvh;
  background: var(--wm-bg);
}

.wm-sidebar {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #111827 100%);
  border-right: 1px solid rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.wm-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.wm-brand-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.2);
  color: #dbeafe;
  font-weight: 700;
}

.wm-brand-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow: hidden;
}

.wm-brand-text strong {
  color: #fff;
  font-size: 15px;
}

.wm-brand-text span {
  color: #94a3b8;
  font-size: 12px;
}

.wm-menu {
  border-right: none;
  padding: 12px 10px;
}

.wm-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 4px 0;
  border-radius: 10px;
}

.wm-menu :deep(.el-menu-item.is-active) {
  background: rgba(37, 99, 235, 0.22);
}

.wm-menu :deep(.el-menu-item:hover) {
  background: rgba(148, 163, 184, 0.12);
}

.wm-main {
  min-width: 0;
}

.wm-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 72px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--wm-border);
}

.wm-topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.wm-topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wm-content {
  padding: 20px;
  background: var(--wm-bg);
  overflow: auto;
}

.wm-content--readings {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 16px;
}

.wm-mobile-brand {
  display: none;
}

.wm-icon-button {
  width: 36px;
  height: 36px;
  padding: 0;
}

.wm-icon-button :deep(.el-icon) {
  font-size: 16px;
}

@media (max-width: 1024px) {
  .wm-layout {
    flex-direction: column;
  }

  .wm-sidebar {
    width: 100% !important;
    max-height: 72px;
    flex-direction: row;
    align-items: center;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .wm-brand {
    min-width: 186px;
    border-bottom: none;
    border-right: 1px solid rgba(148, 163, 184, 0.12);
  }

  .wm-menu {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px;
    overflow-x: auto;
  }

  .wm-menu :deep(.el-menu-item) {
    flex: 0 0 auto;
  }

  .wm-topbar {
    height: auto;
    min-height: 72px;
    flex-wrap: wrap;
  }
}

@media (max-width: 768px) {
  .wm-topbar {
    padding: 12px 14px;
  }

  .wm-page-title p {
    max-width: 34ch;
  }

  .wm-content {
    padding: 14px;
  }

  .wm-content--readings {
    padding: 12px;
  }

  .wm-brand {
    display: none;
  }

  .wm-mobile-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    padding: 12px 14px;
    background: var(--wm-surface);
    border: 1px solid var(--wm-border);
    border-radius: var(--wm-radius);
    box-shadow: var(--wm-shadow-soft);
  }

  .wm-mobile-brand-mark {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    display: grid;
    place-items: center;
    background: rgba(37, 99, 235, 0.12);
    color: var(--wm-primary);
    font-weight: 700;
  }

  .wm-mobile-brand-text {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .wm-mobile-brand-text strong {
    font-size: 14px;
  }

  .wm-mobile-brand-text span {
    font-size: 12px;
    color: var(--wm-text-2);
  }
}
</style>
