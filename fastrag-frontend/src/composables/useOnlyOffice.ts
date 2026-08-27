/**
 * OnlyOffice Document Server 集成的 Vue 组合式 API。
 *
 * <p>封装三类常见操作：
 * <ul>
 *   <li>动态加载 {@code api.js}（幂等，多次调用只注入一次）</li>
 *   <li>创建 / 销毁 DocEditor 实例</li>
 *   <li>编辑器方法封装（滚动到页、设置可见区域）</li>
 * </ul>
 *
 * <p>这些 API 是 OO 7.0+ 的稳定接口；高版本增加的方法（如
 * {@code getSelectedText}）需要运行时特性检测，避免在低版本上报 undefined。
 */
import { ref, shallowRef } from 'vue'
import type { OnlyOfficeConfig, OnlyOfficeDocEditorInstance } from '@/types/onlyoffice'
import { onlyofficeConfig } from '@/config'

let apiLoadingPromise: Promise<void> | null = null

/**
 * 幂等加载 OO {@code api.js}。多次调用复用同一 Promise；脚本注入到 {@code <head>} 末尾。
 */
export function loadOnlyOfficeApi(): Promise<void> {
  if (typeof window !== 'undefined' && window.DocsAPI) {
    return Promise.resolve()
  }
  if (apiLoadingPromise) {
    return apiLoadingPromise
  }
  apiLoadingPromise = new Promise<void>((resolve, reject) => {
    if (typeof document === 'undefined') {
      reject(new Error('Document is not available'))
      return
    }
    const script = document.createElement('script')
    script.src = onlyofficeConfig.apiScriptUrl
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      apiLoadingPromise = null  // 失败允许重试
      reject(new Error(`Failed to load OnlyOffice api.js from ${script.src}`))
    }
    document.head.appendChild(script)
  })
  return apiLoadingPromise
}

/**
 * 在 {@code placeholderId} 容器里实例化 OO DocEditor。
 *
 * <p>使用 {@code shallowRef} 持有实例（OO 实例结构复杂，不做深响应）。
 */
export function createOnlyOfficeEditor(placeholderId: string, config: OnlyOfficeConfig) {
  if (!window.DocsAPI?.DocEditor) {
    throw new Error('OnlyOffice api.js not loaded')
  }
  const instance = new window.DocsAPI.DocEditor(placeholderId, config)
  return instance
}

/**
 * 销毁编辑器并清理 script tag（用于 Dialog 关闭）。
 */
export function destroyOnlyOfficeEditor(instance: OnlyOfficeDocEditorInstance | null) {
  if (!instance) return
  try {
    instance.destroyEditor()
  } catch (e) {
    // ignore
  }
}

/**
 * 仅暴露常用 OO 编辑器方法的命令式封装，配合 reactive 实例使用。
 *
 * <p>使用方式：
 * <pre>
 *   const { instance, isReady, scrollToPage, getSelectedText } = useOnlyOfficeEditor()
 *   onMounted(async () => {
 *     await loadOnlyOfficeApi()
 *     await initEditor('oo-placeholder', config)
 *     isReady.value = true
 *   })
 *   onBeforeUnmount(() => destroy())
 * </pre>
 */
export function useOnlyOfficeEditor() {
  const instance = shallowRef<OnlyOfficeDocEditorInstance | null>(null)
  const isReady = ref(false)
  const error = ref<string | null>(null)

  async function init(placeholderId: string, config: OnlyOfficeConfig) {
    error.value = null
    try {
      await loadOnlyOfficeApi()
      instance.value = createOnlyOfficeEditor(placeholderId, config)
      isReady.value = true
    } catch (e: any) {
      error.value = e?.message || String(e)
      isReady.value = false
    }
  }

  function destroy() {
    destroyOnlyOfficeEditor(instance.value)
    instance.value = null
    isReady.value = false
  }

  function scrollToPage(pageNumber: number) {
    if (instance.value?.scrollToPage) {
      try {
        instance.value.scrollToPage(pageNumber)
      } catch (e) {
        // ignore — 低版本无此方法
      }
    }
  }

  function setVisibleArea(rect: { x: number; y: number; width: number; height: number }) {
    if (instance.value?.setVisibleArea) {
      try {
        instance.value.setVisibleArea(rect)
      } catch (e) {
        // ignore
      }
    }
  }

  /** 获取当前选区文本（OO 7.0+ 支持） */
  async function getSelectedText(): Promise<string> {
    if (!instance.value?.getSelectedText) return ''
    try {
      return (await instance.value.getSelectedText()) || ''
    } catch {
      return ''
    }
  }

  /** 高亮文本（OO 7.4+ SearchAndHighlight 内部 API；非稳定，仅作最佳努力） */
  function highlightText(text: string) {
    if (!instance.value?.SearchAndHighlight) return
    try {
      instance.value.SearchAndHighlight(text)
    } catch {
      // ignore — 可能版本不支持或文本未找到
    }
  }

  return {
    instance,
    isReady,
    error,
    init,
    destroy,
    scrollToPage,
    setVisibleArea,
    getSelectedText,
    highlightText,
  }
}
