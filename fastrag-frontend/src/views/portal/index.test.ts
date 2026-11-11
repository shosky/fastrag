// @vitest-environment jsdom
// 门户首页（portal TDD T2.6-T2.18，2026-11-03 Yuxi 式重构）：分区顺序与唯一性、hero 四层文案、
// 双支柱子能力行与 pill、四步卡、技术栈标签、资源区防回归（T2.7/T2.8）与非交互断言（T2.14）。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import PortalHome from './index.vue'
import { useUserStore } from '@/stores/user'

const { changelogMock } = vi.hoisted(() => ({
  changelogMock: [] as { version: string; date: string; title: string; type: string; items: string[] }[],
}))

vi.mock('@/constants/changelog', () => ({ CHANGELOG: changelogMock }))

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/portal', component: PortalHome },
      { path: '/login', component: { template: '<div />' } },
      { path: '/home', component: { template: '<div />' } },
      { path: '/portal/manual', component: { template: '<div />' } },
      { path: '/portal/changelog', component: { template: '<div />' } },
    ],
  })
}

async function mountHome(opts: { loggedIn?: boolean; changelog?: 'default' | 'empty' } = {}) {
  if (opts.changelog !== 'empty') {
    changelogMock.splice(0, changelogMock.length, {
      version: 'v1.4.0',
      date: '2026-10-20',
      title: '测试版本',
      type: '新功能',
      items: ['要点一'],
    })
  } else {
    changelogMock.splice(0, changelogMock.length)
  }
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore(pinia)
  if (opts.loggedIn) {
    store.setToken('test-token')
    store.setUserInfo({ username: 'alice', realName: '张三' } as any)
  }
  const router = buildRouter()
  router.push('/portal')
  await router.isReady()
  const wrapper = mount(PortalHome, { global: { plugins: [router, pinia, ElementPlus] } })
  return { wrapper, router }
}

/** 点击并等待异步路由导航完成 */
async function clickNav(wrapper: ReturnType<typeof mount>, selector: string) {
  await wrapper.find(selector).trigger('click')
  await flushPromises()
}

/** 分区眉标 → 标题的期望顺序（T2.7，对齐 spec「元素与交互」分区清单） */
const SECTION_SEQUENCE: [string, string][] = [
  ['知识引擎', '从文档到可问答的知识资产'],
  ['应用与智能体', '不止于对话，更能编排与执行'],
  ['快速开始', '四步构建企业知识问答'],
  ['我们的优势', '为什么选 FastRAG'],
  ['业务场景', '覆盖核心业务场景'],
  ['技术栈', '现代而稳健的私有化交付基座'],
  ['资源', '上手与更新'],
  ['常见问题', '关于 FastRAG 的常见解答'],
]

beforeEach(() => {
  vi.clearAllMocks()
  // jsdom localStorage 跨用例持久：已登录用例 setToken 后会泄漏到后续「游客」挂载
  //（store 构造时从 storage 恢复 token），这里强制清零保证登录态隔离
  localStorage.clear()
})

describe('hero 与按钮（T2.6 / T2.11 / T2.18）', () => {
  it('hero 四层文案齐备：徽章、渐变大标题、副标题、描述', async () => {
    const { wrapper } = await mountHome()
    expect(wrapper.find('.hero__badge').text()).toBe('私有化部署 · 开箱即用')
    const title = wrapper.find('.hero__title')
    expect(title.text()).toContain('让企业知识')
    expect(title.find('.grad').text()).toBe('一问即答')
    expect(wrapper.find('.hero__sub').text()).toBe('企业级检索增强生成（RAG）平台')
    expect(wrapper.find('.hero__desc').exists()).toBe(true)
  })

  it('hero 主按钮按登录态切换：游客「立即开始」→ /login；次按钮 → /portal/manual', async () => {
    const { wrapper, router } = await mountHome()
    const actions = wrapper.findAll('.hero__actions .btn')
    expect(actions[0].text()).toBe('立即开始')
    await actions[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/login')
    await wrapper.find('.hero__actions .btn:nth-child(2)').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/portal/manual')
  })

  it('已登录 hero 主按钮「进入平台」→ /home', async () => {
    const { wrapper, router } = await mountHome({ loggedIn: true })
    const primary = wrapper.findAll('.hero__actions .btn')[0]
    expect(primary.text()).toBe('进入平台')
    await primary.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/home')
  })

  it('CTA 主按钮与 hero 同登录态规则（游客 → /login）', async () => {
    const { wrapper, router } = await mountHome()
    await clickNav(wrapper, '.cta .btn')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.find('.cta').text()).toContain('四步')
  })

  it('CTA 主按钮已登录 → /home', async () => {
    const { wrapper, router } = await mountHome({ loggedIn: true })
    expect(wrapper.find('.cta .btn').text()).toBe('进入平台')
    await clickNav(wrapper, '.cta .btn')
    expect(router.currentRoute.value.path).toBe('/home')
  })
})

describe('分区结构与唯一性（T2.7，重复分区缺陷防回归）', () => {
  it('按 DOM 顺序渲染八个内容分区，且不存在旧的「核心能力」分区', async () => {
    const { wrapper } = await mountHome()
    const heads = wrapper.findAll('.section-head')
    const actual = heads.map((h) => [
      h.find('.section-head__eyebrow').text(),
      h.find('.section-head__title').text(),
    ])
    expect(actual).toEqual(SECTION_SEQUENCE)
    expect(wrapper.text()).not.toContain('生产级 RAG，开箱即用')
  })
})

describe('双支柱子能力行（T2.12 / T2.13 / T2.14）', () => {
  it('知识引擎区渲染 6 条子能力行，「知识图谱」行配实拍锚点 scene-graph.png', async () => {
    const { wrapper } = await mountHome()
    const rows = wrapper.findAll('.kb-row')
    expect(rows).toHaveLength(6)
    const titles = rows.map((r) => r.find('.pillar-row__title').text())
    expect(titles).toContain('多格式解析与 AI 分片')
    expect(titles).toContain('父子分片与引用溯源')
    expect(titles).toContain('QA 对提炼')
    expect(titles).toContain('知识图谱')
    expect(titles).toContain('混合检索与重排')
    expect(titles).toContain('检索评估与运营分析')
    const anchor = wrapper.find('.kb-row img')
    expect(anchor.attributes('src')).toBe('/portal/scene-graph.png')
  })

  it('应用与智能体区渲染 5 条子能力行，每行挂 1-2 个静态 pill，「应用与工作流编排」行配锚点 scene-application.png', async () => {
    const { wrapper } = await mountHome()
    const rows = wrapper.findAll('.agent-row')
    expect(rows).toHaveLength(5)
    for (const row of rows) {
      const pills = row.findAll('.pill')
      expect(pills.length).toBeGreaterThanOrEqual(1)
      expect(pills.length).toBeLessThanOrEqual(2)
    }
    expect(rows[0].find('.pillar-row__title').text()).toBe('应用与工作流编排')
    expect(rows[0].find('img').attributes('src')).toBe('/portal/scene-application.png')
  })

  it('双支柱行、pill、技术栈标签为非交互：点击不跳转、非可聚焦元素', async () => {
    const { wrapper, router } = await mountHome()
    const targets = [
      ...wrapper.findAll('.kb-row'),
      ...wrapper.findAll('.agent-row'),
      ...wrapper.findAll('.pill'),
      ...wrapper.findAll('.tech-pill'),
    ]
    expect(targets.length).toBeGreaterThan(0)
    for (const t of targets) {
      const tag = (t.element as HTMLElement).tagName
      expect(['A', 'BUTTON']).not.toContain(tag)
      expect(t.element.hasAttribute('tabindex')).toBe(false)
    }
    await wrapper.find('.kb-row').trigger('click')
    await wrapper.find('.agent-row').trigger('click')
    await wrapper.find('.tech-pill').trigger('click')
    expect(router.currentRoute.value.path).toBe('/portal')
  })
})

describe('工作原理与技术栈（T2.15 / T2.16）', () => {
  it('四步卡按序渲染：01 上传解析 → 02 知识加工 → 03 创建应用 → 04 对话溯源', async () => {
    const { wrapper } = await mountHome()
    const steps = wrapper.findAll('.step')
    expect(steps.map((s) => s.find('.step__num').text())).toEqual(['01', '02', '03', '04'])
    expect(steps.map((s) => s.find('.step__title').text())).toEqual([
      '上传解析',
      '知识加工',
      '创建应用',
      '对话溯源',
    ])
  })

  it('技术栈区渲染六枚静态标签', async () => {
    const { wrapper } = await mountHome()
    const pills = wrapper.findAll('.tech-pill')
    expect(pills.map((p) => p.text())).toEqual(['MySQL', 'Redis', 'RabbitMQ', 'Neo4j', 'Milvus', 'MinIO'])
  })
})

describe('业务场景与资源卡（T2.17 / T2.8，resources 缺陷防回归）', () => {
  it('业务场景卡 ×4 整卡点击 → /home', async () => {
    const { wrapper, router } = await mountHome()
    const cards = wrapper.findAll('.scenario-card')
    expect(cards).toHaveLength(4)
    await cards[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/home')
  })

  it('资源分区正常渲染两张资源卡，徽标 = changelog 首条版本；点击跳对应门户页', async () => {
    const { wrapper, router } = await mountHome()
    const cards = wrapper.findAll('.resource-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('操作手册')
    expect(cards[1].text()).toContain('产品动态')
    expect(cards[1].text()).toContain('v1.4.0')
    await cards[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/portal/manual')
    await cards[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/portal/changelog')
  })

  it('changelog 为空时资源卡徽标不渲染（STATE-002 派生）', async () => {
    const { wrapper } = await mountHome({ changelog: 'empty' })
    expect(wrapper.find('.resource-card .el-tag').exists()).toBe(false)
  })
})
