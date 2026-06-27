<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { CircleCheck, InfoFilled, Search, Monitor, OfficeBuilding, User, Setting, Share, Document } from '@element-plus/icons-vue'
import type { KnowledgeBase, KnowledgeBaseForm, FileTypeConfig, RetrievalSettingConfig } from '@/types/knowledge'
import RetrievalSettingPanel from './detail/components/RetrievalSettingPanel.vue'
import * as api from '@/api'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
// org functions now from api
// personnel functions now from api
// embedding models now from api
// categories now from api
import type { KBRole } from '@/types/auth'
import { KB_ROLE_LABELS } from '@/types/auth'

const router = useRouter()
const userStore = useUserStore()
const { hasRole, getMyKBRole } = useAuth()

// 嵌入模型选项从 API 加载
const embeddingModelOptions = ref<{label:string;value:string}[]>([])
// 分类选项从 API 加载
const categoryOptions = ref<{id:string;name:string}[]>([])

// --------------- Types ---------------
type ShareType = 'global' | 'department' | 'specified'

interface Department {
  id: string
  name: string
  isDefault: boolean
}

interface User {
  id: string
  name: string
  department: string
  isDefault: boolean
}

interface SpecifiedGrant {
  id: string
  name: string
  department: string
  role: KBRole
  /** 是否为当前用户自己（所有者，不可删除/降级） */
  isDefault: boolean
}

// --------------- Props & Emits ---------------
const props = defineProps<{
  mode: 'create' | 'edit'
  initialData?: KnowledgeBase
}>()

const emit = defineEmits<{
  (e: 'submit', data: KnowledgeBaseForm): void
  (e: 'cancel'): void
  (e: 'delete'): void
}>()

// --------------- Form ref ---------------
const formRef = ref<FormInstance>()

// --------------- Active tab ---------------
const activeTab = ref('basic')

// --------------- Submitting flag ---------------
const submitting = ref(false)

// --------------- Tag input ---------------
const tagInput = ref('')

// --------------- Default file type config ---------------
function defaultFileTypeConfig(): FileTypeConfig {
  return {
    documents: true,
    audio: false,
    video: false,
    images: false,
  }
}

const fileTypeConfig = reactive<FileTypeConfig>(defaultFileTypeConfig())

// --------------- 自定义属性 ---------------
const customAttrs = ref<{ name: string; type: string; description: string; required: boolean }[]>([])

// --------------- 解析策略模板（从接口加载） ---------------
const strategyTemplates = ref<{ key: string; name: string; description: string; extensions: string[]; parseMethod: string }[]>([])

async function loadStrategyTemplates() {
  try {
    const res: any = await api.fetchParseStrategyTemplates()
    strategyTemplates.value = res || []
  } catch {
    strategyTemplates.value = []
  }
}

onMounted(loadStrategyTemplates)

// --------------- Share settings ---------------
const shareType = ref<ShareType>('global')
// 弹窗显隐与选中的共享类型解耦：点空白处可关闭弹窗，但不改变已选共享模式
const departmentPopupVisible = ref(false)
const specifiedPopupVisible = ref(false)
const selectedDepartments = ref<Department[]>([])
// 指定人：使用结构化 grant，支持为每个人单独分配 KB 角色
const specifiedGrants = ref<SpecifiedGrant[]>([])

// Department popup — 从 API 加载
const departmentSearch = ref('')
const departments = ref<Department[]>([])

// User popup — 从 API 加载
const userSearch = ref('')
const users = ref<User[]>([])

// 指定人分配的角色选项（编辑者 / 查看者；所有者不在此处分配，避免自降权限）
const assignableKBRoles: KBRole[] = ['editor', 'viewer']

// Filtered lists
const filteredDepartments = computed(() => {
  if (!departmentSearch.value) return departments.value
  return departments.value.filter(d => d.name.includes(departmentSearch.value))
})

const filteredUsers = computed(() => {
  if (!userSearch.value) return users.value
  return users.value.filter(u => u.name.includes(userSearch.value))
})

// --------------- Async data loading ---------------
onMounted(async () => {
  try {
    const [catsRes, modelsRes, orgListRes, personnelRes] = await Promise.all([
      api.getKnowledgeBaseCategories(),
      api.getModels({ purpose: 'Embedding' }),
      api.getOrgFlat(),
      api.getPersonnel(),
    ])
    const cats: any[] = (catsRes as any)?.list || catsRes || []
    const models: any[] = (modelsRes as any)?.list || modelsRes || []
    const orgList: any[] = (orgListRes as any)?.list || orgListRes || []
    const personnel: any[] = (personnelRes as any)?.list || personnelRes || []
    categoryOptions.value = cats
    embeddingModelOptions.value = models.map((m: any) => ({ label: m.name || m.code, value: m.code || m.name }))
    departments.value = orgList.map((o: any) => ({ id: o.id, name: o.name, isDefault: o.id === '1' }))
    users.value = personnel
      .filter((p: any) => p.status === 'enabled')
      .map((p: any) => ({
        id: p.id,
        name: p.username,
        department: p.orgName,
        isDefault: p.id === '1',
      }))
  } catch (e) {
    console.error('Failed to load form data:', e)
  }
})

// --------------- Form data ---------------
function defaultForm(): KnowledgeBaseForm {
  return {
    name: '',
    category: '',
    description: '',
    tags: [],
    permission: 'private',
    embeddingModel: 'text-embedding-v4',
    parseMode: 'auto',
    splitMode: 'auto',
    fileTypeConfig,
    retrievalConfig: defaultRetrievalConfig(),
  }
}

function defaultRetrievalConfig(): RetrievalSettingConfig {
  return {
    mode: 'hybrid',
    topK: 3,
    enableScoreThreshold: false,
    scoreThreshold: 0,
    enableRerank: false,
    rerankStrategy: 'weighted',
    semanticWeight: 0.7,
    enableAutoCorrection: true,
    enableQueryRewrite: true,
    enableGraphExpansion: true,
    graphExpansionDepth: 1,
    graphMaxEntities: 5,
    enableSynonymExpansion: true,
    enableMultiRetrieval: false,
    vectorRecallCount: 20,
    fulltextRecallCount: 20,
    graphRecallCount: 10,
    qaRecallCount: 5,
    fusionStrategy: 'rrf',
    contextAssemblyStrategy: 'concat',
    contextWindowSize: 1,
    maxContextTokens: 4096,
    contextOrder: 'relevance',
    rerankModel: 'bge-reranker-v2-m3',
    enableLLMRerank: false,
    enableMMR: false,
    mmrLambda: 0.5,
    bm25RecallCount: 50,
    vectorWeight: 0.7,
    bm25Weight: 0.3,
    bm25SparseDropRate: 0,
  }
}

const form = reactive<KnowledgeBaseForm>(defaultForm())

// --------------- Track if user explicitly configured embedding model ---------------
const embeddingModelTouched = ref(false)

// --------------- Populate from initialData (edit mode) ---------------
watch(
  () => props.initialData,
  async (data) => {
    // Reset all fields to defaults first to avoid stale values
    const defaults = defaultForm()
    form.name = defaults.name
    form.category = defaults.category
    form.description = defaults.description
    form.tags = defaults.tags
    form.permission = defaults.permission
    form.embeddingModel = defaults.embeddingModel
    form.parseMode = defaults.parseMode
    form.splitMode = defaults.splitMode
    Object.assign(fileTypeConfig, defaultFileTypeConfig())
    Object.assign(form.retrievalConfig, defaultRetrievalConfig())
    embeddingModelTouched.value = false
    shareType.value = 'global'
    selectedDepartments.value = []
    specifiedGrants.value = []

    if (data) {
      form.name = data.name
      form.category = data.category
      form.description = data.description
      form.tags = [...data.tags]
      form.embeddingModel = data.embeddingModel

      const extended = data as Partial<KnowledgeBaseForm>
      if (extended.permission) form.permission = extended.permission
      if (extended.parseMode) form.parseMode = extended.parseMode
      if (extended.splitMode) form.splitMode = extended.splitMode
      if (extended.fileTypeConfig) Object.assign(fileTypeConfig, extended.fileTypeConfig)
      if (extended.retrievalConfig) Object.assign(form.retrievalConfig, extended.retrievalConfig)

      // 编辑模式：从现有 ACL 还原共享设置，而不是根据 permission 字段猜
      await hydrateShareFromAcl(data.id)
    }
  },
  { immediate: true }
)

/**
 * 从 ACL 反推共享设置：
 * - 含 userId='*' 条目 → global
 * - 否则含 userId != 当前用户 的条目 → department / specified
 * 否则只读用户自身：保持 global（实际无共享）
 */
async function hydrateShareFromAcl(kbId: string): Promise<void> {
  const aclRes: any = await api.getKbAcl(kbId)
  const acl: any[] = aclRes?.list || aclRes || []
  const meId = userStore.userInfo?.id || '1'

  if (acl.some((e: any) => e.userId === '*')) {
    shareType.value = 'global'
    return
  }
  const others = acl.filter((e: any) => e.userId !== meId && e.userId !== '*')
  if (others.length === 0) {
    shareType.value = 'global'
    return
  }
  // 用人员 id 反查名称
  const personnelRes: any = await api.getPersonnel()
  const personnel: any[] = personnelRes?.list || personnelRes || []
  specifiedGrants.value = others.map((e: any) => {
    const p = personnel.find((x: any) => x.id === e.userId)
    return {
      id: e.userId,
      name: p?.username || e.userId,
      department: p?.orgName || '',
      role: e.kbRole,
      isDefault: e.userId === meId,
    }
  })
  shareType.value = 'specified'
}

// --------------- Validation rules ---------------
const rules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

// --------------- Tag helpers ---------------
function handleAddTag() {
  const val = tagInput.value.trim()
  if (val && !form.tags.includes(val)) {
    form.tags.push(val)
  }
  tagInput.value = ''
}

function handleRemoveTag(tag: string) {
  form.tags = form.tags.filter(t => t !== tag)
}

// --------------- Share helpers ---------------
/**
 * 点击共享卡片选中该模式。
 * 部门/指定人的弹窗显隐完全交给 el-popover 的 trigger="click" 管理
 * （含点外部关闭），这里只负责切换 shareType 与初始化默认选择项。
 */
function selectShareType(type: ShareType) {
  // 切换共享类型时清理上一个类型的残留状态，避免 badge/列表显示错误
  if (type !== 'department') selectedDepartments.value = []
  if (type !== 'specified') specifiedGrants.value = []
  shareType.value = type

  // 初始化默认选择
  if (type === 'department' && selectedDepartments.value.length === 0 && departments.value[0]) {
    selectedDepartments.value = [departments.value[0]]
  }
  if (type === 'specified' && specifiedGrants.value.length === 0 && users.value[0]) {
    specifiedGrants.value = [{ ...users.value[0], role: 'viewer', isDefault: users.value[0].isDefault }]
  }
}

/**
 * 同时刻最多打开一个弹窗：某个弹窗打开时关闭另一个。
 * 弹窗的显隐由 el-popover trigger=click 自行管理（含点外部关闭）。
 */
watch(departmentPopupVisible, (visible) => {
  if (visible) specifiedPopupVisible.value = false
})
watch(specifiedPopupVisible, (visible) => {
  if (visible) departmentPopupVisible.value = false
})

function toggleDepartment(dept: Department) {
  if (dept.isDefault) return // Default department cannot be removed
  const index = selectedDepartments.value.findIndex(d => d.id === dept.id)
  if (index >= 0) {
    selectedDepartments.value.splice(index, 1)
  } else {
    selectedDepartments.value.push(dept)
  }
}

function toggleUser(user: User) {
  if (user.isDefault) return // Default user (self) cannot be removed
  const index = specifiedGrants.value.findIndex(g => g.id === user.id)
  if (index >= 0) {
    specifiedGrants.value.splice(index, 1)
  } else {
    specifiedGrants.value.push({ ...user, role: 'viewer', isDefault: user.isDefault })
  }
}

function isUserGranted(userId: string): boolean {
  return specifiedGrants.value.some(g => g.id === userId)
}

function setGrantRole(grant: SpecifiedGrant, role: KBRole) {
  grant.role = role
}

// --------------- Progress helpers (create mode) ---------------
const progressSteps = computed(() => [
  { label: '填写知识库名称', done: !!form.name },
  { label: '选择分类', done: !!form.category },
  { label: '填写简介', done: !!form.description },
  { label: '配置高级参数', done: embeddingModelTouched.value },
])

// --------------- Edit mode: metadata display ---------------
const metaItems = computed(() => {
  if (props.mode !== 'edit' || !props.initialData) return []
  const d = props.initialData
  return [
    { label: '创建人', value: d.creator },
    { label: '创建时间', value: d.createdAt },
    { label: '向量维度', value: String(d.dimension) },
    { label: '容量', value: `${d.usedSize} / ${d.totalSize}` },
    { label: '类型', value: d.type },
  ]
})

// --------------- Permission: can delete? ---------------
const canDelete = computed(() => {
  if (hasRole('super_admin')) return true
  if (props.mode === 'edit' && props.initialData) {
    return getMyKBRole(props.initialData.id) === 'owner'
  }
  // 创建模式：创建者天然是 owner
  return props.mode === 'create'
})

// --------------- Submit / Cancel / Delete ---------------
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true

  // Update permission based on share type
  if (shareType.value === 'global') {
    form.permission = 'public'
  } else if (shareType.value === 'department') {
    form.permission = 'team'
  } else {
    form.permission = 'private'
  }

  // 写入 ACL 条目（创建/编辑知识库时同步更新权限）
  const meId = userStore.userInfo?.id || '1'
  // 编辑模式用真实知识库 id；创建模式由父组件在拿到后端 kbId 后修正
  const kbId = props.mode === 'edit' && props.initialData?.id ? props.initialData.id : 'new'

  try {
    // 构造 ACL 条目列表
    const aclEntries: any[] = [{ kbId, userId: meId, kbRole: 'owner', grantedBy: meId }]

    if (shareType.value === 'global') {
      // 全局共享：所有人可查看
      aclEntries.push({ kbId, userId: '*', kbRole: 'viewer', grantedBy: meId })
    } else if (shareType.value === 'department') {
      // 部门共享：异步获取部门成员后赋 viewer
      const memberIds = new Set<string>()
      for (const dept of selectedDepartments.value) {
        try {
          const members: any = await api.getDepartmentMembers(dept.id)
          const list = members?.list || members || []
          list.forEach((m: any) => {
            const mid = m.id || m.userId
            if (mid && mid !== meId) memberIds.add(mid)
          })
        } catch {
          // 部门成员获取失败，跳过
        }
      }
      memberIds.forEach((uid) => {
        aclEntries.push({ kbId, userId: uid, kbRole: 'viewer', grantedBy: meId })
      })
    } else {
      // 指定人：使用每个人选择的角色
      specifiedGrants.value.forEach((g) => {
        if (g.id === meId) return
        aclEntries.push({ kbId, userId: g.id, kbRole: g.role, grantedBy: meId })
      })
    }

    // 调用后端 API 写入 ACL
    if (kbId !== 'new') {
      try {
        await api.setKbAcl(kbId, aclEntries)
      } catch {
        // ACL 写入失败不阻断主流程
      }
    }

    emit('submit', {
      ...form,
      fileTypeConfig: { ...fileTypeConfig },
      retrievalConfig: { ...form.retrievalConfig },
    })
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  emit('cancel')
}

function handleDelete() {
  emit('delete')
}

/** 跳转到解析策略管理页 */
function goToParseStrategy() {
  if (props.mode === 'edit' && props.initialData?.id) {
    router.push(`/knowledge/${props.initialData.id}/parse-strategy`)
  } else {
    ElMessage.warning('请先保存知识库，保存后可配置解析策略')
  }
}
</script>

<template>
  <div class="form-wrapper">
    <!-- ========== Left: form panel ========== -->
    <div class="form-panel">
      <el-tabs v-model="activeTab" class="form-tabs">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane name="basic">
          <template #label>
            <span class="tab-label">
              <el-icon><Document /></el-icon>
              基本信息
            </span>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="100px"
            class="tab-form"
          >
            <!-- Name -->
            <el-form-item label="知识库名称" prop="name">
              <el-input
                v-model="form.name"
                placeholder="请输入知识库名称"
              />
            </el-form-item>

            <!-- Category -->
            <el-form-item label="分类" prop="category">
              <el-select
                v-model="form.category"
                placeholder="请选择分类"
                style="width: 100%"
              >
                <el-option
                  v-for="cat in categoryOptions"
                  :key="cat.id"
                  :label="cat.name"
                  :value="cat.id"
                />
              </el-select>
            </el-form-item>

            <!-- Description -->
            <el-form-item label="简介">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="请输入知识库简介"
              />
            </el-form-item>

            <!-- Tags -->
            <el-form-item label="标签">
              <div class="tag-input-area">
                <el-tag
                  v-for="tag in form.tags"
                  :key="tag"
                  closable
                  @close="handleRemoveTag(tag)"
                >
                  {{ tag }}
                </el-tag>
                <el-input
                  v-model="tagInput"
                  size="small"
                  style="width: 120px"
                  placeholder="输入标签"
                  @keyup.enter="handleAddTag"
                />
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 共享设置 -->
        <el-tab-pane name="share">
          <template #label>
            <span class="tab-label">
              <el-icon><Share /></el-icon>
              共享设置
            </span>
          </template>

          <div class="tab-content">
            <div class="share-settings">
              <!-- Global share -->
              <div class="share-settings__item">
                <div
                  class="share-card"
                  :class="{ 'share-card--active': shareType === 'global' }"
                  @click="selectShareType('global')"
                >
                  <div class="share-card__icon">
                    <el-icon :size="20"><Monitor /></el-icon>
                  </div>
                  <div class="share-card__content">
                    <span class="share-card__title">全局共享</span>
                    <span class="share-card__desc">所有用户都可以访问</span>
                  </div>
                </div>
              </div>

              <!-- Department share -->
              <div class="share-settings__item">
                <el-popover
                  v-model:visible="departmentPopupVisible"
                  placement="bottom"
                  trigger="click"
                  :width="280"
                  popper-class="share-popover"
                  :show-arrow="false"
                  :offset="8"
                >
                  <template #reference>
                    <div
                      class="share-card"
                      :class="{ 'share-card--active': shareType === 'department' }"
                      @click="selectShareType('department')"
                    >
                      <div class="share-card__icon">
                        <el-icon :size="20"><OfficeBuilding /></el-icon>
                      </div>
                      <div class="share-card__content">
                        <span class="share-card__title">
                          部门共享
                          <el-badge
                            v-if="selectedDepartments.length > 0"
                            :value="selectedDepartments.length"
                            class="share-card__badge"
                          />
                        </span>
                        <span class="share-card__desc">选中的部门成员可以访问</span>
                      </div>
                    </div>
                  </template>

                  <!-- Department popup content -->
                  <div class="share-popup" @click.stop>
                    <div class="share-popup__header">
                      <span class="share-popup__title">可访问部门</span>
                      <span class="share-popup__count">{{ selectedDepartments.length }} 个部门可访问</span>
                    </div>
                    <div class="share-popup__search">
                      <el-input
                        v-model="departmentSearch"
                        placeholder="搜索部门"
                        :prefix-icon="Search"
                        size="small"
                      />
                    </div>
                    <div class="share-popup__list">
                      <div
                        v-for="dept in filteredDepartments"
                        :key="dept.id"
                        class="share-popup__item"
                        :class="{ 'share-popup__item--disabled': dept.isDefault }"
                        @click="toggleDepartment(dept)"
                      >
                        <el-checkbox
                          :model-value="selectedDepartments.some(d => d.id === dept.id)"
                          :disabled="dept.isDefault"
                        />
                        <span class="share-popup__item-name">{{ dept.name }}</span>
                        <el-tag v-if="dept.isDefault" size="small" type="info">必选</el-tag>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>

              <!-- Specified users -->
              <div class="share-settings__item">
                <el-popover
                  v-model:visible="specifiedPopupVisible"
                  placement="bottom"
                  trigger="click"
                  :width="320"
                  popper-class="share-popover"
                  :show-arrow="false"
                  :offset="8"
                >
                  <template #reference>
                    <div
                      class="share-card"
                      :class="{ 'share-card--active': shareType === 'specified' }"
                      @click="selectShareType('specified')"
                    >
                      <div class="share-card__icon">
                        <el-icon :size="20"><User /></el-icon>
                      </div>
                      <div class="share-card__content">
                        <span class="share-card__title">
                          指定人
                          <el-badge
                            v-if="specifiedGrants.length > 0"
                            :value="specifiedGrants.length"
                            class="share-card__badge"
                          />
                        </span>
                        <span class="share-card__desc">选中的用户可以访问</span>
                      </div>
                    </div>
                  </template>

                  <!-- User popup content -->
                  <div class="share-popup" @click.stop>
                    <div class="share-popup__header">
                      <span class="share-popup__title">可访问用户</span>
                      <span class="share-popup__count">{{ specifiedGrants.length }} 人可访问</span>
                    </div>
                    <div class="share-popup__search">
                      <el-input
                        v-model="userSearch"
                        placeholder="搜索用户"
                        :prefix-icon="Search"
                        size="small"
                      />
                    </div>
                    <div class="share-popup__list">
                      <div
                        v-for="user in filteredUsers"
                        :key="user.id"
                        class="share-popup__item"
                        :class="{ 'share-popup__item--disabled': user.isDefault }"
                        @click="toggleUser(user)"
                      >
                        <el-checkbox
                          :model-value="isUserGranted(user.id)"
                          :disabled="user.isDefault"
                        />
                        <span class="share-popup__item-name">
                          {{ user.name }}
                          <span class="share-popup__item-dept">（{{ user.department }}）</span>
                        </span>
                        <el-tag v-if="user.isDefault" size="small" type="info">自己</el-tag>
                      </div>
                    </div>

                    <!-- 已选用户的角色分配 -->
                    <div v-if="specifiedGrants.length > 0" class="share-popup__grants">
                      <div class="share-popup__grants-title">权限分配</div>
                      <div
                        v-for="grant in specifiedGrants"
                        :key="grant.id"
                        class="share-popup__grant-row"
                      >
                        <span class="share-popup__grant-name">
                          {{ grant.name }}
                          <span class="share-popup__grant-dept">{{ grant.department }}</span>
                        </span>
                        <el-select
                          v-if="!grant.isDefault"
                          :model-value="grant.role"
                          size="small"
                          style="width: 100px"
                          @change="(v: KBRole) => setGrantRole(grant, v)"
                        >
                          <el-option
                            v-for="r in assignableKBRoles"
                            :key="r"
                            :label="KB_ROLE_LABELS[r]"
                            :value="r"
                          />
                        </el-select>
                        <el-tag v-else size="small" type="success">所有者</el-tag>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 3: 高级配置 -->
        <el-tab-pane name="advanced">
          <template #label>
            <span class="tab-label">
              <el-icon><Setting /></el-icon>
              高级配置
            </span>
          </template>

          <el-form
            :model="form"
            label-width="100px"
            class="tab-form"
          >
            <!-- Embedding Model —— 选项来自模型管理 mock，不再硬编码 -->
            <el-form-item label="嵌入模型">
              <el-tooltip
                v-if="mode === 'edit'"
                content="嵌入模型在创建后不可更改"
                placement="top"
              >
                <el-select
                  v-model="form.embeddingModel"
                  style="width: 100%"
                  disabled
                  @change="embeddingModelTouched = true"
                >
                  <el-option
                    v-for="opt in embeddingModelOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-tooltip>
              <el-select
                v-else
                v-model="form.embeddingModel"
                style="width: 100%"
                @change="embeddingModelTouched = true"
              >
                <el-option
                  v-for="opt in embeddingModelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>

            <!-- 解析策略 -->
            <el-form-item label="解析策略">
              <template v-if="props.mode === 'edit'">
                <el-button type="primary" link @click="goToParseStrategy">
                  <el-icon><Setting /></el-icon>
                  管理解析策略
                </el-button>
                <span class="parse-strategy-desc">为不同文件类型配置解析方法、切片长度、分隔符等参数</span>
              </template>
              <template v-else>
                <el-select v-model="form.parseMode" style="width: 100%">
                  <el-option
                    v-for="tpl in strategyTemplates"
                    :key="tpl.key"
                    :label="tpl.name"
                    :value="tpl.key"
                  />
                </el-select>
                <span class="parse-strategy-desc">创建后可在解析策略管理中进一步自定义</span>
              </template>
            </el-form-item>

            <!-- File Type Configuration（创建/编辑均可配置，移除仅 edit 可见的限制） -->
            <el-form-item label="文件类型">
              <div class="file-type-list">
                <el-checkbox v-model="fileTypeConfig.documents">
                  文档 (PDF、Word、Excel、PPT)
                </el-checkbox>
                <el-checkbox v-model="fileTypeConfig.audio">
                  音频 (MP3、WAV)
                </el-checkbox>
                <el-checkbox v-model="fileTypeConfig.video">
                  视频 (MP4、AVI)
                </el-checkbox>
                <el-checkbox v-model="fileTypeConfig.images">
                  图片 (JPG、PNG，需 OCR)
                </el-checkbox>
              </div>
            </el-form-item>

            <!-- 自定义属性 -->
            <el-divider content-position="left">自定义属性</el-divider>
            <div class="custom-attrs">
              <div v-for="(attr, idx) in customAttrs" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center">
                <el-input v-model="attr.name" placeholder="属性名" style="width: 150px" />
                <el-select v-model="attr.type" style="width: 120px">
                  <el-option label="文本" value="text" /><el-option label="数字" value="number" />
                  <el-option label="下拉" value="select" /><el-option label="日期" value="date" />
                </el-select>
                <el-input v-model="attr.description" placeholder="描述" style="flex: 1" />
                <el-checkbox v-model="attr.required">必填</el-checkbox>
                <el-button link type="danger" @click="customAttrs.splice(idx, 1)">删除</el-button>
              </div>
              <el-button size="small" @click="customAttrs.push({ name: '', type: 'text', description: '', required: false })">添加属性</el-button>
              <p class="parse-strategy-desc">定义知识条目的自定义元数据字段，可用于分类筛选和检索增强。</p>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- Tab 4: 检索设置 -->
        <el-tab-pane name="retrieval">
          <template #label>
            <span class="tab-label">
              <el-icon><Search /></el-icon>
              检索设置
            </span>
          </template>

          <div class="tab-content">
            <RetrievalSettingPanel
              :config="form.retrievalConfig"
              @update:config="(config: RetrievalSettingConfig) => form.retrievalConfig = config"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- ========== Bottom sticky action bar ========== -->
      <div class="form-actions">
        <el-button @click="handleCancel">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ mode === 'create' ? '创建知识库' : '保存修改' }}
        </el-button>
      </div>
    </div>

    <!-- ========== Right panel ========== -->
    <div class="side-panel">
      <!-- Create mode: progress tracker -->
      <template v-if="mode === 'create'">
        <h4>创建进度</h4>
        <div class="progress-items">
          <div
            v-for="(step, idx) in progressSteps"
            :key="idx"
            class="progress-item"
            :class="{ done: step.done }"
          >
            <el-icon><CircleCheck /></el-icon>
            <span>{{ step.label }}</span>
          </div>
        </div>
      </template>

      <!-- Edit mode: metadata + danger zone -->
      <template v-else>
        <h4>基本信息</h4>
        <dl class="meta-list">
          <template v-for="item in metaItems" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </template>
        </dl>

        <el-divider />

        <!-- Danger zone -->
        <h4 class="danger-title">危险操作</h4>
        <div class="danger-zone">
          <p class="danger-hint">
            <el-icon><InfoFilled /></el-icon>
            删除知识库后，所有数据将无法恢复。
          </p>
          <el-button
            v-if="canDelete"
            v-permission="'kb:delete'"
            type="danger"
            style="width: 100%"
            @click="handleDelete"
          >
            删除知识库
          </el-button>
          <p v-else class="danger-hint danger-hint--locked">
            <el-icon><InfoFilled /></el-icon>
            仅所有者或超级管理员可删除。
          </p>
        </div>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.form-wrapper {
  display: flex;
  gap: $spacing-lg;
}

.form-panel {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
}

.form-tabs {
  flex: 1;

  :deep(.el-tabs__header) {
    margin-bottom: $spacing-lg;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
  }
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 14px;
}

.tab-form {
  padding: $spacing-base 0;
}

.tab-content {
  padding: $spacing-base 0;
}

.tag-input-area {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

.file-type-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

// 解析策略描述
.parse-strategy-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-left: $spacing-sm;
}

// --- Bottom sticky action bar ---
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  padding-top: $spacing-lg;
  margin-top: $spacing-lg;
  border-top: 1px solid $border-lighter;
}

// --- Share settings ---
.share-settings {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-base;
  width: 100%;
}

.share-settings__item {
  position: relative;
}

.share-card {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $color-primary;
  }

  &--active {
    border-color: $color-primary;
    background: $bg-active;
  }
}

.share-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: $bg-hover;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-secondary;
  flex-shrink: 0;
}

.share-card--active .share-card__icon {
  background: $color-primary;
  color: $bg-white;
}

.share-card__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.share-card__title {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  display: flex;
  align-items: center;
  gap: 4px;
}

.share-card__badge {
  :deep(.el-badge__content) {
    font-size: 10px;
  }
}

.share-card__desc {
  font-size: 12px;
  color: $text-secondary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// --- Share popup (now rendered inside el-popover, teleported to body) ---
.share-popup {
  width: 100%;

  &__header {
    margin-bottom: $spacing-xs;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  &__count {
    font-size: 11px;
    color: $text-secondary;
    margin-left: $spacing-xs;
  }

  &__search {
    margin-bottom: $spacing-xs;
  }

  &__list {
    max-height: 150px;
    overflow-y: auto;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: 4px $spacing-xs;
    border-radius: $radius-sm;
    cursor: pointer;
    font-size: 12px;

    &:hover {
      background: $bg-hover;
    }

    &--disabled {
      cursor: not-allowed;
    }
  }

  &__item-name {
    flex: 1;
    color: $text-primary;
  }

  &__item-dept {
    font-size: 11px;
    color: $text-secondary;
  }

  // 权限分配区
  &__grants {
    margin-top: $spacing-sm;
    padding-top: $spacing-sm;
    border-top: 1px dashed $border-light;
  }

  &__grants-title {
    font-size: 12px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-xs;
  }

  &__grant-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $spacing-xs;
    padding: 4px $spacing-xs;
    font-size: 12px;
  }

  &__grant-name {
    color: $text-primary;
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__grant-dept {
    font-size: 11px;
    color: $text-secondary;
    margin-left: 4px;
  }
}

.side-panel {
  width: 240px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  flex-shrink: 0;
  align-self: flex-start;

  h4 {
    margin: 0 0 $spacing-lg;
  }
}

.progress-items {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 13px;
  color: $text-secondary;

  &.done {
    color: $color-success;
  }
}

// --- Metadata list (edit mode) ---
.meta-list {
  margin: 0 0 $spacing-base;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: $spacing-xs $spacing-base;
  font-size: 12px;

  dt {
    color: $text-secondary;
  }

  dd {
    margin: 0;
    color: $text-primary;
    text-align: right;
  }
}

.danger-title {
  color: $color-danger;
}

.danger-zone {
  .danger-hint {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: $spacing-sm;

    &--locked {
      color: $text-placeholder;
    }
  }
}
</style>
