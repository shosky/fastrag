<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { RoleMeta } from '@/types/auth'
import { PERMISSION_TREE } from '@/types/auth'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const roleId = route.params.id as string

const role = ref<RoleMeta | null>(null)
const checkedPerms = ref<string[]>([])
const loading = ref(true)
const activeTab = ref('menu')

interface FlatPerm {
  key: string
  label: string
  category: string // 'menu' | 'page_action'
  group?: string
  children?: FlatPerm[]
}

// 从 PERMISSION_TREE 构建每个Tab的数据
const menuPerms = computed<FlatPerm[]>(() => {
  const cat = PERMISSION_TREE.find((t) => t.key === 'cat_menu')
  return cat?.children?.map(buildFlatPerm) || []
})

const actionPerms = computed<FlatPerm[]>(() => {
  const cat = PERMISSION_TREE.find((t) => t.key === 'cat_page_action')
  return cat?.children?.map(buildFlatPerm) || []
})

function buildFlatPerm(node: any): FlatPerm {
  const f: FlatPerm = { key: node.key, label: node.label, category: 'page_action' }
  if (node.children && node.children.length > 0) {
    f.children = node.children.map(buildFlatPerm)
  }
  return f
}

// 提取所有叶子节点 key
function getLeafKeys(nodes: FlatPerm[]): string[] {
  const result: string[] = []
  for (const n of nodes) {
    if (n.children && n.children.length > 0) {
      result.push(...getLeafKeys(n.children))
    } else {
      result.push(n.key)
    }
  }
  return result
}

// 获取叶子节点标签
function getLeafLabel(nodes: FlatPerm[], key: string): string {
  for (const n of nodes) {
    if (n.key === key) return n.label
    if (n.children) {
      const found = getLeafLabel(n.children, key)
      if (found) return found
    }
  }
  return key
}

onMounted(async () => {
  const data = (await api.getRoleDetail(roleId)) as any
  if (!data) {
    ElMessage.error('角色不存在')
    router.push('/admin/account/roles')
    return
  }
  role.value = data
  checkedPerms.value = [...(data.permissions || [])]
  loading.value = false
})

const isSuperAdmin = computed(() => role.value?.key === 'super_admin')

// ---- Tab: 菜单权限 ----
function isItemChecked(key: string): boolean {
  return checkedPerms.value.includes('*') || checkedPerms.value.includes(key)
}

function toggleItem(key: string) {
  if (isSuperAdmin.value) return
  const idx = checkedPerms.value.indexOf(key)
  if (idx >= 0) {
    checkedPerms.value.splice(idx, 1)
  } else {
    checkedPerms.value.push(key)
  }
}

function isGroupAllChecked(children: FlatPerm[]): boolean {
  if (!children || children.length === 0) return false
  return children.every((c) => isItemChecked(c.key) || (c.children && isGroupAllChecked(c.children)))
}

function isGroupIndeterminate(group: FlatPerm): boolean {
  if (!group.children || group.children.length === 0) return false
  const checked = group.children.filter((c) => isItemChecked(c.key) || (c.children && isGroupAllChecked(c.children || []))).length
  return checked > 0 && checked < group.children.length
}

function toggleGroup(group: FlatPerm) {
  if (isSuperAdmin.value) return
  const allChecked = isGroupAllChecked(group.children || [])
  const toggleKeys = (nodes: FlatPerm[]) => {
    for (const n of nodes) {
      if (n.children && n.children.length > 0) {
        toggleKeys(n.children)
      } else {
        const idx = checkedPerms.value.indexOf(n.key)
        if (allChecked) {
          if (idx >= 0) checkedPerms.value.splice(idx, 1)
        } else {
          if (idx < 0) checkedPerms.value.push(n.key)
        }
      }
    }
  }
  toggleKeys(group.children || [])
}

// ---- Tab: API接口 ----
const apiPerms = ref<string[]>([])

async function loadApiPerms() {
  try {
    const perms = (await api.getPermissions()) as any[] || []
    apiPerms.value = perms.filter((p: any) => p.category === 'api').map((p: any) => p.permKey)
  } catch {
    apiPerms.value = []
  }
}

onMounted(() => {
  loadApiPerms()
})

// ---- 保存 ----
async function handleSave() {
  if (!role.value) return
  const perms = checkedPerms.value
  await api.updateRole(roleId, {
    name: role.value.name,
    description: role.value.description,
    permissions: perms,
  })
  ElMessage.success('权限配置已保存')
  router.push('/admin/account/roles')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.push('/admin/account/roles')">
        <el-icon><ArrowLeft /></el-icon>返回角色列表
      </el-button>
      <h3 v-if="role">权限配置 — {{ role.name }}</h3>
    </div>

    <div v-if="loading" v-loading="true" style="min-height: 300px" />

    <template v-else-if="role">
      <!-- 角色信息 -->
      <div class="role-info card-panel">
        <div class="role-info__item">
          <span class="role-info__label">角色名称：</span>
          <span>{{ role.name }}</span>
          <el-tag v-if="role.isDefault" type="success" size="small" style="margin-left: 8px">默认</el-tag>
        </div>
        <div class="role-info__item">
          <span class="role-info__label">描述：</span>
          <span>{{ role.description || '无' }}</span>
        </div>
        <div class="role-info__item">
          <span class="role-info__label">已授予权限：</span>
          <el-tag type="info" size="small">
            {{ checkedPerms.includes('*') ? '全部权限' : checkedPerms.length + ' 项' }}
          </el-tag>
        </div>
      </div>

      <!-- 超管提示 -->
      <div v-if="isSuperAdmin" class="super-admin-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>超级管理员拥有系统全部权限，不可修改。</span>
      </div>

      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" class="perm-tabs">
        <el-tab-pane label="菜单权限" name="menu">
          <div class="perm-groups">
            <div v-for="group in menuPerms" :key="group.key" class="perm-group card-panel">
              <div class="perm-group__header">
                <el-checkbox
                  v-if="group.children && group.children.length > 0"
                  :model-value="isGroupAllChecked(group.children)"
                  :indeterminate="isGroupIndeterminate(group)"
                  :disabled="isSuperAdmin"
                  @change="toggleGroup(group)"
                >
                  <strong>{{ group.label }}</strong>
                </el-checkbox>
                <strong v-else>{{ group.label }}</strong>
              </div>
              <div class="perm-group__items perm-group__items--tree">
                <template v-for="child in (group.children || [])" :key="child.key">
                  <!-- 三级菜单（含有子节点） -->
                  <div v-if="child.children && child.children.length > 0" class="perm-subgroup">
                    <div class="perm-subgroup__title">
                      <el-checkbox
                        :model-value="isGroupAllChecked(child.children)"
                        :indeterminate="isGroupIndeterminate(child)"
                        :disabled="isSuperAdmin"
                        size="small"
                        @change="toggleGroup(child)"
                      >
                        {{ child.label }}
                      </el-checkbox>
                    </div>
                    <div class="perm-subgroup__items">
                      <div
                        v-for="leaf in child.children"
                        :key="leaf.key"
                        class="perm-item"
                        :class="{ 'perm-item--checked': isItemChecked(leaf.key) }"
                        @click="toggleItem(leaf.key)"
                      >
                        <el-checkbox
                          :model-value="isItemChecked(leaf.key)"
                          :disabled="isSuperAdmin"
                          size="small"
                        />
                        <span class="perm-item__name">{{ leaf.label }}</span>
                      </div>
                    </div>
                  </div>
                  <!-- 二级菜单（叶子节点） -->
                  <div
                    v-else
                    class="perm-item"
                    :class="{ 'perm-item--checked': isItemChecked(child.key) }"
                    @click="toggleItem(child.key)"
                  >
                    <el-checkbox
                      :model-value="isItemChecked(child.key)"
                      :disabled="isSuperAdmin"
                      size="small"
                    />
                    <span class="perm-item__name">{{ child.label }}</span>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="页面操作" name="action">
          <div class="perm-groups">
            <div v-for="group in actionPerms" :key="group.key" class="perm-group card-panel">
              <div class="perm-group__header">
                <el-checkbox
                  v-if="group.children && group.children.length > 0"
                  :model-value="isGroupAllChecked(group.children)"
                  :indeterminate="isGroupIndeterminate(group)"
                  :disabled="isSuperAdmin"
                  @change="toggleGroup(group)"
                >
                  <strong>{{ group.label }}</strong>
                </el-checkbox>
                <strong v-else>{{ group.label }}</strong>
              </div>
              <div class="perm-group__items">
                <div
                  v-for="child in (group.children || [])"
                  :key="child.key"
                  class="perm-item"
                  :class="{ 'perm-item--checked': isItemChecked(child.key) }"
                  @click="toggleItem(child.key)"
                >
                  <el-checkbox
                    :model-value="isItemChecked(child.key)"
                    :disabled="isSuperAdmin"
                    size="small"
                  />
                  <span class="perm-item__name">{{ child.label }}</span>
                  <span class="perm-item__key">{{ child.key }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="API接口" name="api">
          <div class="perm-groups">
            <div class="perm-group card-panel">
              <div class="perm-group__header">
                <strong>API接口权限</strong>
              </div>
              <div class="perm-group__items">
                <div
                  v-for="permKey in apiPerms"
                  :key="permKey"
                  class="perm-item"
                  :class="{ 'perm-item--checked': isItemChecked(permKey) }"
                  @click="toggleItem(permKey)"
                >
                  <el-checkbox
                    :model-value="isItemChecked(permKey)"
                    :disabled="isSuperAdmin"
                    size="small"
                  />
                  <span class="perm-item__name">{{ permKey }}</span>
                  <span class="perm-item__key">{{ permKey }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 保存按钮（固定底部） -->
      <div v-if="!isSuperAdmin" class="perm-actions-fixed">
        <div class="perm-actions-fixed__inner">
          <el-button size="large" @click="router.push('/admin/account/roles')">取消</el-button>
          <el-button size="large" type="primary" @click="handleSave">保存权限配置</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.role-info {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-lg;
  margin-bottom: $spacing-base;

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 14px;
  }

  &__label {
    color: $text-secondary;
    font-size: 13px;
  }
}

.super-admin-hint {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-base;
  background: #fff3e0;
  border-radius: $radius-base;
  font-size: 13px;
  color: $text-secondary;
  margin-bottom: $spacing-base;
}

.perm-tabs {
  margin-bottom: 80px; /* 给固定底部留空间 */
}

.perm-groups {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.perm-group {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-lighter;
  }

  &__items {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: $spacing-xs;
  }

  &__items--tree {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }
}

.perm-subgroup {
  padding: $spacing-sm;
  background: $bg-white;
  border-radius: $radius-sm;

  &__title {
    margin-bottom: $spacing-xs;
    font-size: 13px;
    font-weight: 500;
  }

  &__items {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 2px;
    padding-left: $spacing-lg;
  }
}

.perm-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 6px $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  transition: background 0.15s;

  &:hover { background: $bg-hover; }
  &--checked { background: $bg-active; }

  &__name {
    font-size: 13px;
    color: $text-primary;
    flex: 1;
  }

  &__key {
    font-size: 11px;
    color: $text-placeholder;
    font-family: monospace;
  }
}

.perm-actions-fixed {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: $bg-white;
  border-top: 1px solid $border-base;
  padding: $spacing-sm 0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);

  &__inner {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
    padding: 0 $spacing-lg;
  }
}
</style>
