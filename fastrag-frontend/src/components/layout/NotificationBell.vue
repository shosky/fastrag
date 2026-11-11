<script lang="ts">
/**
 * 徽标文案：0/失败 → ''（隐藏）；1–99 → 数值；≥100 → '99+'。
 * 纯函数导出供单测直测（T4.1），组件内经 computed 消费。
 */
export function formatBadge(n: unknown): string {
  if (typeof n !== 'number' || !Number.isFinite(n)) return ''
  if (n <= 0) return ''
  if (n >= 100) return '99+'
  return String(n)
}
</script>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUnreadNotificationCount } from '@/api'

/**
 * 顶栏通知铃铛（nav-shell 链，STATE-101）。
 * 拉取时机：挂载（进入平台）一次 + 路由离开 /notification 时重拉；
 * 未读数仅本组件持有（client 状态，服务器是数据源而非状态属主）。
 * 失败静默：无徽标、无报错弹窗（api 请求层已有统一错误处理时由其兜底，本组件不二次弹窗）。
 */
const route = useRoute()
const router = useRouter()

/** null = 尚未成功获取（含失败），渲染为无徽标 */
const unreadCount = ref<number | null>(null)

const badgeText = computed(() => formatBadge(unreadCount.value))

async function refresh() {
  try {
    const res: unknown = await getUnreadNotificationCount()
    unreadCount.value = typeof res === 'number' ? res : null
  } catch {
    unreadCount.value = null
  }
}

onMounted(refresh)

// 离开通知中心时重拉（页内已读/删除操作因此反映到徽标）
watch(
  () => route.path,
  (to, from) => {
    if (from && from.startsWith('/notification') && !to.startsWith('/notification')) {
      refresh()
    }
  }
)

function handleClick() {
  router.push('/notification')
}
</script>

<template>
  <div class="notification-bell" title="通知中心" @click="handleClick">
    <el-badge :value="badgeText" :hidden="badgeText === ''" :max="99" class="notification-bell__badge">
      <el-icon :size="18"><Bell /></el-icon>
    </el-badge>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.notification-bell {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: $text-regular;
  transition: color 0.2s ease;

  &:hover {
    color: $color-primary;
  }

  &__badge {
    display: flex;
    align-items: center;
  }
}
</style>
