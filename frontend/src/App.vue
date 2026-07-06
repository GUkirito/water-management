<template>
  <router-view />
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { ElNotification } from 'element-plus'

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

function showUpdateStatus(event) {
  ElNotification({
    title: '发现新版本',
    message: event.detail || '正在下载安装，完成后将自动重启。',
    type: 'info',
    duration: 8000
  })
}

function showUpdateError(event) {
  ElNotification({
    title: '更新检查失败',
    message: '请检查网络连接或 GitHub Release 更新配置。',
    type: 'warning',
    duration: 8000
  })
  console.warn('更新检查失败:', event.detail)
}

onMounted(() => {
  darkModeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  applyDarkMode(darkModeMediaQuery)
  darkModeMediaQuery.addEventListener('change', applyDarkMode)
  window.addEventListener('wm-accounting-health', showAccountingHealthWarning)
  window.addEventListener('wm-update-status', showUpdateStatus)
  window.addEventListener('wm-update-error', showUpdateError)
})

onBeforeUnmount(() => {
  darkModeMediaQuery?.removeEventListener('change', applyDarkMode)
  window.removeEventListener('wm-accounting-health', showAccountingHealthWarning)
  window.removeEventListener('wm-update-status', showUpdateStatus)
  window.removeEventListener('wm-update-error', showUpdateError)
})
</script>
