<template>
  <router-view />
  <AppUpdateDialog />
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { ElNotification } from 'element-plus'
import AppUpdateDialog from './components/AppUpdateDialog.vue'

let darkModeMediaQuery

function applyDarkMode(event) {
  document.documentElement.classList.toggle('dark', event.matches)
}

function showAccountingHealthWarning(event) {
  ElNotification({
    title: '发现账务数据问题',
    message: '启动检查发现账务数据异常，请到系统设置中查看账务健康检查。',
    type: 'warning',
    duration: 8000
  })
  console.warn('启动账务健康检查问题:', event.detail)
}

onMounted(() => {
  darkModeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  applyDarkMode(darkModeMediaQuery)
  darkModeMediaQuery.addEventListener('change', applyDarkMode)
  window.addEventListener('wm-accounting-health', showAccountingHealthWarning)
})

onBeforeUnmount(() => {
  darkModeMediaQuery?.removeEventListener('change', applyDarkMode)
  window.removeEventListener('wm-accounting-health', showAccountingHealthWarning)
})
</script>
