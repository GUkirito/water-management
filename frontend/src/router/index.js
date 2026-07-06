import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'households', redirect: '/readings' },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'readings',
        name: 'Readings',
        component: () => import('@/views/Readings.vue'),
        meta: { title: '抄表录入' }
      },
      {
        path: 'billing',
        name: 'Billing',
        component: () => import('@/views/Billing.vue'),
        meta: { title: '缴费管理' }
      },
      {
        path: 'material-fee',
        name: 'MaterialFee',
        component: () => import('@/views/MaterialFee.vue'),
        meta: { title: '材料费' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '报表中心' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '系统设置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 村级自来水管理系统` : '村级自来水管理系统'
})

export default router
