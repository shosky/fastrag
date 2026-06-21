<script setup lang="ts">
import { ref, computed } from 'vue'
import type { SystemRole } from '@/types/auth'
import type { PermissionDetail } from '@/types/auth'
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
    </el-tabs>

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
