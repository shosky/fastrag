<script setup lang="ts">
/**
 * 知识配置（机器人管理-知识配置）：面向机器人的知识范围与召回参数配置。
 * 存储：复用知识库的 retrievalConfig JSON（kb.retrieval_config）内的 knowledgeConfigs 数组，
 * 通过 PUT /api/kb/{kbId} 持久化，无需新增表。
 * 覆盖功能点：新增 / 导入 / 判断 / 编辑 / 修改 / 删除 / 查看
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ kbId: string }>()

const loading = ref(false)
const saving = ref(false)
const configs = ref<any[]>([])
const keyword = ref('')
const sourceFilter = ref('')
const kbDetail = ref<any>({})
/** 除 knowledgeConfigs 外的其它检索配置，保存时原样带回避免覆盖 */
let otherRetrievalConfig: Record<string, unknown> = {}

const SOURCE_OPTIONS = [
  { value: 'knowledge', label: '知识条目' },
  { value: 'qa', label: '问答对' },
  { value: 'document', label: '文档分块' },
  { value: 'table', label: '表格知识' },
]
const MODE_OPTIONS = [
  { value: 'hybrid', label: '混合检索' },
  { value: 'vector', label: '语义（向量）' },
  { value: 'fulltext', label: '关键词（词法）' },
]

const filtered = computed(() =>
  configs.value.filter((c: any) => {
    const kwOk = !keyword.value || (c.name || '').includes(keyword.value) || (c.scope || '').includes(keyword.value)
    const sfOk = !sourceFilter.value || c.source === sourceFilter.value
    return kwOk && sfOk
  }),
)

function parseRetrievalConfig(raw: any): Record<string, unknown> {
  if (!raw) return {}
  if (typeof raw === 'object') return raw as Record<string, unknown>
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

async function loadConfigs() {
  loading.value = true
  try {
    const detail: any = await api.getKnowledgeBaseDetail(props.kbId)
    kbDetail.value = detail || {}
    const rc = parseRetrievalConfig(detail?.retrievalConfig)
    otherRetrievalConfig = { ...rc }
    delete otherRetrievalConfig.knowledgeConfigs
    const list = (rc as any).knowledgeConfigs
    configs.value = Array.isArray(list) ? list : []
  } catch {
    configs.value = []
  } finally {
    loading.value = false
  }
}

/** 保存整个配置列表（编辑/新增/删除/导入后调用） */
async function persist() {
  saving.value = true
  try {
    await api.updateKnowledgeBase(props.kbId, {
      name: kbDetail.value.name,
      retrievalConfig: { ...otherRetrievalConfig, knowledgeConfigs: configs.value },
    })
    return true
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
    return false
  } finally {
    saving.value = false
  }
}

// ===== 新增 / 编辑 / 修改 =====
const dialogVisible = ref(false)
const editingIndex = ref(-1)
const form = ref<any>({})
function openCreate() {
  editingIndex.value = -1
  form.value = {
    name: '',
    source: 'knowledge',
    scope: '',
    scopeType: 'category',
    mode: 'hybrid',
    topK: 5,
    similarityThreshold: 0.3,
    enabled: true,
    remark: '',
  }
  dialogVisible.value = true
}
function openEdit(row: any) {
  editingIndex.value = configs.value.findIndex((c) => c.id === row.id)
  form.value = { ...row }
  dialogVisible.value = true
}
async function saveConfig() {
  if (!form.value.name) {
    ElMessage.warning('请输入配置名称')
    return
  }
  if (editingIndex.value >= 0) {
    configs.value.splice(editingIndex.value, 1, { ...form.value })
  } else {
    configs.value.push({ ...form.value, id: `kc_${Date.now()}`, createdAt: new Date().toLocaleString('zh-CN') })
  }
  if (await persist()) {
    ElMessage.success(editingIndex.value >= 0 ? '知识配置已修改' : '知识配置已新增')
    dialogVisible.value = false
  }
}

// ===== 删除 =====
async function removeConfig(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除知识配置「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  configs.value = configs.value.filter((c) => c.id !== row.id)
  if (await persist()) ElMessage.success('知识配置已删除')
}

// ===== 查看 =====
const viewVisible = ref(false)
const viewRow = ref<any>({})
function openView(row: any) {
  viewRow.value = row
  viewVisible.value = true
}

// ===== 导入 =====
const importInput = ref<HTMLInputElement>()
const importing = ref(false)
function triggerImport() {
  importInput.value?.click()
}
async function handleImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const text = await file.text()
    const parsed = JSON.parse(text)
    const items: any[] = Array.isArray(parsed) ? parsed : parsed?.knowledgeConfigs || []
    if (!items.length) throw new Error('文件中未找到配置数组')
    for (const it of items) {
      configs.value.push({
        id: `kc_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
        name: it.name || '导入配置',
        source: it.source || 'knowledge',
        scope: it.scope || '',
        scopeType: it.scopeType || 'category',
        mode: it.mode || 'hybrid',
        topK: Number(it.topK) || 5,
        similarityThreshold: Number(it.similarityThreshold ?? 0.3),
        enabled: it.enabled !== false,
        remark: it.remark || '导入',
        createdAt: new Date().toLocaleString('zh-CN'),
      })
    }
    if (await persist()) ElMessage.success(`已导入 ${items.length} 条知识配置`)
  } catch (err: any) {
    ElMessage.error('导入失败：' + (err?.message || '文件需为 JSON 数组'))
  } finally {
    importing.value = false
    input.value = ''
  }
}

/** 导出为可再次导入的 JSON（便于演示导入） */
function handleExport() {
  const blob = new Blob([JSON.stringify(configs.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `knowledge-configs_${props.kbId}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出当前知识配置（可用于验证导入）')
}

// ===== 判断（配置有效性校验） =====
const judgeVisible = ref(false)
const judgeResult = ref<any>(null)
function handleJudge(row?: any) {
  const targets = row ? [row] : configs.value
  const issues: string[] = []
  const nameCount: Record<string, number> = {}
  for (const c of configs.value) nameCount[c.name] = (nameCount[c.name] || 0) + 1
  for (const c of targets) {
    if (!c.name || !String(c.name).trim()) issues.push(`配置「${c.name || '(未命名)'}」：名称为空`)
    if (!(Number(c.topK) > 0)) issues.push(`配置「${c.name}」：召回 TopK 必须大于 0`)
    if (Number(c.similarityThreshold) < 0 || Number(c.similarityThreshold) > 1) issues.push(`配置「${c.name}」：相似度阈值需在 0~1 之间`)
    if (nameCount[c.name] > 1) issues.push(`配置「${c.name}」：名称重复（${nameCount[c.name]} 条同名）`)
    if (!c.scope) issues.push(`配置「${c.name}」：未设置知识范围（分类/标签）`)
  }
  judgeResult.value = {
    total: targets.length,
    passed: targets.length - new Set(issues.map((i) => i.split('：')[0])).size,
    issues,
    verdict: issues.length === 0 ? '配置校验通过，可用于机器人问答' : `发现 ${issues.length} 项问题，建议修正后再启用`,
    checkedAt: new Date().toLocaleString('zh-CN'),
  }
  judgeVisible.value = true
}

onMounted(loadConfigs)
</script>

<template>
  <div class="card-panel" v-loading="loading">
    <div class="section-header">
      <div class="section-title">知识配置（机器人知识范围与召回参数）</div>
      <div style="display: flex; gap: 8px">
        <el-button size="small" @click="handleExport">导出（用于导入验证）</el-button>
        <el-button size="small" :loading="importing" @click="triggerImport">导入知识配置</el-button>
        <input ref="importInput" type="file" accept=".json,application/json" style="display: none" @change="handleImport" />
        <el-button size="small" @click="handleJudge()">判断（校验配置）</el-button>
        <el-button size="small" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增知识配置
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="按配置名称/范围查询" clearable size="small" style="width: 220px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="sourceFilter" placeholder="知识来源" clearable size="small" style="width: 140px">
        <el-option v-for="s in SOURCE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <span style="color: var(--el-text-color-secondary); font-size: 12px">共 {{ filtered.length }} 条知识配置</span>
    </div>

    <el-table :data="filtered" stripe size="small">
      <el-table-column prop="name" label="配置名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="知识来源" width="110">
        <template #default="{ row }">{{ SOURCE_OPTIONS.find((s) => s.value === row.source)?.label || row.source }}</template>
      </el-table-column>
      <el-table-column label="知识范围" min-width="180">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.scopeType === 'tag' ? '标签' : '分类' }}</el-tag>
          {{ row.scope || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="检索模式" width="110">
        <template #default="{ row }">{{ MODE_OPTIONS.find((m) => m.value === row.mode)?.label || row.mode }}</template>
      </el-table-column>
      <el-table-column prop="topK" label="TopK" width="70" align="center" />
      <el-table-column label="相似度阈值" width="100" align="center">
        <template #default="{ row }">{{ row.similarityThreshold }}</template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" @click="handleJudge(row)">判断</el-button>
          <el-button link type="danger" size="small" @click="removeConfig(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!filtered.length && !loading" description="暂无知识配置，点击「新增知识配置」或「导入知识配置」" :image-size="60" />

    <!-- 新增/编辑/修改 -->
    <el-dialog v-model="dialogVisible" :title="editingIndex >= 0 ? '编辑知识配置（修改）' : '新增知识配置'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="配置名称">
          <el-input v-model="form.name" placeholder="如：宽带业务知识范围" />
        </el-form-item>
        <el-form-item label="知识来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option v-for="s in SOURCE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围类型">
          <el-radio-group v-model="form.scopeType">
            <el-radio-button value="category">按分类</el-radio-button>
            <el-radio-button value="tag">按标签</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="知识范围">
          <el-input v-model="form.scope" placeholder="如：宽带 / 宽带业务（多个用逗号分隔）" />
        </el-form-item>
        <el-form-item label="检索模式">
          <el-select v-model="form.mode" style="width: 100%">
            <el-option v-for="m in MODE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="召回 TopK">
          <el-input-number v-model="form.topK" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="相似度阈值">
          <el-input-number v-model="form.similarityThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看 -->
    <el-dialog v-model="viewVisible" title="查看知识配置" width="520px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="配置名称">{{ viewRow.name }}</el-descriptions-item>
        <el-descriptions-item label="知识来源">{{ SOURCE_OPTIONS.find((s) => s.value === viewRow.source)?.label || viewRow.source }}</el-descriptions-item>
        <el-descriptions-item label="知识范围">{{ viewRow.scopeType === 'tag' ? '标签' : '分类' }}：{{ viewRow.scope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检索模式">{{ MODE_OPTIONS.find((m) => m.value === viewRow.mode)?.label || viewRow.mode }}</el-descriptions-item>
        <el-descriptions-item label="召回 TopK">{{ viewRow.topK }}</el-descriptions-item>
        <el-descriptions-item label="相似度阈值">{{ viewRow.similarityThreshold }}</el-descriptions-item>
        <el-descriptions-item label="启用">{{ viewRow.enabled ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewRow.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewRow.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
        <el-button type="primary" @click="viewVisible = false; openEdit(viewRow)">编辑</el-button>
      </template>
    </el-dialog>

    <!-- 判断结果 -->
    <el-dialog v-model="judgeVisible" title="知识配置判断结果" width="520px">
      <div v-if="judgeResult">
        <el-alert :type="judgeResult.issues.length ? 'warning' : 'success'" :closable="false" :title="judgeResult.verdict" show-icon />
        <div style="margin-top: 12px; font-size: 13px">
          <p>校验配置数：{{ judgeResult.total }}</p>
          <p>校验时间：{{ judgeResult.checkedAt }}</p>
          <div v-if="judgeResult.issues.length">
            <p style="font-weight: 600">问题明细：</p>
            <ul>
              <li v-for="(it, i) in judgeResult.issues" :key="i" style="margin: 4px 0">{{ it }}</li>
            </ul>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="judgeVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>
