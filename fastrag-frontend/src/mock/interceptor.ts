import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import type { Permission } from '@/types/auth'

/**
 * 接口权限检查装饰器。
 * 在 mock API 函数中调用，模拟后端接口鉴权。
 *
 * 用法：
 *   export async function deleteKnowledgeBase(kbId: string) {
 *     checkApiPermission('kb:delete')
 *     // ... 实际逻辑
 *   }
 */
export function checkApiPermission(requiredPerm: Permission): void {
  try {
    const userInfoStr = localStorage.getItem('userInfo')
    if (!userInfoStr) return
    const userInfo = JSON.parse(userInfoStr)
    const perms: string[] = userInfo.permissions || []

    // 超管放行
    if (perms.includes('*')) return

    if (!perms.includes(requiredPerm)) {
      throw new Error(`权限不足：需要 ${requiredPerm}`)
    }
  } catch (e: any) {
    if (e.message?.startsWith('权限不足')) throw e
    // 解析失败，放行（由 store 层兜底）
  }
}

// Mock 数据注册表
const mockModules: Record<string, unknown> = {}

// 动态加载所有 mock 数据
const mockDataFiles = import.meta.glob('./data/*.json', { eager: true })
for (const [path, module] of Object.entries(mockDataFiles)) {
  const name = path.replace('./data/', '').replace('.json', '')
  mockModules[name] = (module as { default: unknown }).default
}

// URL 到 mock 数据的映射
const urlMockMap: Record<string, string> = {
  '/api/auth/login': 'auth',
  '/api/auth/userinfo': 'auth',
  '/api/home': 'home',
  '/api/workspace': 'workspace',
  '/api/knowledge/list': 'knowledge-base',
  '/api/knowledge/detail': 'knowledge-detail',
  '/api/process/list': 'knowledge-process',
  '/api/application/list': 'applications',
  '/api/operation': 'operation',
  '/api/models': 'models',
  '/api/system': 'system',
  '/api/accounts': 'accounts',
  '/api/audit': 'audit',
  '/api/content': 'content',
  '/api/platform': 'platform',
  '/api/finance': 'finance',
}

/** 创建带 Mock 拦截的 axios 实例 */
export function createMockAxios(): AxiosInstance {
  const instance = axios.create({
    timeout: 10000,
    headers: { 'Content-Type': 'application/json' },
  })

  // 请求拦截器
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('ais_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // 响应拦截器 - Mock 模式
  instance.interceptors.response.use(
    async (response: AxiosResponse) => {
      const url = response.config.url || ''

      // 查找匹配的 mock 数据
      for (const [pattern, mockKey] of Object.entries(urlMockMap)) {
        if (url.startsWith(pattern)) {
          const mockData = mockModules[mockKey]
          if (mockData) {
            // 模拟网络延迟 200-500ms
            const delay = 200 + Math.random() * 300
            await new Promise((resolve) => setTimeout(resolve, delay))

            return {
              ...response,
              data: { code: 0, data: mockData, message: 'success' },
            }
          }
        }
      }

      return response
    },
    (error) => {
      return Promise.reject(error)
    }
  )

  return instance
}
