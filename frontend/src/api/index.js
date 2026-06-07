import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 Axios 实例
const api = axios.create({
  baseURL: '/api',       // 所有请求以 /api 开头，Vite 代理转发到 8080
  timeout: 30000
})

// 响应拦截器：统一处理错误
api.interceptors.response.use(
  response => {
    // 对于文件下载（blob），直接返回原始数据，不做 JSON 解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const { code, message, data } = response.data
    if (code === 200) {
      return data           // 成功时直接返回 data，简化调用方代码
    } else {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
  },
  error => {
    ElMessage.error('网络错误：' + (error.message || '请检查服务器连接'))
    return Promise.reject(error)
  }
)

// ==================== 村民管理 ====================
export const householdApi = {
  list: (params) => api.get('/households/list', { params }),
  getById: (id) => api.get(`/households/${id}`),
  add: (data) => api.post('/households/add', data),
  update: (id, data) => api.put(`/households/update/${id}`, data),
  delete: (id) => api.delete(`/households/delete/${id}`),
  exportExcel: (params) => api.get('/households/export', { params, responseType: 'blob' }),
  importExcel: (formData) => api.post('/households/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 抄表管理 ====================
export const readingApi = {
  exportTemplate: (params) => api.get('/readings/export-template', {
    params,
    responseType: 'blob'
  }),
  importReadings: (formData) => api.post('/readings/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  batchSave: (items, year, month) => api.post('/readings/batch', items, {
    params: { year, month }
  }),
  singleSave: (params) => api.post('/readings/single', null, { params }),
  getByMonth: (params) => api.get('/readings/by-month', { params }),
  getAbnormal: (params) => api.get('/readings/abnormal', { params })
}

// ==================== 收费管理 ====================
export const paymentApi = {
  getPendingWater: (waterMeterId) =>
    api.get('/payments/pending-water', { params: { waterMeterId } }),
  getPendingMaterial: (waterMeterId) =>
    api.get('/payments/pending-material', { params: { waterMeterId } }),
  pay: (data) => api.post('/payments/pay', data),
  getHistory: (waterMeterId) =>
    api.get('/payments/history', { params: { waterMeterId } })
}

// ==================== 报表中心 ====================
export const reportApi = {
  getWaterBillReport: (params) => api.get('/reports/water-bill', { params }),
  exportWaterBillReport: (params) => api.get('/reports/water-bill/export', {
    params,
    responseType: 'blob'
  }),
  getMaterialSummary: (params) => api.get('/reports/material-summary', { params }),
  exportMaterialSummary: (params) => api.get('/reports/material-summary/export', {
    params,
    responseType: 'blob'
  })
}

export default api
