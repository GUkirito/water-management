<template>
  <el-container style="height: 100vh">
    <!-- 左侧菜单 -->
    <el-aside :width="isCollapsed ? '64px' : '200px'" style="background-color:#304156;transition:width 0.28s ease;overflow:hidden">
      <div :style="{
        height:'60px',display:'flex',alignItems:'center',justifyContent:'center',
        color:'#fff',fontWeight:'bold',borderBottom:'1px solid #4a5e73',
        overflow:'hidden',whiteSpace:'nowrap',gap:'6px',
        fontSize: isCollapsed ? '20px' : '16px'
      }">
        💧<span v-if="!isCollapsed">自来水管理</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
        style="border-right:none"
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
          <template #title>收费管理</template>
        </el-menu-item>
        <el-menu-item index="/material-fee">
          <el-icon><Coin /></el-icon>
          <template #title>材料费管理</template>
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

    <!-- 右侧内容区 -->
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #e6e6e6;display:flex;align-items:center;padding:0 20px;gap:12px">
        <el-icon @click="isCollapsed = !isCollapsed"
          style="cursor:pointer;font-size:18px;color:#606266;flex-shrink:0;transition:color 0.2s"
          onmouseover="this.style.color='#409EFF'" onmouseout="this.style.color='#606266'">
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
        <span style="font-size:18px">{{ currentTitle }}</span>
      </el-header>
      <el-main style="background:#f0f2f5;padding:20px">
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
</script>
