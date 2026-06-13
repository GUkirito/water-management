<template>
  <el-container style="height: 100vh">
    <!-- 左侧菜单 -->
    <el-aside width="200px" style="background-color: #304156">
      <div style="height:60px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:16px;font-weight:bold;border-bottom:1px solid #4a5e73">
        💧 自来水管理
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
        style="border-right:none"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/readings">
          <el-icon><EditPen /></el-icon>
          <span>抄表录入</span>
        </el-menu-item>
        <el-menu-item index="/billing">
          <el-icon><Money /></el-icon>
          <span>收费管理</span>
        </el-menu-item>
        <el-menu-item index="/material-fee">
          <el-icon><Coin /></el-icon>
          <span>材料费管理</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <span>报表中心</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #e6e6e6;display:flex;align-items:center;font-size:18px;padding:0 20px">
        {{ currentTitle }}
      </el-header>
      <el-main style="background:#f0f2f5;padding:20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, EditPen, Money, Coin, Document, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
</script>
