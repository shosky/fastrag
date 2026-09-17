<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SystemRole } from '@/types/auth'
import type { PermissionDetail } from '@/types/auth'
import * as api from '@/api'
import {
  PERMISSION_TREE,
  ROLE_PERMISSIONS,
  ROLE_LABELS,
  MENU_PERMISSION_MAP,
} from '@/types/auth'

// --- 权限列表数据 ---
const permissionList = ref<PermissionDetail[]>([])

function buildPermissionList(): PermissionDetail[] {
  const perms: PermissionDetail[] = []

  for (const group of PERMISSION_TREE) {
    const groupName = group.label
    if (group.children) {
      for (const child of group.children) {
        if (child.children) {
          for (const leaf of child.children) {
            const type = groupName === '菜单权限' ? 'menu' as const : 'action' as const
            perms.push({ key: leaf.key, name: leaf.label, type, group: groupName })
          }
        } else {
          const type = groupName === '菜单权限' ? 'menu' as const : 'action' as const
          perms.push({ key: child.key, name: child.label, type, group: groupName })
        }
      }
    }
  }

  // 计算每个权限被哪些角色拥有
  return perms.map((p) => ({
    ...p,
    roleIds: Object.entries(ROLE_PERMISSIONS)
      .filter(([_, perms]) => perms.includes('*') || perms.includes(p.key))
      .map(([role]) => role),
  }))
}

permissionList.value = buildPermissionList()

// Tab 控制
const activeTab = ref('list')

// 搜索和筛选（与 tab 联动）
const searchKeyword = ref('')

const filterType = computed<'all' | 'menu' | 'action'>(() => {
  if (activeTab.value === 'menu') return 'menu'
  if (activeTab.value === 'action') return 'action'
  return 'all'
})

const filteredPermissions = computed(() => {
  let list = permissionList.value
  if (filterType.value !== 'all') {
    list = list.filter((p) => p.type === filterType.value)
  }
  if (searchKeyword.value) {
    list = list.filter((p) =>
      p.key.includes(searchKeyword.value) || p.name.includes(searchKeyword.value),
    )
  }
  return list
})

// 按分组统计
const groupStats = computed(() => {
  const stats: Record<string, number> = {}
  for (const p of permissionList.value) {
    stats[p.group] = (stats[p.group] || 0) + 1
  }
  return stats
})

// --- 菜单-权限映射 ---
function getMenuPermLabel(perms: string[]): string {
  return perms.map((p) => {
    const found = permissionList.value.find((pd) => pd.key === p)
    return found?.name || p
  }).join(' / ')
}

const selectedMenu = ref<any>(null)

function handleMenuSelect(data: any) {
  selectedMenu.value = data
}

function getMenuTreeData() {
  return MENU_PERMISSION_MAP.map((item) => ({
    ...item,
    label: item.title,
    children: item.children?.map((child) => ({
      ...child,
      label: child.title,
      children: child.children?.map((gc) => ({
        ...gc,
        label: gc.title,
      })),
    })),
  }))
}
// ===== 知识权限管理（知识库 ACL：新增 / 修改 / 删除 / 查询 / 库）=====
// 后端：GET /kb/{id}/acl、POST /kb/{id}/acl、PUT /kb/{id}/acl、DELETE /kb/{id}/acl/{userId}
//       GET /acl/users/{userId}/kbs（库视图：某用户可访问的知识库）
const KB_ROLES = [
  { value: 'owner', label: '所有者' },
  { value: 'editor', label: '编辑者' },
  { value: 'viewer', label: '查看者' },
]
const kbOptions = ref<any[]>([])
const aclKbId = ref('')
const aclList = ref<any[]>([])
const aclLoading = ref(false)
const aclKeyword = ref('')
const grantDialog = ref(false)
const grantEditing = ref(false)
const grantForm = ref<{ userId: string; kbRole: string }>({ userId: '', kbRole: 'viewer' })
const userOptions = ref<any[]>([])
// 库视图：按用户查看可访问知识库
const libUserId = ref('')
const libKbs = ref<{ id: string; name: string; role: string }[]>([])
const libLoading = ref(false)

const filteredAcl = computed(() =>
  aclKeyword.value
    ? aclList.value.filter(
        (x) =>
          (x.userName || '').includes(aclKeyword.value) ||
          (x.userId || '').includes(aclKeyword.value),
      )
    : aclList.value,
)
const kbName = (id: string) => kbOptions.value.find((k) => k.id === id)?.name || id
const roleLabel = (r: string) => KB_ROLES.find((x) => x.value === r)?.label || r

async function loadKbOptions() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbOptions.value = res?.list || res?.records || res || []
    if (!aclKbId.value && kbOptions.value.length) aclKbId.value = kbOptions.value[0].id
  } catch {
    kbOptions.value = []
  }
}
async function loadAcl() {
  if (!aclKbId.value) return
  aclLoading.value = true
  try {
    aclList.value = ((await api.getKbAcl(aclKbId.value)) as any) || []
  } catch {
    aclList.value = []
  } finally {
    aclLoading.value = false
  }
}
async function loadUserOptions() {
  try {
    const res: any = await api.getPersonnel({ page: 1, pageSize: 100 })
    userOptions.value = res?.records || res?.list || res || []
  } catch {
    userOptions.value = []
  }
}
function openGrant(row?: any) {
  grantEditing.value = !!row
  grantForm.value = row
    ? { userId: row.userId, kbRole: row.kbRole }
    : { userId: '', kbRole: 'viewer' }
  grantDialog.value = true
}
async function saveGrant() {
  if (!grantForm.value.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    if (grantEditing.value) {
      // 修改：PUT 全量覆盖（同 userId 替换角色）
      const payload = aclList.value.map((x) =>
        x.userId === grantForm.value.userId
          ? { userId: x.userId, kbRole: grantForm.value.kbRole }
          : { userId: x.userId, kbRole: x.kbRole },
      )
      await api.setKbAcl(aclKbId.value, payload)
      ElMessage.success('权限已修改')
    } else {
      await api.addKbAclEntry(aclKbId.value, {
        userId: grantForm.value.userId,
        kbRole: grantForm.value.kbRole,
      })
      ElMessage.success('授权已新增')
    }
    grantDialog.value = false
    await loadAcl()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
async function removeAcl(row: any) {
  try {
    await ElMessageBox.confirm(`确定回收「${row.userName || row.userId}」的权限吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.removeKbAclEntry(aclKbId.value, row.userId)
    ElMessage.success('权限已删除')
    await loadAcl()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}
async function loadUserKbs() {
  if (!libUserId.value) {
    ElMessage.warning('请选择用户')
    return
  }
  libLoading.value = true
  try {
    const ids: any = await api.getUserAccessibleKbs(libUserId.value)
    const list: string[] = Array.isArray(ids) ? ids : ids?.list || []
    libKbs.value = list.map((id) => ({ id, name: kbName(id), role: '' }))
    // 逐库取角色，便于演示「库」视图
    for (const item of libKbs.value) {
      try {
        const role: any = await api.getUserKbRole(libUserId.value, item.id)
        item.role = roleLabel(role)
      } catch {
        item.role = '-'
      }
    }
  } catch {
    libKbs.value = []
  } finally {
    libLoading.value = false
  }
}
onMounted(async () => {
  await Promise.all([loadKbOptions(), loadUserOptions()])
  await loadAcl()
})
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>权限管理</h3>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部权限" name="list" />
      <el-tab-pane label="菜单权限" name="menu" />
      <el-tab-pane label="操作权限" name="action" />
      <el-tab-pane label="菜单-权限映射" name="menu-map" />
      <el-tab-pane label="知识权限" name="kb-acl" />
    </el-tabs>

    <!-- ===== 知识权限管理（知识库 ACL）===== -->
    <template v-if="activeTab === 'kb-acl'">
      <div class="perm-list-toolbar">
        <el-select v-model="aclKbId" placeholder="选择知识库" style="width: 260px" @change="loadAcl">
          <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input
          v-model="aclKeyword"
          placeholder="按用户搜索授权记录"
          clearable
          style="width: 220px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="openGrant()">
          <el-icon><Plus /></el-icon>新增授权
        </el-button>
        <div class="perm-list-toolbar__right">
          <el-tag size="small" type="info">共 {{ filteredAcl.length }} 条授权</el-tag>
        </div>
      </div>

      <el-table :data="filteredAcl" stripe v-loading="aclLoading">
        <el-table-column prop="userName" label="用户" min-width="140">
          <template #default="{ row }">
            {{ row.userName || '-' }}
            <span style="color: var(--el-text-color-secondary); font-size: 12px">（{{ row.userId }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="知识库角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.kbRole === 'owner' ? 'danger' : row.kbRole === 'editor' ? 'warning' : 'info'">
              {{ roleLabel(row.kbRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="grantedBy" label="授权人" width="140" />
        <el-table-column prop="grantedAt" label="授权时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openGrant(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="removeAcl(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 库视图：按用户查看可访问的知识库 -->
      <div class="section-header" style="margin-top: 20px">
        <h3 style="font-size: 15px">权限库视图（按用户查看可访问知识库）</h3>
      </div>
      <div class="perm-list-toolbar">
        <el-select v-model="libUserId" placeholder="选择用户" style="width: 240px">
          <el-option
            v-for="u in userOptions"
            :key="u.id"
            :label="`${u.realName || u.username}（${u.username}）`"
            :value="u.id"
          />
        </el-select>
        <el-button :loading="libLoading" @click="loadUserKbs">查询</el-button>
      </div>
      <el-table :data="libKbs" stripe size="small">
        <el-table-column prop="name" label="知识库" min-width="220" />
        <el-table-column prop="role" label="角色" width="120" align="center" />
        <el-table-column prop="id" label="知识库ID" min-width="200" />
      </el-table>

      <!-- 新增/修改授权弹窗 -->
      <el-dialog v-model="grantDialog" :title="grantEditing ? '修改知识权限' : '新增知识权限'" width="440px">
        <el-form label-width="90px">
          <el-form-item label="用户">
            <el-select v-model="grantForm.userId" :disabled="grantEditing" placeholder="选择用户" style="width: 100%">
              <el-option
                v-for="u in userOptions"
                :key="u.id"
                :label="`${u.realName || u.username}（${u.username}）`"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="知识库角色">
            <el-select v-model="grantForm.kbRole" style="width: 100%">
              <el-option v-for="r in KB_ROLES" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识库">
            <span>{{ kbName(aclKbId) }}</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="grantDialog = false">取消</el-button>
          <el-button type="primary" @click="saveGrant">保存</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- 权限列表 -->
    <template v-if="activeTab !== 'menu-map'">
      <div class="perm-list-toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索权限名称或标识"
          clearable
          style="width: 240px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="perm-list-toolbar__right">
          <el-tag v-for="(count, group) in groupStats" :key="group" size="small" type="info" style="margin: 2px">
            {{ group }}: {{ count }}
          </el-tag>
        </div>
      </div>

      <el-table :data="filteredPermissions" stripe>
        <el-table-column prop="key" label="权限标识" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'menu' ? 'warning' : 'info'" effect="plain">
              {{ row.key }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="权限名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'menu' ? 'warning' : 'info'" size="small">
              {{ row.type === 'menu' ? '菜单' : '操作' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="group" label="分组" width="100" />
        <el-table-column label="关联角色" min-width="200">
          <template #default="{ row }">
            <el-tag
              v-for="roleId in row.roleIds"
              :key="roleId"
              size="small"
              :type="roleId === 'super_admin' ? 'danger' : 'success'"
              style="margin: 1px 2px"
            >
              {{ ROLE_LABELS[roleId as SystemRole] || roleId }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 菜单-权限映射 -->
    <template v-else>
      <div class="menu-perm-layout">
        <div class="menu-perm-tree">
          <h4>菜单结构</h4>
          <el-tree
            :data="getMenuTreeData()"
            node-key="path"
            default-expand-all
            highlight-current
            :props="{ children: 'children', label: 'label' }"
            @current-change="handleMenuSelect"
          />
        </div>
        <div class="menu-perm-detail">
          <h4>菜单权限配置</h4>
          <template v-if="selectedMenu">
            <div class="menu-perm-detail__info">
              <div class="menu-perm-detail__item">
                <span class="menu-perm-detail__label">菜单路径：</span>
                <el-tag size="small" effect="plain">{{ selectedMenu.path }}</el-tag>
              </div>
              <div class="menu-perm-detail__item">
                <span class="menu-perm-detail__label">菜单名称：</span>
                <span>{{ selectedMenu.title }}</span>
              </div>
              <div class="menu-perm-detail__item">
                <span class="menu-perm-detail__label">所需权限：</span>
                <div class="menu-perm-detail__perms">
                  <el-tag
                    v-for="perm in selectedMenu.requiredPerms"
                    :key="perm"
                    size="small"
                    type="warning"
                  >
                    {{ getMenuPermLabel([perm]) }}
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
          <el-empty v-else description="请选择左侧菜单项查看权限配置" :image-size="80" />
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.perm-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
  flex-wrap: wrap;
  gap: $spacing-sm;

  &__right {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

// --- 菜单-权限映射 ---
.menu-perm-layout {
  display: flex;
  gap: $spacing-lg;
  height: calc(100vh - 280px);
}

.menu-perm-tree {
  width: 280px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  overflow-y: auto;
  flex-shrink: 0;

  h4 {
    margin: 0 0 $spacing-sm;
    font-size: 14px;
  }
}

.menu-perm-detail {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;

  h4 {
    margin: 0 0 $spacing-base;
    font-size: 14px;
  }

  &__info {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__item {
    display: flex;
    align-items: flex-start;
    gap: $spacing-sm;
  }

  &__label {
    font-size: 13px;
    color: $text-secondary;
    min-width: 80px;
    flex-shrink: 0;
  }

  &__perms {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-xs;
  }
}
</style>
