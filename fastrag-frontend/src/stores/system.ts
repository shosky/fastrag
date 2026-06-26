import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '@/api'

export const useSystemStore = defineStore('system', () => {
  const systemName = ref('AIS 智能知识服务平台')
  const slogan = ref('让知识触手可及')
  const copyright = ref('')
  const logoUrl = ref('')
  const loaded = ref(false)

  async function loadConfig() {
    if (loaded.value) return
    try {
      const res: any = await api.getDictionaries({ type: '系统信息' })
      const settings = res?.['系统信息'] || []
      settings.forEach((item: any) => {
        if (item.key === 'system_name' && item.value) systemName.value = item.value
        if (item.key === 'system_slogan' && item.value) slogan.value = item.value
        if (item.key === 'copyright' && item.value) copyright.value = item.value
        if (item.key === 'logo_url' && item.value) logoUrl.value = item.value
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
    loaded,
    loadConfig,
  }
})
