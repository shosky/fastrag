<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { CHANGELOG } from '@/constants/changelog'

const router = useRouter()
const userStore = useUserStore()

// 登录即进入平台：游客显示「立即开始」，已登录显示「进入平台」
const isLoggedIn = computed(() => userStore.isLoggedIn)
const heroPrimaryLabel = computed(() => (isLoggedIn.value ? '进入平台' : '立即开始'))
const heroPrimaryTarget = computed(() => (isLoggedIn.value ? '/home' : '/login'))

// 知识引擎：六项知识生产能力（CONTEXT.md 门户首页重构快照决策 6），「知识图谱」行配实拍锚点；
// 行为纯展示不可点击（决策 16）
const kbRows: { title: string; desc: string; img?: string }[] = [
  {
    title: '多格式解析与 AI 分片',
    desc: 'PDF、Word、Markdown、音频、图片多策略解析，AI 分片处理复杂版式，结构级跨页合并不被页边界割裂',
  },
  {
    title: '父子分片与引用溯源',
    desc: '子分片负责精准召回、父分片承载完整上下文，回答附引用来源与相似度，可核验、可审计',
  },
  {
    title: 'QA 对提炼',
    desc: 'LLM 从分片自动提炼完整问答对，人工确认后进入检索通道，高频问题即刻成册',
  },
  {
    title: '知识图谱',
    img: '/portal/scene-graph.png',
    desc: '从分片自动抽取实体与关系，实体向量相似度合并同名实体，力导向图可视化与实体搜索',
  },
  {
    title: '混合检索与重排',
    desc: '向量 + 关键词混合召回再统一重排，检索调试上线前即可验证问答质量',
  },
  {
    title: '检索评估与运营分析',
    desc: '基准测试与评测衡量检索质量，知识资产盘点、热门排行与引用率统计全程可分析',
  },
]

// 应用与智能体：五项应用生产能力（快照决策 6），每行 1-2 个静态 pill，「应用与工作流编排」行配实拍锚点
const agentRows: { title: string; pills: string[]; desc: string; img?: string }[] = [
  {
    title: '应用与工作流编排',
    pills: ['可视化节点', '零代码'],
    img: '/portal/scene-application.png',
    desc: '零代码创建对话应用，可视化节点编排 LLM、检索、重排、条件、代码等流程，配置即时预览',
  },
  {
    title: '智能体',
    pills: ['自主规划', '工具调用'],
    desc: 'Backend + Graph + Middleware + State 架构驱动，自主规划步骤并调用工具完成复杂任务',
  },
  {
    title: '工具与技能生态',
    pills: ['内置 / API / MCP / 技能'],
    desc: '四类工具能力统一挂载：内置工具开箱即用，API 与 MCP 服务即接即用，技能包可导入分享',
  },
  {
    title: '沙箱隔离执行',
    pills: ['多后端隔离'],
    desc: '对话内安全执行代码与命令，docker / cube / e2b 多后端抽象，容器加固与路径钳制保障安全',
  },
  {
    title: 'Widget 对话卡片',
    pills: ['19 组件'],
    desc: '智能体输出结构化交互卡片，用户在卡片内确认与回传，结构化数据直接驱动后续流程',
  },
]

// 工作原理四步（快照决策 9）：还原真实产品路径，含知识加工环节
const steps = [
  { num: '01', title: '上传解析', desc: '把 PDF、Word、Markdown 等文档拖入知识库，系统自动完成解析与结构化' },
  { num: '02', title: '知识加工', desc: '自动分片、QA 对提炼与知识图谱构建，文档变成可推理的知识资产' },
  { num: '03', title: '创建应用', desc: '零代码创建对话应用，绑定知识库与模型，需要时可视化编排工作流' },
  { num: '04', title: '对话溯源', desc: '用自然语言提问，获得检索增强回答，每条回答附引用来源可核验' },
]

// 业务场景：示例取自平台内真实应用
const scenarios = [
  { title: '智能客服', img: '/portal/crop-chatcolumn.jpg', desc: '客服制度、产品 FAQ 统一入库，回答口径一致、可溯源，新员工即学即用', example: '高速客服' },
  { title: '企业知识问答', img: '/portal/crop-kb.png', desc: '制度、规范、手册随问随答，文档不再躺在网盘里', example: '知识库对话' },
  { title: '数据问答', img: '/portal/crop-analytics.png', desc: '自然语言检索业务知识，检索日志与命中率全程可分析', example: '检索日志分析' },
  { title: '知识运营', img: '/portal/scene-application.png', desc: '多应用统一管理，按业务线组织知识资产', example: '应用中心' },
]

// 技术栈：私有化交付基座（快照决策 10），静态文字标签、无 icon 依赖
const techStack = ['MySQL', 'Redis', 'RabbitMQ', 'Neo4j', 'Milvus', 'MinIO']

// 资源区（快照决策 26：修复 resources 未定义缺陷）
const resources = [
  { title: '操作手册', desc: '从上传文档到应用对话的完整使用说明，按章节导航', path: '/portal/manual' },
  { title: '产品动态', desc: '按版本查看功能更新记录', path: '/portal/changelog' },
]

// 为什么选 FastRAG：四个真实卖点
const whys = [
  { icon: 'Box', title: '开箱即用', desc: '无需训练模型，上传文档解析即可获得问答服务，当天上线' },
  { icon: 'Lock', title: '私有化部署', desc: '完整支持内网私有化交付，企业数据不出域' },
  { icon: 'View', title: '回答可溯源', desc: '每条回答附带引用来源与相似度，可核验、可审计' },
  { icon: 'Shield', title: '企业级安全', desc: '组织级 RBAC 与知识库 ACL 双层权限，操作审计全程留痕' },
]

// 常见问题：全部为可诚实回答的产品事实
const faqs = [
  {
    q: '需要训练模型吗？',
    a: '不需要。FastRAG 采用检索增强生成（RAG）路线：上传文档后自动解析、分片、向量化，对话时实时检索相关知识交给大模型作答。文档更新后重新解析即可，无需任何训练过程。',
  },
  {
    q: '数据安全如何保障？',
    a: '支持全内网私有化部署，企业数据不出域。平台提供组织级 RBAC、知识库级 ACL（Owner/Editor/Viewer）双层权限控制，敏感词拦截与操作审计全程留痕。',
  },
  {
    q: '支持哪些文档格式？',
    a: '支持 PDF、Word、Markdown、TXT 等主流格式；解析策略可按文档类型定制（分片长度、重叠、分隔符等），并提供 AI 分片能力处理复杂版式文档。',
  },
  {
    q: '回答可以溯源吗？',
    a: '可以。每条回答附带引用的知识来源（文档名与分片定位），用户可核验答案依据；知识库侧还支持检索调试，上线前即可验证问答质量。',
  },
  {
    q: '如何开始使用？',
    a: '点击「立即开始」注册登录，按四步操作：上传文档到知识库 → 知识自动加工 → 创建应用并绑定知识库 → 提问对话。遇到问题可随时查阅操作手册。',
  },
]

const latestVersion = CHANGELOG[0]?.version

// 滚动渐显（动效基线四项之一，threshold 0.15）
let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  const els = document.querySelectorAll('.portal-home .reveal')
  if (!('IntersectionObserver' in window)) {
    els.forEach((el) => el.classList.add('is-visible'))
    return
  }
  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.15 }
  )
  els.forEach((el) => revealObserver?.observe(el))
})

onBeforeUnmount(() => revealObserver?.disconnect())
</script>

<template>
  <div class="portal-home">
    <!-- ===== hero：四层文案 + 大尺寸真实产品截图 ===== -->
    <section class="hero">
      <div class="container hero__inner">
        <div class="hero__copy">
          <span class="hero__badge">私有化部署 · 开箱即用</span>
          <h1 class="hero__title">让企业知识 <span class="grad">一问即答</span></h1>
          <p class="hero__sub">企业级检索增强生成（RAG）平台</p>
          <p class="hero__desc">
            FastRAG 把散落在文档里的知识，变成可检索、可对话、可溯源的
            AI 问答服务——无需训练模型，上传文档即可使用。
          </p>
          <div class="hero__actions">
            <el-button type="primary" size="large" class="btn" @click="router.push(heroPrimaryTarget)">
              {{ heroPrimaryLabel }}
            </el-button>
            <el-button size="large" class="btn" @click="router.push('/portal/manual')">
              查看操作手册
            </el-button>
          </div>
        </div>

        <div class="hero__mock" aria-hidden="true">
          <div class="browser">
            <div class="browser__bar">
              <i></i><i></i><i></i>
              <span class="browser__title">企业知识助手 · FastRAG</span>
            </div>
            <img src="/portal/crop-chatcolumn.jpg" alt="知识问答界面" class="browser__img" />
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 知识引擎（双支柱一）：六条子能力行，纯展示 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">知识引擎</span>
          <h2 class="section-head__title">从文档到可问答的知识资产</h2>
          <p class="section-head__sub">解析、分片、提炼、图谱、检索、评估——知识生产的完整链路</p>
        </div>
        <div class="pillar">
          <div v-for="row in kbRows" :key="row.title" class="pillar-row kb-row reveal">
            <div class="pillar-row__body">
              <h3 class="pillar-row__title">{{ row.title }}</h3>
              <p class="pillar-row__desc">{{ row.desc }}</p>
            </div>
            <div v-if="row.img" class="pillar-row__imgwrap">
              <img :src="row.img" :alt="row.title + '界面'" class="pillar-row__img" loading="lazy" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 应用与智能体（双支柱二）：五条子能力行 × 静态 pill ===== -->
    <section class="section section--muted">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">应用与智能体</span>
          <h2 class="section-head__title">不止于对话，更能编排与执行</h2>
          <p class="section-head__sub">应用、智能体、工具、沙箱与对话卡片，一套平台全部承载</p>
        </div>
        <div class="pillar">
          <div v-for="row in agentRows" :key="row.title" class="pillar-row agent-row reveal">
            <div class="pillar-row__body">
              <h3 class="pillar-row__title">{{ row.title }}</h3>
              <p class="pillar-row__desc">{{ row.desc }}</p>
              <div class="pillar-row__pills">
                <span v-for="pill in row.pills" :key="pill" class="pill">{{ pill }}</span>
              </div>
            </div>
            <div v-if="row.img" class="pillar-row__imgwrap">
              <img :src="row.img" :alt="row.title + '界面'" class="pillar-row__img" loading="lazy" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 工作原理：四步纯编号卡 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">快速开始</span>
          <h2 class="section-head__title">四步构建企业知识问答</h2>
          <p class="section-head__sub">无需训练模型，上传文档即可获得可对话的企业知识</p>
        </div>
        <div class="steps">
          <div v-for="step in steps" :key="step.num" class="step reveal">
            <span class="step__num">{{ step.num }}</span>
            <h3 class="step__title">{{ step.title }}</h3>
            <p class="step__desc">{{ step.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 为什么选 FastRAG ===== -->
    <section class="section section--muted">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">我们的优势</span>
          <h2 class="section-head__title">为什么选 FastRAG</h2>
          <p class="section-head__sub">四个理由，让企业知识真正用起来</p>
        </div>
        <div class="why-grid">
          <div v-for="why in whys" :key="why.title" class="why-card reveal">
            <el-icon :size="28" class="why-card__icon">
              <component :is="why.icon" />
            </el-icon>
            <h3 class="why-card__title">{{ why.title }}</h3>
            <p class="why-card__desc">{{ why.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 业务场景 ===== -->
    <section class="section section--muted">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">业务场景</span>
          <h2 class="section-head__title">覆盖核心业务场景</h2>
          <p class="section-head__sub">同一套知识引擎，服务多种业务形态——平台内的真实应用就在你身边</p>
        </div>
        <div class="scenario-grid">
          <div v-for="scene in scenarios" :key="scene.title" class="scenario-card reveal" @click="router.push('/home')">
            <div class="scenario-card__imgwrap">
              <img :src="scene.img" :alt="scene.title + '界面截图'" class="scenario-card__img" loading="lazy" />
            </div>
            <div class="scenario-card__body">
              <h3 class="scenario-card__title">{{ scene.title }}</h3>
              <p class="scenario-card__desc">{{ scene.desc }}</p>
              <span class="scenario-card__example">平台示例：{{ scene.example }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 技术栈：六枚静态文字标签 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">技术栈</span>
          <h2 class="section-head__title">现代而稳健的私有化交付基座</h2>
          <p class="section-head__sub">全组件开源栈，内网私有化交付开箱即用</p>
        </div>
        <div class="tech-stack reveal">
          <span v-for="tech in techStack" :key="tech" class="tech-pill">{{ tech }}</span>
        </div>
      </div>
    </section>

    <!-- ===== 资源 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">资源</span>
          <h2 class="section-head__title">上手与更新</h2>
        </div>
        <div class="resource-grid">
          <div v-for="res in resources" :key="res.title" class="resource-card reveal" @click="router.push(res.path)">
            <div class="resource-card__body">
              <div class="resource-card__title">
                {{ res.title }}
                <el-tag v-if="res.title === '产品动态' && latestVersion" size="small" effect="plain">
                  {{ latestVersion }}
                </el-tag>
              </div>
              <div class="resource-card__desc">{{ res.desc }}</div>
            </div>
            <el-icon class="resource-card__arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 常见问题 ===== -->
    <section class="section section--muted">
      <div class="container container--narrow">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">常见问题</span>
          <h2 class="section-head__title">关于 FastRAG 的常见解答</h2>
        </div>
        <el-collapse class="faq reveal">
          <el-collapse-item v-for="faq in faqs" :key="faq.q" :name="faq.q">
            <template #title>
              <span class="faq__q">{{ faq.q }}</span>
            </template>
            <p class="faq__a">{{ faq.a }}</p>
          </el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <!-- ===== CTA 深色横幅 ===== -->
    <section class="section section--flush">
      <div class="container">
        <div class="cta reveal">
          <div class="cta__dots" aria-hidden="true"></div>
          <div class="cta__copy">
            <h2>让企业知识，开始对话</h2>
            <p>上传文档，四步获得可溯源的 AI 问答服务</p>
            <el-button type="primary" size="large" class="btn" @click="router.push(heroPrimaryTarget)">
              {{ heroPrimaryLabel }}
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 页脚 ===== -->
    <footer class="footer">
      <div class="container footer__inner">
        <div class="footer__brand">
          <span class="footer__logo">FR</span>
          <span>FastRAG</span>
        </div>
        <nav class="footer__links">
          <a @click.prevent="router.push('/portal/manual')" href="#manual">操作手册</a>
          <a @click.prevent="router.push('/portal/changelog')" href="#changelog">产品动态</a>
          <a v-if="!isLoggedIn" @click.prevent="router.push('/login')" href="#login">登录</a>
          <a v-else @click.prevent="router.push('/home')" href="#home">进入平台</a>
        </nav>
        <span class="footer__copy">© 2026 FastRAG</span>
      </div>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.portal-home {
  flex: 1;
  background: #fff;
  color: #18181b;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 $spacing-xl;
}

.container--narrow {
  max-width: 860px;
}

.btn {
  border-radius: 8px;
}

// ===== hero =====
.hero {
  background: #fff;
  border-bottom: 1px solid #f0f0f1;
  overflow: hidden;
}

.hero__inner {
  display: flex;
  align-items: center;
  gap: 72px;
  padding-top: 100px;
  padding-bottom: 100px;
}

.hero__copy {
  flex: 1;
  min-width: 0;
}

.hero__badge {
  display: inline-block;
  padding: 5px 14px;
  border: 1px solid $border-base;
  border-radius: $radius-round;
  font-size: 13px;
  letter-spacing: 1px;
  color: $text-secondary;
  margin-bottom: 24px;
}

.hero__title {
  margin: 0 0 16px;
  font-size: 64px;
  line-height: 1.14;
  font-weight: 700;
  letter-spacing: -1.5px;
  color: #09090b;
}

// 「一问即答」蓝青渐变文字（模块文档视觉节）
.grad {
  background: linear-gradient(92deg, $color-primary-dark 0%, $color-primary 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero__sub {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: #18181b;
}

.hero__desc {
  margin: 0 0 36px;
  max-width: 500px;
  font-size: 16px;
  line-height: 1.9;
  color: #52525b;
}

.hero__actions {
  display: flex;
  gap: $spacing-base;
  flex-wrap: wrap;
}

// 入场动效：徽章 → 标题 → 副标题 → 描述 → 按钮 依次浮现（动效基线）
.hero__badge,
.hero__title,
.hero__sub,
.hero__desc,
.hero__actions {
  opacity: 0;
  animation: rise-in 0.9s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

.hero__badge { animation-delay: 0.05s; }
.hero__title { animation-delay: 0.15s; }
.hero__sub { animation-delay: 0.25s; }
.hero__desc { animation-delay: 0.35s; }
.hero__actions { animation-delay: 0.45s; }

@keyframes rise-in {
  from { opacity: 0; transform: translateY(26px); }
  to { opacity: 1; transform: translateY(0); }
}

.hero__mock {
  flex-shrink: 0;
  width: 580px;
}

.browser {
  border: 1px solid #e4e4e7;
  border-radius: 14px;
  overflow: hidden;
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.04),
    0 24px 64px -16px rgba(9, 9, 11, 0.18);
  background: #fff;
}

.browser__bar {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 16px;
  border-bottom: 1px solid #f0f0f1;
  background: #fafafa;

  i {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #e4e4e7;

    &:nth-child(1) { background: #fca5a5; }
    &:nth-child(2) { background: #fcd34d; }
    &:nth-child(3) { background: #86efac; }
  }
}

.browser__title {
  margin-left: 6px;
  font-size: 12px;
  color: $text-placeholder;
}

.browser__img {
  display: block;
  width: 100%;
}

// ===== 通用分区 =====
.section {
  padding: 110px 0;
}

.section--muted {
  background: #fafafa;
  border-top: 1px solid #f0f0f1;
  border-bottom: 1px solid #f0f0f1;
}

.section--flush {
  padding-top: 100px;
  padding-bottom: 100px;
}

.section-head {
  text-align: center;
  margin-bottom: 64px;
}

.section-head__eyebrow {
  display: inline-block;
  font-size: 14px;
  letter-spacing: 5px;
  color: #a1a1aa;
  margin-bottom: 14px;
}

.section-head__title {
  margin: 0 0 14px;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #09090b;
}

.section-head__sub {
  margin: 0;
  font-size: 16px;
  color: #52525b;
}

// ===== 双支柱子能力行（Yuxi 式轻列表）：纯展示、hover 仅底色微亮 =====
.pillar-row {
  display: flex;
  align-items: center;
  gap: 48px;
  padding: 26px 32px;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #fff;
  margin-bottom: 20px;
  transition: background 0.2s;

  &:hover {
    background: #fafafa;
  }
}

.section--muted .pillar-row {
  border-color: #e4e4e7;
}

.pillar-row__body {
  flex: 1;
  min-width: 0;
}

.pillar-row__title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #09090b;
}

.pillar-row__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
}

.pillar-row__pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.pill {
  padding: 3px 12px;
  border: 1px solid #e4e4e7;
  border-radius: $radius-round;
  font-size: 12px;
  color: #52525b;
  background: #fff;
  white-space: nowrap;
}

.pillar-row__imgwrap {
  flex-shrink: 0;
  width: 380px;
  border: 1px solid #f0f0f1;
  border-radius: 10px;
  overflow: hidden;
}

.pillar-row__img {
  display: block;
  width: 100%;
}

// ===== 业务场景 =====
.scenario-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px;
}

.scenario-card {
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 20px 48px -16px rgba(9, 9, 11, 0.14);
    transform: translateY(-3px);
  }
}

.scenario-card__imgwrap {
  height: 250px;
  overflow: hidden;
  border-bottom: 1px solid #f0f0f1;
}

.scenario-card__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
  transition: transform 0.4s ease;
}

.scenario-card:hover .scenario-card__img {
  transform: scale(1.03);
}

.scenario-card__body {
  padding: 22px 24px 26px;
}

.scenario-card__title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
  color: #09090b;
}

.scenario-card__desc {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
}

.scenario-card__example {
  font-size: 12px;
  color: #a1a1aa;
  border-top: 1px dashed #e4e4e7;
  padding-top: 12px;
  display: block;
}

// ===== 工作原理四步 =====
.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 28px;
}

.step {
  padding: 36px 32px;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 16px 40px -16px rgba(9, 9, 11, 0.12);
    transform: translateY(-3px);

    .step__num {
      color: $color-primary;
    }
  }
}

.step__num {
  font-size: 44px;
  font-weight: 700;
  color: #e4e4e7;
  letter-spacing: 1px;
  transition: color 0.2s;
}

.step__title {
  margin: 14px 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: #09090b;
}

.step__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
}

// ===== 为什么选 =====
.why-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 28px;
}

.why-card {
  padding: 32px 28px;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 16px 40px -16px rgba(9, 9, 11, 0.1);
    transform: translateY(-3px);
  }
}

.why-card__icon {
  color: $color-primary;
  margin-bottom: $spacing-base;
}

.why-card__title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
  color: #09090b;
}

.why-card__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
}

// ===== 技术栈：静态文字标签 =====
.tech-stack {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.tech-pill {
  padding: 8px 24px;
  border: 1px solid #e4e4e7;
  border-radius: $radius-round;
  font-size: 14px;
  font-weight: 600;
  color: #52525b;
  background: #fff;
  letter-spacing: 0.5px;
}

// ===== 资源 =====
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px;
}

.resource-card {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: 28px;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 16px 40px -16px rgba(9, 9, 11, 0.12);
    transform: translateY(-3px);

    .resource-card__arrow {
      color: $color-primary;
    }
  }
}

.resource-card__body {
  flex: 1;
  min-width: 0;
}

.resource-card__title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 17px;
  font-weight: 600;
  color: #09090b;
  margin-bottom: 8px;
}

.resource-card__desc {
  font-size: 14px;
  color: #52525b;
}

.resource-card__arrow {
  color: #a1a1aa;
  flex-shrink: 0;
  transition: color 0.2s;
}

// ===== 常见问题 =====
.faq {
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 0 28px;
  background: #fff;

  :deep(.el-collapse-item__header) {
    font-size: 16px;
    font-weight: 600;
    color: #09090b;
    padding: 10px 0;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 20px;
    font-size: 14px;
  }
}

.faq__q {
  flex: 1;
}

.faq__a {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #52525b;
}

// ===== CTA =====
.cta {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  background: #09090b;
  padding: 72px 64px;
}

.cta__dots {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.12) 1px, transparent 1px);
  background-size: 22px 22px;
  mask-image: linear-gradient(100deg, transparent 40%, #000 90%);
  -webkit-mask-image: linear-gradient(100deg, transparent 40%, #000 90%);
  animation: dots-drift 26s linear infinite;
}

@keyframes dots-drift {
  to { background-position: 220px 110px; }
}

.cta__copy {
  position: relative;

  h2 {
    margin: 0 0 12px;
    font-size: 34px;
    font-weight: 700;
    color: #fafafa;
    letter-spacing: -0.5px;
  }

  p {
    margin: 0 0 28px;
    font-size: 15px;
    color: #a1a1aa;
  }
}

// ===== 页脚 =====
.footer {
  background: #09090b;
  padding: 32px 0;
}

.footer__inner {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
}

.footer__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fafafa;
  font-size: 15px;
  font-weight: 600;
}

.footer__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: $color-primary;
  font-size: 11px;
}

.footer__links {
  flex: 1;
  display: flex;
  gap: $spacing-lg;

  a {
    font-size: 13px;
    color: #a1a1aa;
    text-decoration: none;
    cursor: pointer;

    &:hover {
      color: #fafafa;
    }
  }
}

.footer__copy {
  font-size: 12px;
  color: #52525b;
}

// ===== 滚动渐显 =====
.reveal {
  opacity: 0;
  transform: translateY(30px);
  transition: opacity 0.8s ease, transform 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

.reveal.is-visible {
  opacity: 1;
  transform: none;
}

// ===== 响应式 =====
@media (max-width: 1024px) {
  .hero__inner {
    flex-direction: column-reverse;
    text-align: center;
    padding-top: 64px;
    padding-bottom: 64px;
    gap: 48px;
  }

  .hero__desc {
    margin-left: auto;
    margin-right: auto;
  }

  .hero__actions,
  .hero__mock {
    justify-content: center;
  }

  .hero__mock {
    width: min(640px, 100%);
  }

  .hero__title {
    font-size: 48px;
  }

  .scenario-grid,
  .steps {
    grid-template-columns: 1fr;
  }

  .why-grid {
    grid-template-columns: 1fr 1fr;
  }

  .pillar-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }

  .pillar-row__imgwrap {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .why-grid,
  .resource-grid {
    grid-template-columns: 1fr;
  }

  .section {
    padding: 64px 0;
  }

  .cta {
    padding: 44px 28px;
  }

  .hero__title {
    font-size: 44px;
  }
}

// ===== 动效降级（prefers-reduced-motion 全禁用，直接呈现最终态） =====
@media (prefers-reduced-motion: reduce) {
  .hero__badge,
  .hero__title,
  .hero__sub,
  .hero__desc,
  .hero__actions {
    opacity: 1;
    animation: none;
  }

  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .cta__dots {
    animation: none;
  }

  .scenario-card:hover,
  .step:hover,
  .why-card:hover,
  .resource-card:hover {
    transform: none;
  }

  .scenario-card:hover .scenario-card__img {
    transform: none;
  }
}
</style>
