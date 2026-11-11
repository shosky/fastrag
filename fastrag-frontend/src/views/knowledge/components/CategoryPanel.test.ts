// @vitest-environment jsdom
// 修订 #18：知识库分类面板（ADP 式）——树渲染/计数口径/搜索过滤/选择 emit/折叠/权限操作/CRUD。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import ElementPlus, { ElMessageBox } from 'element-plus'

const { getKbCategories, createKbCategory, updateKbCategory, deleteKbCategory, getOrgTree } = vi.hoisted(() => ({
  getKbCategories: vi.fn(),
  createKbCategory: vi.fn(),
  updateKbCategory: vi.fn(),
  deleteKbCategory: vi.fn(),
  getOrgTree: vi.fn(),
}))

vi.mock('@/api', () => ({ getKbCategories, createKbCategory, updateKbCategory, deleteKbCategory, getOrgTree }))

import CategoryPanel from './CategoryPanel.vue'

const KBS = [
  { id: 'kb-1', category: '产品' },
  { id: 'kb-2', category: '产品' },
  { id: 'kb-3', category: '技术' },
  { id: 'kb-4' },
]
const CATS = [
  { id: 'c1', name: '产品', color: '#1890ff' },
  { id: 'c2', name: '技术' },
]

const ADMIN = { username: 'admin', realName: '王翔', roles: ['super_admin'], permissions: ['*'] }
const VIEWER = { username: 'viewer', realName: '', roles: ['kb_user'], permissions: [] }

function mountPanel(modelValue = '', kbs: any[] = KBS, userInfo: any = ADMIN) {
  const pinia = createPinia()
  const store = useUserStore(pinia)
  store.setUserInfo(userInfo as any)
  const wrapper = mount(CategoryPanel, {
    props: { modelValue, knowledgeBases: kbs },
    global: { plugins: [pinia, ElementPlus] },
  })
  return { wrapper }
}

let confirmSpy: any
beforeEach(() => {
  vi.clearAllMocks()
  confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(true)
  getKbCategories.mockResolvedValue(CATS)
  getOrgTree.mockResolvedValue([{ id: 'org-1', name: '研发部', children: [] }])
  localStorage.clear()
})

describe('分类树渲染与计数口径（T-面板 1）', () => {
  it('全部/未分类/各分类 + 前端计数', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    const items = wrapper.findAll('.category-item').map((n) => n.text())
    expect(items).toHaveLength(4)
    expect(items[0]).toContain('全部')
    expect(items[0]).toContain('4')
    expect(items[1]).toContain('未分类')
    expect(items[1]).toContain('1')
    expect(items[2]).toContain('产品')
    expect(items[2]).toContain('2')
    expect(items[3]).toContain('技术')
    expect(items[3]).toContain('1')
  })

  it('选中态：modelValue=c1 时「产品」行高亮', async () => {
    const { wrapper } = mountPanel('c1')
    await flushPromises()
    const active = wrapper.findAll('.category-item.active')
    expect(active).toHaveLength(1)
    expect(active[0].text()).toContain('产品')
  })

  it('搜索只过滤分类列表（全部/未分类恒显）', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.categorySearch = '产'
    await flushPromises()
    const names = wrapper.findAll('.category-item').map((n) => n.text())
    expect(names).toHaveLength(3)
    expect(names.some((n) => n.includes('产品'))).toBe(true)
    expect(names.some((n) => n.includes('技术'))).toBe(false)
  })
})

describe('选择交互（T-面板 2）', () => {
  it('点击分类 emit update:modelValue', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    await wrapper.findAll('.category-item')[2].trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]).toEqual(['c1'])
  })

  it('点击已选中的分类 → 取消选择（emit 空串）', async () => {
    const { wrapper } = mountPanel('c1')
    await flushPromises()
    await wrapper.findAll('.category-item')[2].trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([''])
  })

  it('点击未分类 emit __uncategorized__', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    await wrapper.findAll('.category-item')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]).toEqual(['__uncategorized__'])
  })
})

describe('折叠（T-面板 3）', () => {
  it('折叠 → 窄条模式；展开按钮恢复', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    // panel-actions 顺序：[新增(受控 tooltip), 收起(Fold)] → 点最后一个
    const icons = wrapper.findAll('.panel-actions .action-icon')
    await icons[icons.length - 1].trigger('click')
    await flushPromises()
    expect(wrapper.find('.category-panel--collapsed').exists()).toBe(true)
    expect(wrapper.find('.category-list').exists()).toBe(false)

    await wrapper.find('.collapse-btn').trigger('click')
    await flushPromises()
    expect(wrapper.find('.category-list').exists()).toBe(true)
  })
})

describe('分类管理（修订 #18 迁入）', () => {
  it('超管每个分类行有「…」操作；无权限账号不可见（修订 #18）', async () => {
    const admin = mountPanel()
    await flushPromises()
    expect(admin.wrapper.findAll('.op-icon')).toHaveLength(2) // 两个分类行各一个「…」

    const viewer = mountPanel('', KBS, VIEWER)
    await flushPromises()
    expect(viewer.wrapper.findAll('.op-icon')).toHaveLength(0)
  })

  it('点击「…」弹出操作菜单（编辑/删除），点编辑打开对话框', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()
    await wrapper.findAll('.op-icon')[0].trigger('click')
    await flushPromises()
    const menuText = Array.from(document.body.querySelectorAll('.el-dropdown-menu__item')).map((el) => el.textContent?.trim())
    expect(menuText).toContain('编辑')
    expect(menuText).toContain('删除')

    // 菜单→编辑的命令分流由 handleRowCommand 承接；对话框打开用测试缝直驱（不测 EP dropdown 内部）
    const vm = wrapper.vm as any
    vm.handleRowCommand('edit', { id: 'c1', name: '产品' })
    await flushPromises()
    expect(vm.showDialog).toBe(true)
    expect(vm.dialogTitle).toBe('编辑分类')
    expect(vm.formData.id).toBe('c1')
  })

  it('新增：打开对话框 → 保存调用 createKbCategory 并 emit changed', async () => {
    createKbCategory.mockResolvedValue({})
    const { wrapper } = mountPanel()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleAdd()
    vm.formData.name = '新分类'
    vm.formData.orgId = 'org-1'
    await vm.handleSave()
    await flushPromises()
    expect(createKbCategory).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('changed')).toBeTruthy()
  })

  it('删除：确认后调用 deleteKbCategory；删除当前选中分类时 emit 归零', async () => {
    deleteKbCategory.mockResolvedValue({})
    confirmSpy.mockResolvedValueOnce(true)
    const { wrapper } = mountPanel('c1')
    await flushPromises()
    const vm = wrapper.vm as any
    await vm.handleDelete({ id: 'c1', name: '产品' })
    await flushPromises()
    expect(deleteKbCategory).toHaveBeenCalledWith('c1')
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual([''])
  })
})
