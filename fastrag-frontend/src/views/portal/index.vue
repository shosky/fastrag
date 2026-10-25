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

// 核心能力：七项生产级 RAG 能力，配平台真实界面截图
const capabilities = [
  { title: '知识库管理', img: '/portal/scene-knowledge.png', desc: '文档自动解析、分片、向量化，分类、标签、知识图谱一体化管理' },
  { title: '智能分片', img: '/portal/scene-chunks.png', desc: '截图式分片卡片视图：编号、Token 统计、元数据面板，支持新增分片与重新分片' },
  { title: '多种切片方式', img: '/portal/scene-parse.png', desc: '通用文档 / 音频 / 图片多种解析策略，分块大小与分隔符自由定制' },
  { title: '知识图谱', img: '/portal/scene-graph.png', desc: '从分片自动抽取实体与关系，力导向图可视化，支持实体搜索' },
  { title: '应用对话', img: '/portal/crop-chatcolumn.jpg', desc: '零代码创建对话应用，绑定知识库与模型，流式回答附带引用来源' },
  { title: '检索增强', img: '/portal/crop-cite.jpg', desc: '向量 + 关键词混合检索与重排序，检索日志全程可分析' },
  { title: '运营分析', img: '/portal/crop-analytics.png', desc: '知识资产盘点、热门排行与引用率统计，运营效果一目了然' },
]

// 业务场景：示例取自平台内真实应用
const scenarios = [
  { title: '智能客服', img: '/portal/crop-chatcolumn.jpg', desc: '客服制度、产品 FAQ 统一入库，回答口径一致、可溯源，新员工即学即用', example: '高速客服' },
  { title: '企业知识问答', img: '/portal/crop-kb.png', desc: '制度、规范、手册随问随答，文档不再躺在网盘里', example: '知识库对话' },
  { title: '数据问答', img: '/portal/crop-analytics.png', desc: '自然语言检索业务知识，检索日志与命中率全程可分析', example: '检索日志分析' },
  { title: '知识运营', img: '/portal/scene-application.png', desc: '多应用统一管理，按业务线组织知识资产', example: '应用中心' },
]

// 三步上手：还原真实产品路径
const steps = [
  { num: '01', title: '上传文档', desc: '把 PDF、Word、Markdown 等文档拖入知识库，系统自动完成解析、分片与向量化' },
  { num: '02', title: '创建应用', desc: '一键创建对话应用，绑定知识库与模型，配置开场白后即可预览问答效果' },
  { num: '03', title: '提问对话', desc: '用自然语言向应用提问，获得基于企业知识的检索增强回答，答案可溯源' },
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
    a: '点击「立即开始」注册登录，按三步操作：上传文档到知识库 → 创建应用并绑定知识库 → 提问对话。遇到问题可随时查阅操作手册。',
  },
]

const latestVersion = CHANGELOG[0]?.version

// 滚动渐显
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
    { threshold: 0.12 }
  )
  els.forEach((el) => revealObserver?.observe(el))
})

onBeforeUnmount(() => revealObserver?.disconnect())
</script>

<template>
  <div class="portal-home">
    <!-- ===== hero：大字排版 + 大尺寸真实产品截图 ===== -->
    <section class="hero">
      <div class="container hero__inner">
        <div class="hero__copy">
          <span class="hero__badge">企业级检索增强生成平台</span>
          <h1 class="hero__title">让企业知识<br />一问即答</h1>
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

    <!-- ===== 核心能力 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">核心能力</span>
          <h2 class="section-head__title">生产级 RAG，开箱即用</h2>
          <p class="section-head__sub">覆盖知识生产、检索增强与应用对话的完整链路</p>
        </div>
        <div class="cap-grid">
          <div v-for="cap in capabilities" :key="cap.title" class="cap-card reveal" @click="router.push('/home')">
            <div class="cap-card__imgwrap">
              <img :src="cap.img" :alt="cap.title + '界面'" class="cap-card__img" loading="lazy" />
            </div>
            <h3 class="cap-card__title">{{ cap.title }}</h3>
            <p class="cap-card__desc">{{ cap.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 核心能力：bento 网格 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">核心能力</span>
          <h2 class="section-head__title">生产级 RAG，开箱即用</h2>
          <p class="section-head__sub">知识图谱 · 智能分片 · 多种切片方式 · 检索增强 · 应用对话</p>
        </div>
        <div class="bento">
          <div v-for="(cap, idx) in capabilities" :key="cap.title" class="bento__card reveal" :class="{ 'reveal-d1': idx % 3 === 1, 'reveal-d2': idx % 3 === 2 }" @click="router.push('/home')">
            <div class="bento__copy">
              <h3>{{ cap.title }}</h3>
              <p>{{ cap.desc }}</p>
            </div>
            <div class="bento__imgwrap">
              <img :src="cap.img" :alt="cap.title + '界面截图'" class="bento__img" loading="lazy" />
            </div>
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

    <!-- ===== 三步上手 ===== -->
    <section class="section">
      <div class="container">
        <div class="section-head reveal">
          <span class="section-head__eyebrow">快速开始</span>
          <h2 class="section-head__title">三步构建企业知识问答</h2>
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
            <p>上传文档，三步获得可溯源的 AI 问答服务</p>
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
  margin: 0 0 20px;
  font-size: 64px;
  line-height: 1.14;
  font-weight: 700;
  letter-spacing: -1.5px;
  color: #09090b;
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

// 入场动效：徽章 → 标题 → 描述 → 按钮 依次浮现
// 入场动效：徽章 → 标题 → 描述 → 按钮 依次浮现
.hero__badge,
.hero__title,
.hero__desc,
.hero__actions {
  opacity: 0;
  animation: rise-in 0.9s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

.hero__badge { animation-delay: 0.05s; }
.hero__title { animation-delay: 0.15s; }
.hero__desc { animation-delay: 0.3s; }
.hero__actions { animation-delay: 0.45s; }

@keyframes rise-in {
  from { opacity: 0; transform: translateY(26px); }
  to { opacity: 1; transform: translateY(0); }
}

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

// ===== 核心能力 =====
.cap-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px;
}

.cap-card {
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 20px 20px 28px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 20px 48px -16px rgba(9, 9, 11, 0.14);
    transform: translateY(-3px);
  }
}

.cap-card__imgwrap {
  border: 1px solid #f0f0f1;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 20px;
}

.cap-card__img {
  display: block;
  width: 100%;
  transition: transform 0.35s ease;
}

.cap-card:hover .cap-card__img {
  transform: scale(1.025);
}

.cap-card__title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: #09090b;
}

.cap-card__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
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

// ===== 三步 =====
.bento {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.bento__card {
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 22px 22px 24px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: #d4d4d8;
    box-shadow: 0 16px 40px -16px rgba(9, 9, 11, 0.14);
    transform: translateY(-3px);

    .bento__img {
      transform: scale(1.03);
    }
  }
}

.bento__imgwrap {
  border: 1px solid #f0f0f1;
  border-radius: 10px;
  overflow: hidden;
}

.bento__img {
  display: block;
  width: 100%;
  transition: transform 0.35s ease;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

  .cap-grid,
  .scenario-grid {
    grid-template-columns: 1fr;
  }

  .steps {
    grid-template-columns: 1fr;
  }

  .why-grid {
    grid-template-columns: 1fr 1fr;
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

// ===== 滚动渐显（reduced-motion 降级） =====
@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .cta__dots {
    animation: none;
  }

  .cap-card:hover .cap-card__img,
  .scenario-card:hover .scenario-card__img {
    transform: none;
  }
}
</style>
