<template>
  <router-view />
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'

let darkModeMediaQuery

function applyDarkMode(event) {
  document.documentElement.classList.toggle('dark', event.matches)
}

onMounted(() => {
  darkModeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  applyDarkMode(darkModeMediaQuery)
  darkModeMediaQuery.addEventListener('change', applyDarkMode)
})

onBeforeUnmount(() => {
  darkModeMediaQuery?.removeEventListener('change', applyDarkMode)
})
</script>
