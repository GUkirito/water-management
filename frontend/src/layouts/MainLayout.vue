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

      <div class="wm-sidebar-version">
        <span>v1.7.2</span>
      </div>
    </el-aside>

    <el-container class="wm-main">
      <el-header :class="topbarClass">
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
        <router-view v-slot="{ Component }">
          <Transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, EditPen, Money, Coin, Document, Setting, Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const isCollapsed = ref(false)
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
const contentClass = computed(() => ['wm-content', route.path === '/readings' ? 'wm-content--readings' : ''])
const topbarClass = computed(() => ['wm-topbar', route.path === '/readings' ? 'wm-topbar--compact' : ''])

function dispatchShortcut(name) {
  window.dispatchEvent(new CustomEvent(`wm-${name}`))
}

function focusFirstInput() {
  const input = Array.from(document.querySelectorAll('.wm-content input:not([disabled]), .wm-content textarea:not([disabled])'))
    .find((element) => element.offsetParent !== null)
  input?.focus()
  input?.select?.()
}

function handleKeydown(event) {
  if (event.key === 'F5') {
    event.preventDefault()
    dispatchShortcut('refresh')
    return
  }

  if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() === 'n') {
    event.preventDefault()
    dispatchShortcut('new')
    return
  }

  if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() === 'f') {
    event.preventDefault()
    dispatchShortcut('focus-search')
    requestAnimationFrame(focusFirstInput)
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.wm-layout {
  min-height: 100dvh;
  background: var(--wm-bg);
}

.wm-sidebar {
  position: sticky;
  top: 0;
  align-self: flex-start;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background:
    radial-gradient(circle at 20% 0%, rgba(14, 165, 233, 0.18), transparent 34%),
    linear-gradient(180deg, #083344 0%, #0f172a 58%, #111827 100%);
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
  border-radius: 14px 14px 14px 6px;
  background: rgba(2, 132, 199, 0.22);
  color: #e0f2fe;
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.25);
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
  padding-bottom: 50px;
}

.wm-sidebar-version {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 12px;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.wm-sidebar-version span {
  color: #94a3b8;
  font-size: 12px;
}

.wm-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 4px 0;
  border-radius: 10px;
}

.wm-menu :deep(.el-menu-item.is-active) {
  position: relative;
  background: rgba(2, 132, 199, 0.16) !important;
}

.wm-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background: var(--wm-primary);
  border-radius: 0 3px 3px 0;
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

.wm-topbar--compact {
  height: 52px;
  padding: 0 16px;
}

.wm-topbar--compact .wm-page-title h1 {
  font-size: 16px;
}

.wm-topbar--compact .wm-page-title p,
.wm-topbar--compact .wm-topbar-right {
  display: none;
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
  height: calc(100dvh - 52px);
  min-height: 0;
  overflow: hidden;
  padding: 6px 8px;
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

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 1024px) {
  .wm-layout {
    flex-direction: column;
  }

  .wm-sidebar {
    position: relative;
    width: 100% !important;
    height: auto;
    max-height: 72px;
    flex-direction: row;
    align-items: center;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .wm-sidebar-version {
    display: none;
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
    padding: 8px;
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
    border-radius: 12px 12px 12px 5px;
    display: grid;
    place-items: center;
    background: rgba(2, 132, 199, 0.12);
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
