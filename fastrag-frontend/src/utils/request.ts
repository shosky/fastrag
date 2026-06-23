import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { storage } from '@/utils/storage'

/**
 * 真实 API 请求实例
 * 替代 mock/interceptor.ts 中的 createMockAxios()
 */

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截器：自动附加 JWT token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = storage.get<string>('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 {code, data, message} 格式
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 后端统一返回 { code, data, message }
    if (res.code !== undefined) {
      // 成功
      if (res.code === 200 || res.code === 0) {
        return res.data
      }

      // 未授权 → 清除 token 跳转登录
      if (res.code === 401) {
        storage.remove('token')
        storage.remove('userInfo')
        window.location.href = '/login'
        return Promise.reject(new Error(res.message || '未授权，请重新登录'))
      }

      // 其他业务错误
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    // 非标准响应（如文件下载），直接返回
    return res
  },
  (error) => {
    // 网络错误 / HTTP 错误
    const message = error.response?.data?.message || error.message || '网络异常'
    const status = error.response?.status

    if (status === 401) {
      storage.remove('token')
      storage.remove('userInfo')
      window.location.href = '/login'
    } else {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  },
)

export default service
