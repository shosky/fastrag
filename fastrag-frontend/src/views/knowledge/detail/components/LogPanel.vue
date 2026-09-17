<script setup lang="ts">
import * as api from '@/api'

const props = defineProps<{
  kbId: string
}>()

// --- 筛选 ---
const activeCategory = ref<string>('all')
const searchKeyword = ref('')

const allLogs = ref<any[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const params: any = { page: 1, pageSize: 100 }
    if (activeCategory.value !== 'all') params.category = activeCategory.value
    const res = await api.getKbLogs(props.kbId, params)
    allLogs.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

watch(activeCategory, refresh)
onMounted(refresh)

const filteredLogs = computed(() => {
  if (!searchKeyword.value) return allLogs.value
  const kw = searchKeyword.value.toLowerCase()
  return allLogs.value.filter((l: any) =>
    (l.target || '').toLowerCase().includes(kw) ||
    (l.detail || '').toLowerCase().includes(kw) ||
    (l.operator || '').toLowerCase().includes(kw),
  )
})

// --- 统计 ---
const stats = computed(() => {
  const all = allLogs.value
  return {
    total: all.length,
    operation: all.filter((l: any) => l.category === 'operation').length,
    retrieval: all.filter((l: any) => l.category === 'retrieval').length,
    publish: all.filter((l: any) => l.category === 'publish').length,
  }
})

// --- 类型配置 ---
const categoryConfig: Record<string, { label: string; color: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  operation: { label: '操作', color: 'primary' },
  retrieval: { label: '检索', color: 'success' },
  publish: { label: '发布', color: 'warning' },
}

const actionLabels: Record<string, string> = {
  file_added: '新增文件',
  file_removed: '删除文件',
  file_updated: '更新文件',
  chunk_added: '新增切片',
  chunk_removed: '删除切片',
  chunk_updated: '更新切片',
  config_changed: '配置变更',
  search: '检索查询',
  version_created: '创建版本',
  submitted: '提交审核',
  approved: '审核通过',
  rejected: '审核驳回',
  published: '版本发布',
  reverted: '版本回退',
}

function getActionLabel(action: string): string {
  return actionLabels[action] || action
}
// ===== 检索日志分析（新增 / 修改 / 删除 / 查询 / 存储）=====
// 后端：GET/POST /api/retrieval/logs、PUT/DELETE /api/retrieval/logs/{id}、GET /api/retrieval/logs/analysis
const activeTab = ref('kb-log')
const rlogLoading = ref(false)
const rlogList = ref<any[]>([])
const rlogKeyword = ref('')
const rlogFilter = ref('')
const rlogAnalysis = ref<any>(null)
const rlogDialog = ref(false)
const rlogEditing = ref(false)
const rlogForm = ref<any>({ id: null, query: '', hasResult: true, hitCount: 0, maxSimilarity: 0, durationMs: 0 })

const filteredRlogs = computed(() =>
  rlogList.value.filter((l: any) => {
    const kwOk = !rlogKeyword.value || (l.query || '').includes(rlogKeyword.value)
    const stOk = !rlogFilter.value || (rlogFilter.value === 'hit' ? l.hasResult : !l.hasResult)
    return kwOk && stOk
  }),
)

async function loadRlogs() {
  rlogLoading.value = true
  try {
    const res: any = await api.getRetrievalLogs({ kbId: props.kbId, page: 1, pageSize: 100 })
    rlogList.value = res?.list || res?.records || res || []
  } catch {
    rlogList.value = []
  } finally {
    rlogLoading.value = false
  }
}

async function loadRlogAnalysis() {
  try {
    rlogAnalysis.value = (await api.getRetrievalLogAnalysis(props.kbId)) as any
  } catch {
    rlogAnalysis.value = null
  }
}

function openRlog(row?: any) {
  rlogEditing.value = !!row
  rlogForm.value = row
    ? { id: row.id, query: row.query, hasResult: row.hasResult !== false, hitCount: row.hitCount ?? 0, maxSimilarity: row.maxSimilarity ?? 0, durationMs: row.durationMs ?? 0 }
    : { id: null, query: '', hasResult: true, hitCount: 0, maxSimilarity: 0, durationMs: 0 }
  rlogDialog.value = true
}

async function saveRlog() {
  if (!rlogForm.value.query) {
    ElMessage.warning('请输入检索词')
    return
  }
  const payload = {
    kbId: props.kbId,
    query: rlogForm.value.query,
    hasResult: !!rlogForm.value.hasResult,
    hitCount: Number(rlogForm.value.hitCount) || 0,
    maxSimilarity: Number(rlogForm.value.maxSimilarity) || 0,
    durationMs: Number(rlogForm.value.durationMs) || 0,
    userId: 'user_admin',
  }
  try {
    if (rlogEditing.value) {
      await api.updateRetrievalLog(rlogForm.value.id, payload)
      ElMessage.success('检索日志已修改')
    } else {
      await api.createRetrievalLog(payload)
      ElMessage.success('检索日志已新增（存储成功）')
    }
    rlogDialog.value = false
    await Promise.all([loadRlogs(), loadRlogAnalysis()])
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function removeRlog(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除检索日志「${row.query}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteRetrievalLog(row.id)
    ElMessage.success('检索日志已删除')
    await Promise.all([loadRlogs(), loadRlogAnalysis()])
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

/** 存储验证：真实走一次检索（检索主链路会自动落日志） */
async function simulateSearchStore() {
  try {
    await api.searchRetrieval({ knowledgeId: props.kbId, query: rlogKeyword.value || '宽带 资费', config: { topK: 5 } } as any)
    ElMessage.success('已执行一次检索，日志已自动落库')
    await Promise.all([loadRlogs(), loadRlogAnalysis()])
  } catch {
    ElMessage.error('检索执行失败')
  }
}

watch(activeTab, (t) => {
  if (t === 'retrieval-log') {
    loadRlogs()
    loadRlogAnalysis()
  }
})
</script>

<template>
  <div class="log-panel">
    <el-tabs v-model="activeTab">
      <!-- ===== 知识库日志（原有）===== -->
      <el-tab-pane label="知识库日志" name="kb-log">
        <!-- 筛选栏 -->
        <div class="log-panel__filter">
      <el-radio-group v-model="activeCategory" size="small">
        <el-radio-button value="all">全部 ({{ stats.total }})</el-radio-button>
        <el-radio-button value="operation">操作 ({{ stats.operation }})</el-radio-button>
        <el-radio-button value="retrieval">检索 ({{ stats.retrieval }})</el-radio-button>
        <el-radio-button value="publish">发布 ({{ stats.publish }})</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索对象/详情/操作人"
        clearable
        size="small"
        style="width: 220px"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 日志表格 -->
    <el-table v-loading="loading" :data="filteredLogs" stripe size="small">
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="categoryConfig[(row as any).category]?.color || 'info'" size="small">
            {{ categoryConfig[(row as any).category]?.label || (row as any).category }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          {{ getActionLabel((row as any).action) }}
        </template>
      </el-table-column>

      <el-table-column label="对象" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as any).target }}
        </template>
      </el-table-column>

      <el-table-column label="详情" min-width="200">
        <template #default="{ row }">
          <div class="log-panel__detail">
            <span>{{ (row as any).detail }}</span>
            <!-- 检索日志扩展信息 -->
            <template v-if="(row as any).category === 'retrieval' && (row as any).extra">
              <span class="log-panel__extra">
                模式: {{ (row as any).extra?.mode }} · TopK: {{ (row as any).extra?.topK }} · 耗时: {{ ((row as any).extra?.duration / 1000)?.toFixed(1) }}s
              </span>
            </template>
            <!-- diff 信息 -->
            <template v-if="(row as any).extra?.oldValue">
              <span class="log-panel__diff">
                <span class="log-panel__diff-old">{{ (row as any).extra?.oldValue }}</span>
                →
                <span class="log-panel__diff-new">{{ (row as any).extra?.newValue }}</span>
              </span>
            </template>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="操作人" width="90">
        <template #default="{ row }">
          {{ (row as any).operator }}
        </template>
      </el-table-column>

      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag
            v-if="(row as any).status"
            :type="(row as any).status === 'success' ? 'success' : (row as any).status === '已发布' ? 'primary' : 'info'"
            size="small"
          >
            {{ (row as any).status }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          {{ (row as any).timestamp || (row as any).createdAt }}
        </template>
      </el-table-column>
    </el-table>

        <el-empty v-if="filteredLogs.length === 0 && !loading" description="暂无日志记录" />
      </el-tab-pane>

      <!-- ===== 检索日志分析（新增/修改/删除/查询/存储）===== -->
      <el-tab-pane label="检索日志分析" name="retrieval-log">
        <!-- 分析指标 -->
        <div class="log-panel__filter" style="justify-content: flex-start; gap: 16px">
          <el-tag size="small" type="info">总检索次数：{{ rlogAnalysis?.totalQueries ?? 0 }}</el-tag>
          <el-tag size="small" type="warning">无结果率：{{ rlogAnalysis?.noResultRate ?? 0 }}%</el-tag>
          <el-tag size="small">平均耗时：{{ rlogAnalysis?.avgLatencyMs ?? 0 }} ms</el-tag>
          <el-tag size="small" type="success">平均命中：{{ rlogAnalysis?.avgHitCount ?? 0 }} 条</el-tag>
        </div>

        <!-- 工具条：查询 + 新增 + 存储验证 -->
        <div class="log-panel__filter">
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input v-model="rlogKeyword" placeholder="按检索词查询" clearable size="small" style="width: 200px">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="rlogFilter" placeholder="结果筛选" clearable size="small" style="width: 130px">
              <el-option label="有结果" value="hit" />
              <el-option label="无结果" value="miss" />
            </el-select>
            <span style="color: var(--el-text-color-secondary); font-size: 12px">共 {{ filteredRlogs.length }} 条</span>
          </div>
          <div style="display: flex; gap: 8px">
            <el-button size="small" @click="simulateSearchStore">执行检索并落库（验证存储）</el-button>
            <el-button size="small" @click="loadRlogs(); loadRlogAnalysis()">刷新</el-button>
            <el-button size="small" type="primary" @click="openRlog()">
              <el-icon><Plus /></el-icon>新增检索日志
            </el-button>
          </div>
        </div>

        <el-table v-loading="rlogLoading" :data="filteredRlogs" stripe size="small">
          <el-table-column prop="query" label="检索词" min-width="200" show-overflow-tooltip />
          <el-table-column label="是否有结果" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.hasResult ? 'success' : 'danger'" size="small">{{ row.hasResult ? '有结果' : '无结果' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hitCount" label="命中数" width="80" align="center" />
          <el-table-column label="最高相似度" width="110" align="center">
            <template #default="{ row }">{{ row.maxSimilarity ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="耗时(ms)" width="90" align="center">
            <template #default="{ row }">{{ row.durationMs ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="userId" label="检索人" width="110" />
          <el-table-column prop="createdAt" label="时间" min-width="160" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openRlog(row)">修改</el-button>
              <el-button link type="danger" size="small" @click="removeRlog(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="filteredRlogs.length === 0 && !rlogLoading" description="暂无检索日志（在「检索测试」执行一次搜索即会自动落库）" :image-size="60" />
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/修改检索日志 -->
    <el-dialog v-model="rlogDialog" :title="rlogEditing ? '修改检索日志' : '新增检索日志'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="检索词">
          <el-input v-model="rlogForm.query" placeholder="如：宽带资费" />
        </el-form-item>
        <el-form-item label="是否有结果">
          <el-switch v-model="rlogForm.hasResult" />
        </el-form-item>
        <el-form-item label="命中数">
          <el-input-number v-model="rlogForm.hitCount" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="最高相似度">
          <el-input-number v-model="rlogForm.maxSimilarity" :min="0" :max="1" :step="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="耗时(ms)">
          <el-input-number v-model="rlogForm.durationMs" :min="0" :max="60000" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rlogDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRlog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.log-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.log-panel__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-base;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-sm $spacing-base;
}

.log-panel__detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log-panel__extra {
  font-size: 11px;
  color: $text-secondary;
}

.log-panel__diff {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.log-panel__diff-old {
  color: $color-danger;
  text-decoration: line-through;
}

.log-panel__diff-new {
  color: $color-success;
}
</style>
