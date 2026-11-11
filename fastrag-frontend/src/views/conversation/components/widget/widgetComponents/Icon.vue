<script setup lang="ts">
import { computed } from 'vue'
import {
  Postcard,
  Star,
  User,
  Goods,
  Document,
  DataLine,
  ChatLineRound,
  Box,
  Coin,
  Connection,
  MagicStick,
  Checked,
  Search,
  Setting,
  Bell,
  Collection,
  Cpu,
  TrendCharts,
  Files,
  Promotion,
  WarningFilled,
  CircleCheckFilled,
} from '@element-plus/icons-vue'
import { propString, type WidgetComponentProps } from '../registry'

// Icon：图标（Element Plus 图标名 → 组件映射；未知名回退 Postcard——与资产 icon
// 为空回退同一缺省，MOD ## 视觉）
const props = defineProps<WidgetComponentProps>()

const ICON_MAP: Record<string, unknown> = {
  Postcard, Star, User, Goods, Document, DataLine, ChatLineRound, Box, Coin,
  Connection, MagicStick, Checked, Search, Setting, Bell, Collection, Cpu,
  TrendCharts, Files, Promotion, WarningFilled, CircleCheckFilled,
}

const name = propString(props.node, 'name')
const icon = computed(() => ICON_MAP[name] || Postcard)
const size = typeof props.node?.props?.size === 'number' ? props.node.props.size : 16
</script>

<template>
  <el-icon class="widget-w-icon" :size="size"><component :is="icon" /></el-icon>
</template>

<style scoped>
.widget-w-icon {
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}
</style>
