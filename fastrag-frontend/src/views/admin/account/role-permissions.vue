<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { RoleMeta } from '@/types/auth'
import { PERMISSION_TREE } from '@/types/auth'
import { getRole, updateRole } from '@/mock/auth-roles'

const route = useRoute()
const router = useRouter()
const roleId = route.params.id as string

const role = ref<RoleMeta | null>(null)
const checkedPerms = ref<string[]>([])
const loading = ref(true)

// 从 PERMISSION_TREE 构建按分组的权限列表
interface PermGroup {
  key: string
  label: string
  children: { key: string; label: string }[]
}

const permGroups = computed<PermGroup[]>(() => {
  return PERMISSION_TREE.map((group) => ({
    key: group.key,
    label: group.label,
    children: (group.children || []).flatMap((child) => {
      if (child.children) {
        return child.children.map((leaf) => ({ key: leaf.key, label: leaf.label }))
      }
      return [{ key: child.key, label: child.label }]
    }),
  }))
})

onMounted(() => {
  const data = getRole(roleId)
  if (!data) {
    ElMessage.error('角色不存在')
    router.push('/admin/account/roles')
    return
  }
  role.value = data
  checkedPerms.value = [...data.permissions]
  loading.value = false
})

const isSuperAdmin = computed(() => role.value?.key === 'super_admin')

// 判断某个分组是否全选
function isGroupAllChecked(group: PermGroup): boolean {
  return group.children.every((p) => checkedPerms.value.includes('*') || checkedPerms.value.includes(p.key))
}

// 判断某个分组是否部分选中
function isGroupIndeterminate(group: PermGroup): boolean {
  const checked = group.children.filter((p) => checkedPerms.value.includes(p.key) || checkedPerms.value.includes('*')).length
  return checked > 0 && checked < group.children.length
}

// 全选/全不选某分组
function toggleGroup(group: PermGroup) {
  if (isSuperAdmin.value) return
  const allChecked = isGroupAllChecked(group)
  for (const p of group.children) {
    const idx = checkedPerms.value.indexOf(p.key)
    if (allChecked) {
      if (idx >= 0) checkedPerms.value.splice(idx, 1)
    } else {
      if (idx < 0) checkedPerms.value.push(p.key)
    }
  }
}

// 切换单个权限
function togglePerm(permKey: string) {
  if (isSuperAdmin.value) return
  const idx = checkedPerms.value.indexOf(permKey)
  if (idx >= 0) {
    checkedPerms.value.splice(idx, 1)
  } else {
    checkedPerms.value.push(permKey)
  }
}

// 保存
function handleSave() {
  if (!role.value) return
  // 过滤掉分组 key（只保留叶子节点权限）
  const leafKeys = PERMISSION_TREE.flatMap((g) =>
    (g.children || []).flatMap((c) => (c.children ? c.children.map((l) => l.key) : [c.key])),
  )
  const perms = checkedPerms.value.filter((k) => leafKeys.includes(k) || k === '*')

  updateRole(roleId, {
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

      <!-- 权限分组列表 -->
      <div class="perm-groups">
        <div v-for="group in permGroups" :key="group.key" class="perm-group card-panel">
          <div class="perm-group__header">
            <el-checkbox
              :model-value="isGroupAllChecked(group)"
              :indeterminate="isGroupIndeterminate(group)"
              :disabled="isSuperAdmin"
              @change="toggleGroup(group)"
            >
              <strong>{{ group.label }}</strong>
            </el-checkbox>
            <span class="perm-group__count">
              {{ group.children.filter(p => checkedPerms.includes(p.key) || checkedPerms.includes('*')).length }} / {{ group.children.length }}
            </span>
          </div>
          <div class="perm-group__items">
            <div
              v-for="perm in group.children"
              :key="perm.key"
              class="perm-item"
              :class="{ 'perm-item--checked': checkedPerms.includes(perm.key) || checkedPerms.includes('*') }"
              @click="togglePerm(perm.key)"
            >
              <el-checkbox
                :model-value="checkedPerms.includes(perm.key) || checkedPerms.includes('*')"
                :disabled="isSuperAdmin"
                size="small"
              />
              <span class="perm-item__name">{{ perm.label }}</span>
              <span class="perm-item__key">{{ perm.key }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 保存按钮 -->
      <div v-if="!isSuperAdmin" class="perm-actions">
        <el-button @click="router.push('/admin/account/roles')">取消</el-button>
        <el-button type="primary" @click="handleSave">保存权限配置</el-button>
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

  &__count {
    font-size: 12px;
    color: $text-secondary;
  }

  &__items {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: $spacing-xs;
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

.perm-actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  padding-top: $spacing-lg;
  margin-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}
</style>
