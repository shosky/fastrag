import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api'
import { storage } from '@/utils/storage'

export const useSystemStore = defineStore('system', () => {
  const systemName = ref('AIS 智能知识服务平台')
  const slogan = ref('让知识触手可及')
  const copyright = ref('')
  const logoUrl = ref('')
  const orgName = ref('')
  const loaded = ref(false)

  async function loadConfig() {
    if (loaded.value) return
    // 未登录时跳过接口请求，使用默认值，避免 403 触发响应拦截器重定向
    if (!storage.get('token')) return
    try {
      // 从配置 API 加载品牌信息
      const res: any = await api.getSysConfigs('brand')
      const settings = res?.data || []
      settings.forEach((item: any) => {
        if (item.configKey === 'system_name' && item.configValue) systemName.value = item.configValue
        if (item.configKey === 'system_slogan' && item.configValue) slogan.value = item.configValue
        if (item.configKey === 'copyright' && item.configValue) copyright.value = item.configValue
        if (item.configKey === 'logo_url' && item.configValue) logoUrl.value = item.configValue
        if (item.configKey === 'org_name' && item.configValue) {
          try {
            const parsed = JSON.parse(item.configValue)
            if (parsed.value) orgName.value = parsed.value
          } catch { /* ignore */ }
        }
      })
      loaded.value = true
    } catch {
      // 加载失败使用默认值
    }
  }

  return {
    systemName,
    slogan,
    copyright,
    logoUrl,
    orgName,
    loaded,
    loadConfig,
  }
})
