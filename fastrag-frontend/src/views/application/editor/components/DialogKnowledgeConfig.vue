<script setup lang="ts">
/**
 * 对话知识（应用管理）：知识新增 / 知识导入 / 智能推荐-判断 / 对话流配置-编辑
 * - 知识新增、知识导入落到应用绑定知识库（默认第一个绑定库，可切换）
 * - 智能推荐-判断：关键词判断（/keywords/judge）+ 推荐列表（/keywords/recommend）
 * - 对话流配置：绑定业务流程（/apps/{id}/workflow-config）+ 绑定知识库 + 检索参数/开场白
 *   （检索参数存 app_config.kb_settings，先读后合并，避免覆盖已有设置）
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: any }>()

const kbOptions = ref<any[]>([])
const kbId = ref('')
const workflowOptions = ref<any[]>([])
const kbBindings = ref<any[]>([])
const flowForm = ref<any>({ workflowIds: [] as string[], retrievalMode: 'hybrid', topK: 5, opening: '您好，请问有什么可以帮您？', boundKbIds: [] as string[] })
const flowSaving = ref(false)

const appId = computed(() => props.appInfo?.id || '')

async function loadKbOptions() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbOptions.value = Array.isArray(res) ? res : res?.list || res?.records || []
    const bindings: any = await api.getAppKbBindings(appId.value)
    kbBindings.value = Array.isArray(bindings) ? bindings : bindings?.list || []
    const boundIds = kbBindings.value.map((b: any) => b.kbId || b.id).filter(Boolean)
    flowForm.value.boundKbIds = boundIds
    if (!kbId.value) kbId.value = boundIds[0] || kbOptions.value[0]?.id || ''
  } catch {
    kbOptions.value = []
  }
}

async function loadFlowConfig() {
  try {
    const [flows, cfg] = await Promise.all([
      api.getWorkflows().catch(() => []),
      api.getAppWorkflowConfig(appId.value).catch(() => null),
    ])
    workflowOptions.value = Array.isArray(flows) ? flows : (flows as any)?.list || (flows as any)?.records || []
    flowForm.value.workflowIds = (cfg as any)?.workflowIds || []
    const settings: any = await api.getAppKbSettings(appId.value).catch(() => null)
    if (settings?.dialogFlow) {
      flowForm.value.retrievalMode = settings.dialogFlow.retrievalMode || 'hybrid'
      flowForm.value.topK = settings.dialogFlow.topK ?? 5
      flowForm.value.opening = settings.dialogFlow.opening || flowForm.value.opening
    }
  } catch {
    /* ignore */
  }
}

// ===== 知识新增 =====
const addForm = ref<any>({ title: '', category: '', tags: '', content: '' })
const addSaving = ref(false)
async function submitKnowledge() {
  if (!kbId.value) { ElMessage.warning('请先选择知识库'); return }
  if (!addForm.value.title.trim() || !addForm.value.content.trim()) { ElMessage.warning('标题与内容必填'); return }
  addSaving.value = true
  try {
    await api.createKnowledge(kbId.value, {
      title: addForm.value.title,
      category: addForm.value.category,
      tags: addForm.value.tags ? String(addForm.value.tags).split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [],
      content: addForm.value.content,
      status: 'published',
      source: 'manual',
    })
    ElMessage.success('知识已新增')
    addForm.value = { title: '', category: '', tags: '', content: '' }
    await loadKbKnowledge()
  } catch (e: any) {
    ElMessage.error('新增失败：' + (e?.message || ''))
  } finally {
    addSaving.value = false
  }
}

// ===== 知识导入 =====
const importText = ref('')
const importing = ref(false)
const importInput = ref<HTMLInputElement>()
const kbKnowledge = ref<any[]>([])
async function loadKbKnowledge() {
  if (!kbId.value) return
  try {
    const res: any = await api.getKnowledgeList(kbId.value)
    kbKnowledge.value = Array.isArray(res) ? res : res?.list || res?.records || []
  } catch { kbKnowledge.value = [] }
}
function triggerImport() { importInput.value?.click() }
async function importFromFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try { importText.value = await file.text() } catch { ElMessage.error('读取文件失败') }
  finally { input.value = '' }
  await doImport()
}
async function doImport() {
  if (!kbId.value) { ElMessage.warning('请先选择知识库'); return }
  if (!importText.value.trim()) { ElMessage.warning('请粘贴或选择 JSON 数组内容'); return }
  let items: any[] = []
  try {
    const parsed = JSON.parse(importText.value)
    items = Array.isArray(parsed) ? parsed : parsed?.items || []
  } catch {
    ElMessage.error('内容不是合法 JSON 数组'); return
  }
  if (!items.length) { ElMessage.warning('未解析到可导入的知识'); return }
  importing.value = true
  let okCount = 0
  try {
    for (const it of items) {
      if (!it?.title) continue
      await api.createKnowledge(kbId.value, {
        title: it.title,
        category: it.category,
        tags: Array.isArray(it.tags) ? it.tags : (it.tags ? String(it.tags).split(/[,，]/) : []),
        content: it.content || it.summary || it.title,
        status: it.status || 'published',
        source: 'import',
      })
      okCount++
    }
    ElMessage.success(`已导入 ${okCount} 条知识`)
    importText.value = ''
    await loadKbKnowledge()
  } catch (e: any) {
    ElMessage.error(`导入中断（已成功 ${okCount} 条）：` + (e?.message || ''))
  } finally {
    importing.value = false
  }
}
function useImportSample() {
  importText.value = JSON.stringify([
    { title: '宽带提速办理说明', category: '宽带', tags: ['宽带', '提速'], content: '用户可通过 APP 或营业厅申请宽带提速，次月生效，提速当月按新资费计费。' },
    { title: '5G 副卡办理规则', category: '5G', tags: ['5G', '副卡'], content: '199 元档主卡可办理两张副卡，副卡 10 元/月，共享主卡流量。' },
  ], null, 2)
}

// ===== 智能推荐-判断 =====
const recQuery = ref('')
const recLoading = ref(false)
const recJudge = ref<any>(null)
const recList = ref<any[]>([])
async function handleRecommend() {
  if (!kbId.value) { ElMessage.warning('请先选择知识库'); return }
  if (!recQuery.value.trim()) { ElMessage.warning('请输入用户问题'); return }
  recLoading.value = true
  try {
    const [j, rec] = await Promise.all([
      api.judgeKeywords(kbId.value, recQuery.value).catch(() => null),
      api.getKeywordRecommendations(kbId.value, recQuery.value, 8).catch(() => []),
    ])
    recJudge.value = j
    recList.value = Array.isArray(rec) ? rec : (rec as any)?.list || []
  } catch (e: any) {
    ElMessage.error('推荐失败：' + (e?.message || ''))
  } finally {
    recLoading.value = false
  }
}

// ===== 对话流配置-编辑 =====
async function saveFlowConfig() {
  if (!appId.value) { ElMessage.warning('缺少应用信息'); return }
  flowSaving.value = true
  try {
    await api.saveAppWorkflowConfig(appId.value, { workflowIds: flowForm.value.workflowIds })
    // 合并写入检索参数（保留既有 kb_settings 其它键）
    const current: any = await api.getAppKbSettings(appId.value).catch(() => ({}))
    await api.saveAppKbSettings(appId.value, {
      ...(current || {}),
      dialogFlow: { retrievalMode: flowForm.value.retrievalMode, topK: flowForm.value.topK, opening: flowForm.value.opening },
    })
    ElMessage.success('对话流配置已保存')
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    flowSaving.value = false
  }
}
async function reloadFlowConfig() {
  await loadFlowConfig()
  ElMessage.success('已读取当前对话流配置')
}

onMounted(async () => {
  await Promise.all([loadKbOptions(), loadFlowConfig()])
  await loadKbKnowledge()
})
</script>

<template>
  <div class="config-section dk-config">
    <h3>对话知识</h3>
    <p class="desc">在应用内维护对话用知识（新增/导入）、判断智能推荐，并配置对话流（业务流程绑定 + 检索参数 + 开场白）。</p>

    <div class="dk-toolbar">
      <span>目标知识库：</span>
      <el-select v-model="kbId" placeholder="选择知识库" size="small" style="width: 260px" @change="loadKbKnowledge">
        <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <span class="dk-hint">共 {{ kbKnowledge.length }} 条知识</span>
    </div>

    <el-tabs>
      <!-- 知识新增 -->
      <el-tab-pane label="知识新增">
        <el-form label-width="90px" size="small" style="max-width: 720px">
          <el-form-item label="标题"><el-input v-model="addForm.title" placeholder="如：宽带提速办理说明" /></el-form-item>
          <el-form-item label="分类/标签">
            <el-input v-model="addForm.category" placeholder="分类" style="width: 200px" />
            <el-input v-model="addForm.tags" placeholder="标签，逗号分隔" style="width: 280px; margin-left: 10px" />
          </el-form-item>
          <el-form-item label="内容"><el-input v-model="addForm.content" type="textarea" :rows="5" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="addSaving" @click="submitKnowledge">保存知识</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 知识导入 -->
      <el-tab-pane label="知识导入">
        <div style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
          <el-button size="small" @click="triggerImport">选择 JSON 文件</el-button>
          <el-button size="small" @click="useImportSample">填充示例数据</el-button>
          <el-button size="small" type="primary" :loading="importing" @click="doImport">导入</el-button>
          <input ref="importInput" type="file" accept=".json,application/json" style="display:none" @change="importFromFile" />
          <span class="dk-hint">支持 JSON 数组：[{ title, category, tags[], content, status }]</span>
        </div>
        <el-input v-model="importText" type="textarea" :rows="8" placeholder='[{"title":"宽带提速办理说明","category":"宽带","content":"…"}]' />
      </el-tab-pane>

      <!-- 智能推荐-判断 -->
      <el-tab-pane label="智能推荐-判断">
        <div style="display:flex;gap:8px;margin-bottom:10px;align-items:center">
          <el-input v-model="recQuery" placeholder="输入用户问题，如：宽带怎么退款" size="small" style="width: 320px" @keyup.enter="handleRecommend" />
          <el-button size="small" type="primary" :loading="recLoading" @click="handleRecommend">判断并推荐</el-button>
        </div>
        <div v-if="recJudge" style="margin-bottom:10px">
          <el-tag :type="recJudge.matched ? 'success' : 'info'" size="small">
            {{ recJudge.matched ? '已命中配置关键词' : '未命中配置关键词' }}
          </el-tag>
          <span v-for="(k, i) in (recJudge.keywords || [])" :key="i" style="margin-left:8px">
            <el-tag size="small" :type="k.source === 'standard' ? 'primary' : 'warning'" effect="plain">
              {{ k.source === 'standard' ? '标准问法' : '问答对关键词' }}
            </el-tag>
            <span style="margin-left:4px;font-size:12px">{{ k.text }}</span>
            <span style="margin-left:4px;color:#909399;font-size:12px">置信度 {{ k.score }}</span>
          </span>
        </div>
        <div v-if="recList.length">
          <div style="font-size:13px;font-weight:600;margin-bottom:6px">推荐知识点（{{ recList.length }}）</div>
          <el-table :data="recList" size="small" stripe>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ row.type === 'standard' ? '标准问法' : '相似问法' }}</template>
            </el-table-column>
            <el-table-column prop="text" label="推荐内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="score" label="得分" width="90" align="center" />
          </el-table>
        </div>
        <el-empty v-else-if="recJudge" description="暂无推荐内容" :image-size="50" />
      </el-tab-pane>

      <!-- 对话流配置-编辑 -->
      <el-tab-pane label="对话流配置">
        <el-form label-width="120px" size="small" style="max-width: 720px">
          <el-form-item label="绑定业务流程">
            <el-select v-model="flowForm.workflowIds" multiple filterable placeholder="选择要绑定的业务流程" style="width: 100%">
              <el-option v-for="wf in workflowOptions" :key="wf.id" :label="wf.name" :value="wf.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="检索模式">
            <el-select v-model="flowForm.retrievalMode" style="width: 200px">
              <el-option label="混合检索" value="hybrid" />
              <el-option label="语义（向量）" value="vector" />
              <el-option label="关键词（词法）" value="fulltext" />
            </el-select>
            <span style="margin-left:12px">召回 TopK</span>
            <el-input-number v-model="flowForm.topK" :min="1" :max="20" style="margin-left:8px" />
          </el-form-item>
          <el-form-item label="开场白">
            <el-input v-model="flowForm.opening" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="已绑定知识库">
            <span>{{ kbBindings.length ? kbBindings.map((b: any) => b.kbName || b.name || b.kbId).join('、') : '（未绑定，可在「知识库配置」页绑定）' }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="flowSaving" @click="saveFlowConfig">保存对话流配置</el-button>
            <el-button @click="reloadFlowConfig">读取当前配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
.dk-config {
  .dk-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 12px 0;
  }
  .dk-hint {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
