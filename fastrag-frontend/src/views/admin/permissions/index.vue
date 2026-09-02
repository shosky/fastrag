<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ROLE_LABELS, PERMISSIONS } from '@/types/auth'
import type { RoleMeta } from '@/types/auth'
import { usePagination } from '@/composables/usePagination'
import * as api from '@/api'

interface PermissionItem {
  id: number
  permKey: string
  name: string
  type: string  // menu / action / group
  group: string
  category: string  // menu / page_action / api
  parentKey: string | null
  description: string | null
  roleIds: string[]
  children?: PermissionItem[]
}

// --- State ---
const permissionList = ref<PermissionItem[]>([])
const roleMap = ref<Record<string, string>>({})
const activeTab = ref('list')
const searchKeyword = ref('')
const loading = ref(false)
const expandedRows = ref<Record<number, boolean>>({})

function isExpanded(rowId: number) {
  return !!expandedRows.value[rowId]
}

function toggleRow(row: PermissionItem) {
  expandedRows.value = {
    ...expandedRows.value,
    [row.id]: !expandedRows.value[row.id],
  }
}

// --- Pagination ---
const { currentPage, pageSize, handleCurrentChange, handleSizeChange } = usePagination(10)

// --- CRUD Dialog ---
const showDialog = ref(false)
const dialogTitle = ref('新增权限')
const editingId = ref<number | null>(null)
const formData = ref({
  permKey: '',
  name: '',
  type: 'action' as string,
  group: 'admin' as string,
  category: 'page_action' as string,
  parentKey: '',
  description: '',
  // API 权限专用字段
  httpMethod: 'GET' as string,
  apiPath: '',
  // 页面操作专用字段
  pagePath: '' as string,
})

const httpMethodOptions = ['GET', 'POST', 'PUT', 'DELETE']

const groupOptions = [
  { label: '菜单', value: 'menu' },
  { label: '知识库', value: 'kb' },
  { label: '管理后台', value: 'admin' },
  { label: '应用中心', value: 'app' },
  { label: '业务流', value: 'workflow' },
  { label: '审核管理', value: 'review' },
]

/** 页面操作的可选页面列表 */
const pageOptions = [
  { label: '首页', value: '/home' },
  { label: '知识库列表', value: '/knowledge' },
  { label: '知识库分类', value: '/knowledge/categories' },
  { label: '知识库详情', value: '/knowledge/:id' },
  { label: '应用中心', value: '/application' },
  { label: '应用编辑', value: '/application/:id/editor' },
  { label: '应用运行', value: '/application/runtime' },
  { label: '我的工具', value: '/application/my-tools' },
  { label: 'MCP管理', value: '/application/mcp-management' },
  { label: '技能管理', value: '/application/skill-management' },
  { label: '数据库管理', value: '/application/database-management' },
  { label: '业务流管理', value: '/application/workflow-manage' },
  { label: '管理中心概览', value: '/admin/index' },
  { label: '通用设置', value: '/admin/system/general-settings' },
  { label: '知识库配置', value: '/admin/system/kb-config' },
  { label: '敏感词设置', value: '/admin/system/sensitive-words' },
  { label: '字典管理', value: '/admin/system/dictionary' },
  { label: '术语管理', value: '/admin/system/terminology' },
  { label: '角色管理', value: '/admin/account/roles' },
  { label: '组织管理', value: '/admin/account/organization' },
  { label: '人员管理', value: '/admin/account/personnel' },
  { label: '权限管理', value: '/admin/permissions' },
  { label: '系统日志', value: '/admin/audit/system-log' },
  { label: '模型管理', value: '/admin/platform/model-management' },
  { label: '开放密钥', value: '/admin/platform/api-keys' },
  { label: '运营中心-知识资产分析', value: '/operation/kb-analytics' },
  { label: '运营中心-用户反馈', value: '/operation/feedback' },
  { label: '运营中心-问答明细', value: '/operation/qa-detail' },
  { label: '知识审核-审核流程', value: '/knowledge-review/flows' },
  { label: '知识审核-发布管理', value: '/knowledge-review/management' },
  { label: '数据挖掘', value: '/robot-operation/data-mining' },
]

const typeOptions = [
  { label: '菜单权限', value: 'menu' },
  { label: '操作权限', value: 'action' },
]

const categoryOptions = [
  { label: '菜单权限', value: 'menu' },
  { label: '页面操作', value: 'page_action' },
  { label: 'API接口', value: 'api' },
]

// --- 数据加载 ---
async function loadData() {
  loading.value = true
  try {
    const [permRes, roleRes] = await Promise.all([
      api.getPermissions(),
      api.getRoles(),
    ])
    permissionList.value = flattenPermissions((permRes as any) || [])
    // 构建角色ID->名称映射
    const roles = ((roleRes as any)?.list || (roleRes as any) || []) as RoleMeta[]
    const map: Record<string, string> = {}
    for (const r of roles) {
      map[r.id] = r.name
    }
    roleMap.value = map
  } finally {
    loading.value = false
  }
}

/** 将树形 API 数据展平为列表（排除 group 节点） */
function flattenPermissions(data: PermissionItem[]): PermissionItem[] {
  const result: PermissionItem[] = []
  for (const item of data) {
    if (item.type === 'group' && item.children) {
      result.push(...flattenPermissions(item.children))
    } else if (item.type !== 'group') {
      result.push(item)
    }
  }
  return result
}

onMounted(() => {
  loadData()
})

// --- 搜索过滤 ---
const filterType = computed<'all' | 'menu' | 'action' | 'api'>(() => {
  if (activeTab.value === 'menu') return 'menu'
  if (activeTab.value === 'action') return 'action'
  if (activeTab.value === 'api') return 'api'
  return 'all'
})

const filteredPermissions = computed(() => {
  let list = permissionList.value
  if (filterType.value !== 'all') {
    if (filterType.value === 'api') {
      list = list.filter((p) => p.category === 'api')
    } else {
      list = list.filter((p) => p.type === filterType.value && p.category !== 'api')
    }
  }
  if (searchKeyword.value) {
    list = list.filter((p) =>
      p.permKey.includes(searchKeyword.value) || p.name.includes(searchKeyword.value),
    )
  }
  return list
})

// --- 分页 ---
const paginatedPermissions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredPermissions.value.slice(start, start + pageSize.value)
})

// --- 按分类统计 ---
const categoryStats = computed(() => {
  const stats: Record<string, number> = {}
  for (const p of permissionList.value) {
    const c = p.category || 'page_action'
    stats[c] = (stats[c] || 0) + 1
  }
  return stats
})

const categoryLabels: Record<string, string> = {
  menu: '菜单权限',
  page_action: '页面操作',
  api: 'API接口',
}

// --- 权限 CRUD ---
function handleAdd() {
  dialogTitle.value = '新增权限'
  editingId.value = null
  const defaultCategory = activeTab.value === 'api' ? 'api' : 'page_action'
  formData.value = { permKey: '', name: '', type: 'action', group: 'admin', category: defaultCategory, parentKey: '', description: '', httpMethod: 'GET', apiPath: '', pagePath: '' }
  showDialog.value = true
}

function handleEdit(row: PermissionItem) {
  dialogTitle.value = '编辑权限'
  editingId.value = row.id
  // 从 permKey 反推 API 路径
  let httpMethod = 'GET'
  let apiPath = ''
  if (row.category === 'api' && row.permKey.startsWith('api:')) {
    const parts = row.permKey.split(':')
    const idx = ['get', 'post', 'put', 'delete'].includes(parts[1]) ? 1 : 0
    if (idx === 1) httpMethod = parts[1].toUpperCase()
    apiPath = '/api/' + parts.slice(idx + 1).join('/')
  }
  formData.value = {
    permKey: row.permKey,
    name: row.name,
    type: row.type,
    group: row.group || 'admin',
    category: row.category || 'page_action',
    parentKey: row.parentKey || '',
    description: row.description || '',
    httpMethod,
    apiPath,
    pagePath: '',
  }
  showDialog.value = true
}

async function handleDelete(row: PermissionItem) {
  try {
    await ElMessageBox.confirm(`确认删除权限「${row.name}」？关联的角色配置也将被清除。`, '删除确认', { type: 'warning' })
    await api.deletePermission(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {}
}

async function handleSave() {
  if (!formData.value.permKey.trim() || !formData.value.name.trim()) {
    ElMessage.warning('请输入权限标识和名称')
    return
  }
  const payload = {
    permKey: formData.value.permKey,
    name: formData.value.name,
    type: formData.value.type,
    group: formData.value.group,
    category: formData.value.category,
    parentKey: formData.value.parentKey || null,
    description: formData.value.description,
  }
  if (editingId.value != null) {
    await api.updatePermission(editingId.value, payload)
    ElMessage.success('更新成功')
  } else {
    await api.createPermission(payload)
    ElMessage.success('创建成功')
  }
  showDialog.value = false
  await loadData()
}

// API 权限自动生成 permKey
watch([() => formData.value.httpMethod, () => formData.value.apiPath], ([method, path]) => {
  if (formData.value.category === 'api' && path) {
    const cleanPath = path.replace(/\{([^}]+)\}/g, '{$1}').replace(/^\/?/, '/')
    formData.value.permKey = `api:${method.toLowerCase()}${cleanPath.replace(/\/api\/?/i, ':').replace(/\//g, ':').replace(/\{/g, '').replace(/\}/g, '')}`
    if (!formData.value.name) {
      formData.value.name = `${method} ${cleanPath}`
    }
  }
})
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部权限" name="list" />
      <el-tab-pane label="菜单权限" name="menu" />
      <el-tab-pane label="操作权限" name="action" />
      <el-tab-pane label="API接口" name="api" />
    </el-tabs>

    <!-- 权限列表 -->
    <div class="perm-list-toolbar">
      <div class="perm-list-toolbar__left">
        <el-button type="primary" size="small" v-permission="PERMISSIONS.ADMIN_ROLE" @click="handleAdd">新增权限</el-button>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索权限名称或标识"
          clearable
          style="width: 240px"
          size="small"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="perm-list-toolbar__right">
        <el-tag v-for="(count, cat) in categoryStats" :key="cat" size="small" type="info" style="margin: 2px">
          {{ categoryLabels[cat as string] || cat }}: {{ count }}
        </el-tag>
      </div>
    </div>

    <el-table :data="paginatedPermissions" stripe v-loading="loading">
      <el-table-column prop="permKey" label="权限标识" min-width="180">
        <template #default="{ row }">
          <el-tag size="small" :type="row.type === 'menu' ? 'warning' : 'info'" effect="plain">
            {{ row.permKey }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="权限名称" min-width="120" />
      <el-table-column prop="type" label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 'menu' ? 'warning' : 'info'" size="small">
            {{ row.type === 'menu' ? '菜单' : '操作' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="90">
        <template #default="{ row }">
          <el-tag :type="row.category === 'menu' ? 'primary' : row.category === 'api' ? 'warning' : 'success'" size="small">
            {{ row.category === 'menu' ? '菜单' : row.category === 'api' ? '接口' : '操作' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联角色" min-width="200">
        <template #default="{ row }">
          <div class="role-tags">
            <template v-for="(roleId, idx) in (row.roleIds || [])" :key="roleId">
              <el-tag
                v-if="isExpanded(row.id) || idx < 2"
                size="small"
                style="margin: 1px 2px"
              >
                {{ roleMap[roleId] || roleId }}
              </el-tag>
            </template>
            <el-button
              v-if="(row.roleIds || []).length > 2"
              link
              type="primary"
              size="small"
              @click="toggleRow(row)"
            >
              {{ isExpanded(row.id) ? '收起' : `+${(row.roleIds || []).length - 2} 更多` }}
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-permission="PERMISSIONS.ADMIN_ROLE" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" v-permission="PERMISSIONS.ADMIN_ROLE" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="perm-page__pagination">
      <el-pagination
        v-if="filteredPermissions.length > pageSize"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="filteredPermissions.length"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <!-- 编辑模式 -->
        <template v-if="editingId != null">
          <el-form-item label="权限标识" required>
            <el-input v-model="formData.permKey" disabled />
          </el-form-item>
          <!-- API 权限编辑时显示 HTTP 方法和路径 -->
          <template v-if="formData.category === 'api'">
            <el-form-item label="HTTP 方法">
              <el-select v-model="formData.httpMethod" style="width: 100%">
                <el-option v-for="m in httpMethodOptions" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="API 路径">
              <el-input v-model="formData.apiPath" placeholder="如 /api/kb/{id}" />
            </el-form-item>
          </template>
          <el-form-item label="权限名称" required>
            <el-input v-model="formData.name" placeholder="如 创建知识库" />
          </el-form-item>
          <el-form-item v-if="formData.category !== 'api'" label="权限类型" required>
            <el-radio-group v-model="formData.type">
              <el-radio v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="权限分类">
            <el-select v-model="formData.category" style="width: 100%">
              <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="权限描述（可选）" />
          </el-form-item>
        </template>
        <!-- 新增模式：按分类显示专用字段 -->
        <template v-else>
          <template v-if="formData.category === 'api'">
            <el-form-item label="HTTP 方法" required>
              <el-select v-model="formData.httpMethod" style="width: 100%">
                <el-option v-for="m in httpMethodOptions" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="API 路径" required>
              <el-input v-model="formData.apiPath" placeholder="如 /api/kb/{id}" />
            </el-form-item>
            <el-form-item label="权限标识">
              <el-input v-model="formData.permKey" placeholder="根据方法+路径自动生成" disabled />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="权限标识" required>
              <el-input v-model="formData.permKey" placeholder="如 kb:create" />
            </el-form-item>
          </template>
          <el-form-item label="权限名称" required>
            <el-input v-model="formData.name" placeholder="如 创建知识库" />
          </el-form-item>
          <el-form-item v-if="formData.category !== 'api'" label="权限类型" required>
            <el-radio-group v-model="formData.type">
              <el-radio v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="formData.category === 'page_action'" label="所属页面">
            <el-select v-model="formData.pagePath" style="width: 100%" placeholder="选择此操作所在的页面">
              <el-option v-for="opt in pageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="权限分类">
            <el-select v-model="formData.category" style="width: 100%">
              <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="权限描述（可选）" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.perm-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
  flex-wrap: wrap;
  gap: $spacing-sm;

  &__left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__right {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

.perm-page__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}
</style>
