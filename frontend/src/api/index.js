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
  }),
  importFromRegister: (formData) => api.post('/households/import-from-register', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  batchUpdateVillage: (ids, villageName) => api.put('/households/batch-update-village', { ids, villageName }),
  batchDelete: (ids) => api.post('/households/batch-delete', { ids }),
  deleteByVillage: (villageName) => api.delete('/households/delete-by-village', { params: { villageName } })
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
  batchSave: (items, readingDate) => api.post('/readings/batch', items, {
    params: { readingDate }
  }),
  singleSave: (params) => api.post('/readings/single', null, { params }),
  getByDate: (params) => api.get('/readings/by-date', { params }),
  getAbnormal: (params) => api.get('/readings/abnormal', { params }),
  getConfig: () => api.get('/readings/config'),
  updateConfig: (data) => api.post('/readings/config', data)
}

// ==================== 收费管理 ====================
export const paymentApi = {
  listPendingWater: (params) =>
    api.get('/payments/pending-water-list', { params }),
  getPendingWater: (waterMeterId) =>
    api.get('/payments/pending-water', { params: { waterMeterId } }),
  pay: (data) => api.post('/payments/pay', data),
  getHistory: (waterMeterId) =>
    api.get('/payments/history', { params: { waterMeterId } }),
  getAllWaterBills: (waterMeterId) =>
    api.get('/payments/all-water-bills', { params: { waterMeterId } }),
  getWaterPrepaymentBalance: (waterMeterId) =>
    api.get('/payments/water-prepayment-balance', { params: { waterMeterId } }),
  getWaterPrepaymentLogs: (waterMeterId) =>
    api.get('/payments/water-prepayment-logs', { params: { waterMeterId } })
}

// ==================== 材料费管理（独立系统） ====================
export const materialRecordApi = {
  list: (params) => api.get('/material-records/list', { params }),
  getById: (id) => api.get(`/material-records/${id}`),
  create: (data) => api.post('/material-records', data),
  update: (id, data) => api.put(`/material-records/${id}`, data),
  delete: (id) => api.delete(`/material-records/${id}`),
  batchDelete: (ids) => api.post('/material-records/batch-delete', { ids }),
  importExcel: (formData) => api.post('/material-records/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  exportExcel: (params) => api.get('/material-records/export', {
    params, responseType: 'blob'
  }),
  collect: (id, data) => api.post(`/material-records/${id}/collect`, data),
  getPayments: (id) => api.get(`/material-records/${id}/payments`)
}

// ==================== 报表中心 ====================
export const reportApi = {
  getWaterBillReport: (params) => api.get('/reports/water-bill', { params }),
  exportWaterBillReport: (params) => api.get('/reports/water-bill/export', {
    params,
    responseType: 'blob'
  }),
  getVillageCollectionSummary: (params) => api.get('/reports/village-collection-summary', { params })
}

// ==================== 系统设置 ====================
export const settingsApi = {
  getInfo: () => api.get('/settings/info'),
  downloadBackup: () => api.get('/settings/backup/download', { responseType: 'blob' }),
  restoreBackup: (formData) => api.post('/settings/backup/restore', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export default api
