import type { Directive, DirectiveBinding } from 'vue'
import type { Permission } from '@/types/auth'
import { useUserStore } from '@/stores/user'
import { ROLE_PERMISSIONS } from '@/types/auth'

/**
 * v-permission 指令
 *
 * 用法：
 *   <el-button v-permission="'kb:upload'">上传</el-button>
 *   <el-button v-permission="'admin:access'">管理后台</el-button>
 *
 * 无权限时元素会被移除（不占位）。
 * 超级管理员（permissions=['*']）自动通过。
 */
function checkPermission(binding: DirectiveBinding<Permission | Permission[]>): boolean {
  const userStore = useUserStore()
  const perms = userStore.permissions || []

  // 超级管理员通配
  if (perms.includes('*')) return true

  const required = Array.isArray(binding.value) ? binding.value : [binding.value]
  return required.some((p) => perms.includes(p))
}

export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<Permission | Permission[]>) {
    if (!checkPermission(binding)) {
      el.parentNode?.removeChild(el)
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding<Permission | Permission[]>) {
    if (!checkPermission(binding)) {
      el.parentNode?.removeChild(el)
    }
  },
}
