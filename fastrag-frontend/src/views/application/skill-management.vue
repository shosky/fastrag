<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const router = useRouter()

// --- 列表数据 ---
const skills = ref<any[]>([])

async function loadSkills() {
  skills.value = (await api.getSkills()) as any || []
}

onMounted(loadSkills)

const searchKeyword = ref('')
const selectedCategory = ref('')

// 分类筛选选项
const categoryOptions = [
  { label: '全部', value: '' },
  { label: '工具', value: 'tool' },
  { label: '模型', value: 'model' },
  { label: 'MCP', value: 'mcp' },
  { label: '技能', value: 'skill' },
]

const filteredSkills = computed(() => {
  let list = skills.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (s: any) =>
        s.name.toLowerCase().includes(kw) ||
        (s.slug || '').toLowerCase().includes(kw) ||
        s.description.toLowerCase().includes(kw),
    )
  }
  if (selectedCategory.value) {
    list = list.filter((s: any) => s.category === selectedCategory.value)
  }
  return list
})

// --- 路由跳转 ---
const goToDetail = (skill: any) => {
  router.push('/application/skill-management/' + skill.slug)
}

const goToEdit = (skill: any) => {
  router.push('/application/skill-management/' + skill.slug)
}

async function handleDelete(skill: any) {
  try {
    await ElMessageBox.confirm(`确定删除技能「${skill.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteSkill(skill.id)
    await loadSkills()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleToggleEnabled(skill: any) {
  // 切换到具体状态而不是取反
  const newEnabled = skill.enabled === 1 || skill.enabled === true ? 0 : 1
  await api.setSkillEnabled(skill.id, newEnabled === 1)
  skill.enabled = newEnabled
  ElMessage.success(newEnabled ? '已启用' : '已禁用')
}

// --- 上传技能（F3 两步化：upload 建草稿预览 → 用户确认 → confirm 安装） ---
// 第一步上传仅解析预览不安装（POST /skills/import/upload）；第二步用户确认后
// confirm（POST /skills/install-drafts/{draftId}/confirm）逐项返回 success/errorMsg。
// slug 与现有技能重复时显式提示「覆盖重装」（in-place，主键与绑定保留）再确认。
const showUploadDialog = ref(false)
const uploadFileList = ref<any[]>([])
const parseLoading = ref(false) // 第一步：解析预览中
const installLoading = ref(false) // 第二步：确认安装中

/** 草稿条目（SkillInstallDraft.DraftItem；预览与 confirm 结果共用形状） */
interface DraftItem {
  slug?: string
  name?: string
  description?: string
  success?: boolean
  errorMsg?: string
}

/** 安装草稿（upload 返回）：items 为待安装清单预览，expiresAt 起 1 小时过期 */
const draft = ref<{ draftId: string; items: DraftItem[]; source?: string } | null>(null)
/** confirm 返回的逐项安装结果（非空即已走完第二步，草稿已被服务端清理） */
const installResults = ref<DraftItem[] | null>(null)

const dialogStep = computed(() => (!draft.value ? 1 : installResults.value ? 3 : 2))

function openUpload() {
  showUploadDialog.value = true
  uploadFileList.value = []
  draft.value = null
  installResults.value = null
}

function handleUploadRemove() {
  uploadFileList.value = []
}

/** 第一步：上传建草稿（仅解析预览，不安装） */
async function handleUploadParse() {
  if (!uploadFileList.value.length) {
    ElMessage.warning('请先选择技能包文件')
    return
  }
  parseLoading.value = true
  try {
    const file = uploadFileList.value[0].raw
    const res: any = await api.uploadSkillImport(file)
    if (!res?.draftId || !Array.isArray(res.items) || !res.items.length) {
      ElMessage.error('解析结果为空，请检查技能包内容')
      return
    }
    draft.value = res
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '解析失败')
  } finally {
    parseLoading.value = false
  }
}

/** slug 与现有技能重复 → 覆盖重装（update-in-place，主键与 app 绑定保留） */
function isOverwrite(item: DraftItem): boolean {
  return !!item.slug && skills.value.some((s: any) => (s.slug || s.identifier) === item.slug)
}

const overwriteCount = computed(
  () => (draft.value?.items || []).filter((i) => i.success !== false && isOverwrite(i)).length
)

/** 可安装条目（预览解析失败项会在 confirm 阶段失败，确认前提示） */
const installableCount = computed(
  () => (draft.value?.items || []).filter((i) => i.success !== false).length
)

/** 第二步：确认安装（逐项 DraftItem 即安装结果明细） */
async function handleConfirmInstall() {
  if (!draft.value) return
  installLoading.value = true
  try {
    const results: any = await api.confirmSkillImport(draft.value.draftId)
    installResults.value = Array.isArray(results) ? results : []
    const ok = installResults.value.filter((i) => i.success).length
    const fail = installResults.value.length - ok
    if (fail === 0) ElMessage.success(`安装成功（${ok} 个技能）`)
    else ElMessage.warning(`安装完成：成功 ${ok} 个，失败 ${fail} 个`)
    await loadSkills()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '确认安装失败')
  } finally {
    installLoading.value = false
  }
}

/** 第二步退回第一步：丢弃当前草稿，重选文件 */
async function handleRedraft() {
  if (draft.value) await api.discardSkillDraft(draft.value.draftId).catch(() => {})
  draft.value = null
  uploadFileList.value = []
}

/** 弹窗关闭：未走完 confirm 的草稿丢弃（服务端清理；confirm 后草稿已被服务端清理无需再调） */
function handleDialogClosed() {
  if (draft.value && !installResults.value) {
    api.discardSkillDraft(draft.value.draftId).catch(() => {})
  }
  draft.value = null
  installResults.value = null
  uploadFileList.value = []
}

// --- 新建技能 ---
function goToCreate() {
  router.push('/application/skill-management/create')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <h2>技能管理</h2>
        <p>管理智能体的技能配置，扩展其能力边界</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索技能名称 / 标识 / 描述"
          clearable
          style="width: 320px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedCategory" placeholder="全部分类" clearable style="width: 160px">
          <el-option
            v-for="c in categoryOptions.filter((x) => x.value)"
            :key="c.value"
            :label="c.label"
            :value="c.value"
          />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="goToCreate">
          <el-icon><Plus /></el-icon>新建技能
        </el-button>
        <el-button type="primary" @click="openUpload">
          <el-icon><UploadFilled /></el-icon>上传技能
        </el-button>
      </div>
    </div>

    <!-- 技能列表 -->
    <div class="skills-grid" v-if="filteredSkills.length">
      <div v-for="skill in filteredSkills" :key="skill.id" class="skill-card">
        <div class="skill-card-body" @click="goToDetail(skill)">
          <div class="skill-title-row">
            <div class="skill-icon">
              <el-icon :size="16" color="#909399"><MagicStick /></el-icon>
            </div>
            <h4 :title="skill.name">{{ skill.name }}</h4>
            <span class="source-text">{{ skill.source }}</span>
          </div>
          <p class="skill-desc">{{ skill.description }}</p>
          <div class="skill-tags">
            <span class="tag-text">{{ skill.category }}</span>
            <span class="tag-text tag-mono">{{ skill.slug || skill.identifier }}</span>
          </div>
        </div>
        <div class="skill-card-footer">
          <div class="usage">
            <el-icon><DataLine /></el-icon>
            <span>调用 {{ skill.usageCount }} 次</span>
          </div>
          <div class="actions">
            <el-switch :model-value="skill.enabled === 1 || skill.enabled === true" size="small" style="pointer-events: none;" />
            <el-button link size="small" @click.stop="handleToggleEnabled(skill)">
              {{ skill.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" size="small" @click.stop="goToDetail(skill)">详情</el-button>
            <el-button link type="primary" size="small" @click.stop="goToEdit(skill)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleDelete(skill)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无技能，点击右上角新建或上传技能" />

    <!-- ========== 上传技能弹窗（F3 两步化：解析预览 → 确认安装 → 逐项结果） ========== -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传技能"
      width="640px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <!-- 第一步：选择技能包并解析预览（不安装） -->
      <template v-if="dialogStep === 1">
        <el-form label-width="100px" label-position="right">
          <el-form-item label="技能包" required>
            <el-upload
              v-model:file-list="uploadFileList"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".zip,.md,.markdown"
              :on-remove="handleUploadRemove"
              style="width: 100%"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                将技能包拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">支持 .zip / .md（SKILL.md）格式，解析预览确认后再安装</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </template>

      <!-- 第二步：安装清单预览（slug 冲突显式标「覆盖重装」） -->
      <template v-else-if="dialogStep === 2 && draft">
        <el-alert
          v-if="overwriteCount"
          type="warning"
          :closable="false"
          show-icon
          :title="`${overwriteCount} 个技能与现有 slug 重复，确认后将覆盖重装（原技能主键与应用绑定保留）`"
          style="margin-bottom: 12px"
        />
        <el-table :data="draft.items" size="small" max-height="320">
          <el-table-column label="名称" min-width="120">
            <template #default="{ row }">{{ row.name || row.slug || '—' }}</template>
          </el-table-column>
          <el-table-column label="标识（slug）" min-width="120">
            <template #default="{ row }">
              <span class="mono-slug">{{ row.slug || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.success === false" type="danger" size="small">解析失败</el-tag>
              <el-tag v-else-if="isOverwrite(row)" type="warning" size="small">覆盖重装</el-tag>
              <el-tag v-else type="success" size="small">新增</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="installableCount === 0" class="draft-hint danger">
          所有条目解析失败，无法安装；请检查技能包内的 SKILL.md 格式
        </div>
        <div v-else-if="installableCount < draft.items.length" class="draft-hint">
          {{ draft.items.length - installableCount }} 个条目解析失败，确认安装时将跳过
        </div>
      </template>

      <!-- 第三步：逐项安装结果明细 -->
      <template v-else-if="dialogStep === 3 && installResults">
        <el-table :data="installResults" size="small" max-height="320">
          <el-table-column label="名称" min-width="120">
            <template #default="{ row }">{{ row.name || row.slug || '—' }}</template>
          </el-table-column>
          <el-table-column label="标识（slug）" min-width="120">
            <template #default="{ row }">
              <span class="mono-slug">{{ row.slug || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="安装结果" min-width="200">
            <template #default="{ row }">
              <template v-if="row.success">
                <el-tag type="success" size="small">成功</el-tag>
              </template>
              <template v-else>
                <el-tag type="danger" size="small">失败</el-tag>
                <span class="fail-msg">{{ row.errorMsg || '未知错误' }}</span>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <template v-if="dialogStep === 1">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button
            type="primary"
            :loading="parseLoading"
            :disabled="!uploadFileList.length"
            @click="handleUploadParse"
          >解析预览</el-button>
        </template>
        <template v-else-if="dialogStep === 2">
          <el-button :disabled="installLoading" @click="handleRedraft">重选文件</el-button>
          <el-button
            type="primary"
            :loading="installLoading"
            :disabled="installableCount === 0"
            @click="handleConfirmInstall"
          >确认安装（{{ installableCount }} 项）</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="showUploadDialog = false">完成</el-button>
        </template>
      </template>
    </el-dialog>

  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0 0 $spacing-xs;
    font-size: 20px;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $text-secondary;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-base;
}

.skill-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $border-base;
    transform: translateY(-2px);
  }
}

.skill-card-body {
  padding: $spacing-base;
  flex: 1;
  cursor: pointer;
}

.skill-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .skill-icon {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  h4 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .source-text {
    flex-shrink: 0;
    font-size: 12px;
    color: $text-secondary;
  }
}

.skill-desc {
  margin: 0 0 $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}

.skill-tags {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;

  .tag-text {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
  }

  .tag-mono {
    font-family: 'Consolas', 'Monaco', monospace;
  }
}

.skill-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-base;
  border-top: 1px solid $border-lighter;

  .usage {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: $text-secondary;
  }

  .actions {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  .more-icon {
    cursor: pointer;
    color: $text-secondary;
    padding: 2px;

    &:hover {
      color: $color-primary;
    }
  }
}

// ── 上传弹窗（F3 两步化）──
.mono-slug {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: $text-secondary;
}

.draft-hint {
  margin-top: 8px;
  font-size: 12px;
  color: $text-secondary;
  &.danger {
    color: var(--el-color-danger);
  }
}

.fail-msg {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
