import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    // 代理：将 /api 开头的请求转发到后端 Spring Boot（8080 端口）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 前端打包输出目录 → 直接输出到后端的 static 目录
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
