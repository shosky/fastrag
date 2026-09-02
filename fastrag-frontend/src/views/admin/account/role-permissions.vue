<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { RoleMeta } from '@/types/auth'
import { MENU_PERM_TREE, ACTION_API_LINKS, type MenuPermNode } from '@/config/menu-perm-tree'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const roleId = route.params.id as string

const role = ref<RoleMeta | null>(null)
const checkedPerms = ref<string[]>([])
const loading = ref(true)

/**
 * 联动组：页面操作与其对应 API 视为一个整体开关。
 * 组内勾选/取消任一项，其余成员同步勾选/取消。
 *
 * 声明式分组（ACTION_API_LINKS）可能重叠——多个操作共享同一 API
 * （如 skill:create/edit/share 都涉及 api:skill:file:manage）。
 * 用并查集将共享成员的组合并为一个，保证任一入口点击效果一致。
 */
function mergeLinkGroups(entries: [string, string[]][]): string[][] {
  // 键 → 组下标；共享键出现时把两个组标为同一集合
  const ownerOf = new Map<string, number>()
  const groups: string[][] = []
  const unionRoot: number[] = [] // unionRoot[i] = 组 i 当前合并到的代表组下标
  const findRoot = (i: number): number => {
    while (unionRoot[i] !== i) {
      unionRoot[i] = unionRoot[unionRoot[i]]
      i = unionRoot[i]
    }
    return i
  }
  const merge = (a: number, b: number) => {
    const ra = findRoot(a)
    const rb = findRoot(b)
    if (ra !== rb) unionRoot[rb] = ra
  }
  for (const [action, apis] of entries) {
    const members = [action, ...apis]
    const i = groups.length
    groups.push(members)
    unionRoot.push(i)
    for (const key of members) {
      const existing = ownerOf.get(key)
      if (existing === undefined) {
        ownerOf.set(key, i)
      } else {
        merge(existing, i)
      }
    }
  }
  // 按代表组归并成员（去重）
  const byRoot = new Map<number, Set<string>>()
  for (let i = 0; i < groups.length; i++) {
    const root = findRoot(i)
    if (!byRoot.has(root)) byRoot.set(root, new Set())
    for (const key of groups[i]) byRoot.get(root)!.add(key)
  }
  return [...byRoot.values()].map((set) => [...set])
}

const LINK_GROUPS: string[][] = mergeLinkGroups(Object.entries(ACTION_API_LINKS))

/** 权限键 → 所属联动组（已合并）；不在任何组内返回 null */
const linkGroupOfKey = new Map<string, string[]>()
for (const group of LINK_GROUPS) {
  for (const key of group) linkGroupOfKey.set(key, group)
}

/** permKey → 权限名称（来自后端权限树，缺失时回退显示 permKey） */
const permNameMap = ref<Record<string, string>>({})
/** 后端已登记的权限键集合；null 表示权限树加载失败（不过滤，全量展示映射表） */
const existingKeys = ref<Set<string> | null>(null)

const isSuperAdmin = computed(() => role.value?.key === 'super_admin')

// ---- 菜单权限树工具函数 ----

/** 过滤掉后端已不存在的叶子权限键 */
function visibleLeaves(keys: string[] | undefined): string[] {
  if (!keys) return []
  if (!existingKeys.value) return keys
  return keys.filter((k) => existingKeys.value!.has(k))
}

/** 节点子树内实际生效的全部权限键（自身门禁键 + 子孙菜单键 + 可见的操作/API） */
function subtreeKeys(node: MenuPermNode): string[] {
  const keys = [...visibleLeaves(node.keys), ...visibleLeaves(node.actions), ...visibleLeaves(node.apis)]
  for (const child of node.children ?? []) keys.push(...subtreeKeys(child))
  return keys
}

function isItemChecked(key: string): boolean {
  return checkedPerms.value.includes('*') || checkedPerms.value.includes(key)
}

/** 节点内容（子菜单 / 页面操作 / API）是否展开：自身或任一子孙权限被勾选即展开 */
function isNodeExpanded(node: MenuPermNode): boolean {
  return subtreeKeys(node).some((k) => isItemChecked(k))
}

function isNodeAllChecked(node: MenuPermNode): boolean {
  const keys = subtreeKeys(node)
  return keys.length > 0 && keys.every((k) => isItemChecked(k))
}

function isNodeIndeterminate(node: MenuPermNode): boolean {
  const keys = subtreeKeys(node)
  if (keys.length === 0) return false
  const checked = keys.filter((k) => isItemChecked(k)).length
  return checked > 0 && checked < keys.length
}

function checkedCount(node: MenuPermNode): number {
  return subtreeKeys(node).filter((k) => isItemChecked(k)).length
}

/** 收集树中所有叶子页面节点（无子菜单的节点） */
function leafPageNodes(nodes: MenuPermNode[], acc: MenuPermNode[] = []): MenuPermNode[] {
  for (const n of nodes) {
    if (n.children && n.children.length > 0) leafPageNodes(n.children, acc)
    else acc.push(n)
  }
  return acc
}
const allLeafPages = leafPageNodes(MENU_PERM_TREE)

/**
 * 键是否被 node 之外的页面"占用"：其他叶子页面的门禁键已勾选，
 * 且该键属于其子树（页面操作/API/门禁键），取消 node 时不应摘除。
 */
function isKeyOwnedByOtherPages(key: string, node: MenuPermNode): boolean {
  for (const page of allLeafPages) {
    if (page === node) continue
    const ownKeys = [...visibleLeaves(page.keys), ...visibleLeaves(page.actions), ...visibleLeaves(page.apis)]
    if (!ownKeys.includes(key)) continue
    // 该页面依赖此键：页面门禁仍勾选则保留
    if (visibleLeaves(page.keys).some((k) => isItemChecked(k))) return true
  }
  return false
}

/**
 * 取消 node 子树：仅移除不被其他勾选页面占用的键（多对多挂载下，
 * 如 app:use 同时挂应用中心/应用运行，取消一方不得摘除共享键）。
 */
function removeSubtreeKeys(node: MenuPermNode) {
  const keys = subtreeKeys(node)
  for (const k of keys) {
    if (isKeyOwnedByOtherPages(k, node)) continue
    checkedPerms.value = checkedPerms.value.filter((p) => p !== k)
  }
}

/** 勾选/取消节点：整棵子树（含页面操作与API）级联全选/全不选 */
function toggleNode(node: MenuPermNode) {
  if (isSuperAdmin.value) return
  const keys = subtreeKeys(node)
  if (keys.length === 0) return
  if (keys.every((k) => isItemChecked(k))) {
    removeSubtreeKeys(node)
  } else {
    for (const k of keys) if (!isItemChecked(k)) checkedPerms.value.push(k)
  }
}

/** 叶子勾选：联动组内整体切换（勾操作带起对应 API，勾 API 带起对应操作），组外单独切换 */
function toggleLeaf(key: string) {
  if (isSuperAdmin.value) return
  const group = linkGroupOfKey.get(key)
  if (!group) {
    const idx = checkedPerms.value.indexOf(key)
    if (idx >= 0) {
      // 删除所有匹配项（防止历史重复数据残留）
      checkedPerms.value = checkedPerms.value.filter((p) => p !== key)
    } else {
      checkedPerms.value.push(key)
    }
    return
  }
  const willCheck = !group.every((k) => isItemChecked(k))
  const groupSet = new Set(group)
  if (willCheck) {
    for (const k of group) if (!isItemChecked(k)) checkedPerms.value.push(k)
  } else {
    checkedPerms.value = checkedPerms.value.filter((p) => !groupSet.has(p))
  }
}

// ---- 展平为行：每个顶级菜单一张卡片，内部按深度缩进渲染 ----

interface LeafItem {
  key: string
  label: string
  /** 所属联动组（页面操作↔API 对应关系），null 表示独立项 */
  linkGroup: string[] | null
}

interface PermRow {
  rowKey: string
  kind: 'menu' | 'section' | 'leaves'
  depth: number
  node?: MenuPermNode
  label?: string
  items?: LeafItem[]
}

function appendSections(node: MenuPermNode, depth: number, path: string, rows: PermRow[]) {
  const actions = visibleLeaves(node.actions)
  const apis = visibleLeaves(node.apis)
  if (actions.length > 0) {
    rows.push({ rowKey: `${path}#a`, kind: 'section', depth, label: '页面操作' })
    rows.push({
      rowKey: `${path}#ai`,
      kind: 'leaves',
      depth,
      items: actions.map((k) => ({ key: k, label: permNameMap.value[k] || k, linkGroup: linkGroupOfKey.get(k) ?? null })),
    })
  }
  if (apis.length > 0) {
    rows.push({ rowKey: `${path}#p`, kind: 'section', depth, label: 'API 接口' })
    rows.push({
      rowKey: `${path}#pi`,
      kind: 'leaves',
      depth,
      items: apis.map((k) => ({ key: k, label: permNameMap.value[k] || k, linkGroup: linkGroupOfKey.get(k) ?? null })),
    })
  }
}

function buildRows(node: MenuPermNode, depth: number, path: string, rows: PermRow[]) {
  const rowKey = `${path}/${node.title}`
  rows.push({ rowKey, kind: 'menu', depth, node })
  if (!isNodeExpanded(node)) return
  appendSections(node, depth, rowKey, rows)
  for (const child of node.children ?? []) buildRows(child, depth + 1, rowKey, rows)
}

const topNodes = MENU_PERM_TREE

/** 顶级菜单 → 卡片内行列表 */
const rowsByTop = computed(() => {
  const map = new Map<MenuPermNode, PermRow[]>()
  for (const top of topNodes) {
    const rows: PermRow[] = []
    if (isNodeExpanded(top)) {
      appendSections(top, 0, top.title, rows)
      for (const child of top.children ?? []) buildRows(child, 1, top.title, rows)
    }
    map.set(top, rows)
  }
  return map
})

function indentOf(depth: number): string {
  return `${depth * 24}px`
}

onMounted(async () => {
  const data = (await api.getRoleDetail(roleId)) as any
  if (!data) {
    ElMessage.error('角色不存在')
    router.push('/admin/account/roles')
    return
  }
  role.value = data

  // 加载后端权限树：构建 permKey→名称映射与存在性集合，失败则回退展示映射表全量
  try {
    const treeRes: any = await api.getPermissionTree()
    const map: Record<string, string> = {}
    const keys = new Set<string>()
    const walk = (nodes: any[]) => {
      for (const n of nodes || []) {
        if (n.permKey) {
          map[n.permKey] = n.name
          keys.add(n.permKey)
        }
        if (n.children) walk(n.children)
      }
    }
    walk(Array.isArray(treeRes) ? treeRes : [])
    permNameMap.value = map
    existingKeys.value = keys
  } catch {
    permNameMap.value = {}
    existingKeys.value = null
  }

  const saved: string[] = data.permissions || []
  if (saved.includes('*')) {
    checkedPerms.value = ['*']
  } else if (saved.length === 0) {
    // 从未配置过权限的角色：默认全选
    const all: string[] = []
    for (const top of topNodes) all.push(...subtreeKeys(top))
    checkedPerms.value = [...new Set(all)]
  } else {
    // 存量回显：联动组内任一勾选则整组勾上，保证操作↔API 不变量成立
    const set = new Set(saved)
    for (const group of LINK_GROUPS) {
      if (group.some((k) => set.has(k))) group.forEach((k) => set.add(k))
    }
    checkedPerms.value = [...set]
  }

  loading.value = false
})

// ---- 保存 ----
async function handleSave() {
  if (!role.value) return
  // 去重后提交（后端同样会去重，双保险）
  const perms = [...new Set(checkedPerms.value)]
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

      <!-- 操作提示 -->
      <div v-else class="perm-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>先勾选菜单页面，页面内的所有操作与 API 接口将默认全选。页面操作与对应 API 联动：勾选任一侧，另一侧同步勾选/取消。</span>
      </div>

      <!-- 菜单权限树：每个顶级菜单一张卡片 -->
      <div class="perm-branches">
        <div v-for="top in topNodes" :key="top.title" class="perm-branch card-panel">
          <div class="perm-branch__header">
            <el-checkbox
              :model-value="isNodeAllChecked(top)"
              :indeterminate="isNodeIndeterminate(top)"
              :disabled="isSuperAdmin"
              @change="toggleNode(top)"
            >
              <strong>{{ top.title }}</strong>
            </el-checkbox>
            <span class="perm-branch__count">{{ checkedCount(top) }} / {{ subtreeKeys(top).length }}</span>
          </div>

          <template v-if="isNodeExpanded(top)">
            <div class="perm-branch__body">
              <template v-for="row in (rowsByTop.get(top) || [])" :key="row.rowKey">
                <!-- 子菜单行 -->
                <div v-if="row.kind === 'menu'" class="menu-row" :style="{ paddingLeft: indentOf(row.depth) }">
                  <el-checkbox
                    :model-value="isNodeAllChecked(row.node!)"
                    :indeterminate="isNodeIndeterminate(row.node!)"
                    :disabled="isSuperAdmin"
                    @change="toggleNode(row.node!)"
                  >
                    {{ row.node?.title }}
                  </el-checkbox>
                  <span v-if="visibleLeaves(row.node?.keys).length" class="menu-row__keys">{{ visibleLeaves(row.node?.keys).join('  ') }}</span>
                </div>
                <!-- 小节标题 -->
                <div v-else-if="row.kind === 'section'" class="section-row" :style="{ paddingLeft: indentOf(row.depth) }">
                  {{ row.label }}
                </div>
                <!-- 叶子权限网格 -->
                <div v-else class="leaf-grid" :style="{ marginLeft: indentOf(row.depth) }">
                  <div
                    v-for="item in row.items"
                    :key="item.key"
                    class="perm-item"
                    :class="{
                      'perm-item--checked': isItemChecked(item.key),
                      'perm-item--linked': item.linkGroup,
                    }"
                    :title="item.linkGroup ? `联动项：${item.linkGroup.join('、')}` : undefined"
                    @click="toggleLeaf(item.key)"
                  >
                    <el-checkbox :model-value="isItemChecked(item.key)" :disabled="isSuperAdmin" size="small" />
                    <span class="perm-item__name">{{ item.label }}</span>
                    <span class="perm-item__key">{{ item.key }}</span>
                  </div>
                </div>
              </template>
            </div>
          </template>
          <div v-else class="perm-branch__empty">勾选该菜单后展开其页面操作与 API 接口</div>
        </div>
      </div>

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

.super-admin-hint,
.perm-tip {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-base;
  font-size: 13px;
  color: $text-secondary;
  margin-bottom: $spacing-base;
}

.super-admin-hint {
  background: #fff3e0;
}

.perm-tip {
  background: $bg-white;
  border: 1px solid $border-lighter;
}

.perm-branches {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.perm-branch {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-lighter;
  }

  &__count {
    font-size: 12px;
    color: $text-placeholder;
    font-family: monospace;
  }

  &__body {
    display: flex;
    flex-direction: column;
    padding-top: $spacing-sm;
  }

  &__empty {
    padding: $spacing-sm 0 2px;
    font-size: 12px;
    color: $text-placeholder;
  }
}

.menu-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 4px 0;

  &__keys {
    font-size: 11px;
    color: $text-placeholder;
    font-family: monospace;
  }
}

.section-row {
  margin-top: $spacing-xs;
  padding: 2px 0;
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
}

.leaf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 2px;
  margin-top: 2px;
  margin-bottom: $spacing-xs;
}

.perm-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 4px $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  transition: background 0.15s;

  &:hover { background: $bg-hover; }
  &--checked { background: $bg-active; }

  // 联动项：页面操作与对应 API 整体开关，左侧细条标识
  &--linked {
    border-left: 2px solid var(--el-color-primary-light-5);
  }

  &__name {
    font-size: 13px;
    color: $text-primary;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__key {
    font-size: 11px;
    color: $text-placeholder;
    font-family: monospace;
  }
}

.perm-actions-fixed {
  position: sticky;
  bottom: 0;
  z-index: 100;
  background: $bg-white;
  border-top: 1px solid $border-base;
  padding: $spacing-sm 0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);

  &__inner {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
    padding: 0 $spacing-lg;
  }
}
</style>
