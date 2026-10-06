<script setup lang="ts">
import { computed } from 'vue'

// ===========================================================================
// 数据库查询结果专用渲染器（app-chat-optimize 二批）
//
// 消费结构化 data（后端 DatabaseToolExecutor structured 透传）：
//   { display_type:'database_query', columns: string[], rows: {列名:值}[],
//     row_count: number, elapsed_ms: number }
// 三分支：正常表格 / 空结果虚线空态 / 数据异常（契约外形状）。
// 安全边界：不显示 SQL（任务约定）。
// ===========================================================================

/** 行对象：列名 → 单元格值（后端 rows 为 Map 序列化，值类型开放） */
type TableRow = Record<string, unknown>

const props = defineProps<{
  data?: Record<string, unknown>
}>()

const columns = computed<string[]>(() => {
  const raw = props.data?.columns
  return Array.isArray(raw) ? raw.map(c => (typeof c === 'string' ? c : String(c))) : []
})
const rows = computed<TableRow[]>(() => {
  const raw = props.data?.rows
  return Array.isArray(raw) ? (raw as TableRow[]) : []
})

/** 数据异常：columns/rows 任一缺位或非数组（后端契约外形状） */
const isAbnormal = computed(
  () => !Array.isArray(props.data?.columns) || !Array.isArray(props.data?.rows)
)
/** 空结果：结构正常但零行 */
const isEmpty = computed(() => !isAbnormal.value && rows.value.length === 0)

const elapsedMs = computed(() => {
  const v = props.data?.elapsed_ms
  return typeof v === 'number' ? v : null
})

/** 单元格文本化：null/undefined 显示 —，对象 JSON 压缩，其余 String() */
function cellText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<template>
  <div class="db-query-table">
    <!-- 异常：契约外形状 -->
    <div v-if="isAbnormal" class="dbq-abnormal">结果数据格式异常，请查看原始输出</div>

    <!-- 空结果：虚线空态 -->
    <div v-else-if="isEmpty" class="dbq-empty">
      <el-icon :size="20"><Coin /></el-icon>
      <span>查询结果为空（0 行）</span>
    </div>

    <!-- 正常：columns→thead，rows→tbody；容器横向滚动，单元格超宽省略 -->
    <template v-else>
      <div class="dbq-scroll">
        <table class="dbq-table">
          <thead>
            <tr>
              <th v-for="col in columns" :key="col" class="dbq-th" :title="col">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in rows" :key="ri">
              <td
                v-for="col in columns"
                :key="col"
                class="dbq-td"
                :title="cellText(row[col])"
              >
                {{ cellText(row[col]) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="elapsedMs !== null" class="dbq-meta">共 {{ rows.length }} 行 · 耗时 {{ elapsedMs }}ms</div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
// 配色沿用 MessageBubble 体系（不引入 variables.scss，沿全局注入约定）
$primary: #046a82;
$text-primary: #151616;
$text-muted: #979999;
$border: #eef0f0;
$bg-hover: #f5f7f7;

.db-query-table { font-size: 12px; }

.dbq-scroll {
  overflow-x: auto;
  border: 1px solid $border;
  border-radius: 4px;
  background: #fff;
}

.dbq-table {
  border-collapse: collapse;
  width: 100%;
  min-width: 100%;
}

.dbq-th {
  position: sticky;
  top: 0;
  background: $bg-hover;
  color: $text-primary;
  font-weight: 500;
  text-align: left;
  padding: 6px 10px;
  border-bottom: 1px solid $border;
  white-space: nowrap;
}

.dbq-td {
  padding: 6px 10px;
  border-bottom: 1px solid $border;
  color: $text-primary;
  white-space: nowrap;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

tbody tr {
  &:hover { background: $bg-hover; }
  &:last-child .dbq-td { border-bottom: none; }
}

.dbq-meta {
  margin-top: 4px;
  font-size: 11px;
  color: $text-muted;
}

.dbq-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 12px;
  border: 1px dashed $border;
  border-radius: 4px;
  color: $text-muted;
  font-size: 12px;
}

.dbq-abnormal {
  padding: 10px 12px;
  border: 1px dashed $border;
  border-radius: 4px;
  color: $text-muted;
  font-size: 12px;
}
</style>
