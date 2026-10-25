/**
 * 产品动态静态数据（CONTEXT.md 决策快照 17：version 倒序，首条驱动首页资源卡徽标）。
 * 一期无后端接口；未来后端化时仅本文件消费边界替换为接口，页面与组件不变。
 */
export type ChangelogType = '新功能' | '优化' | '修复' | '安全'

export interface ChangelogEntry {
  /** 版本号，如 v1.4.0 */
  version: string
  /** 发布日期 YYYY-MM-DD */
  date: string
  /** 一句话主题 */
  title: string
  /** 类型（四值枚举，映射 Element Plus 语义色） */
  type: ChangelogType
  /** 要点列表 */
  items: string[]
}

export const CHANGELOG: ChangelogEntry[] = [
  {
    version: 'v1.4.0',
    date: '2026-10-24',
    title: '首页门户上线',
    type: '新功能',
    items: [
      '新增门户首页、操作手册与产品动态三个门户页面',
      '登录后默认落地门户首页，平台整体收敛为门户菜单',
      '操作手册内嵌渲染，支持章节目录导航',
    ],
  },
  {
    version: 'v1.3.0',
    date: '2026-10-20',
    title: 'MCP 绑定工具级勾选',
    type: '新功能',
    items: ['应用绑定 MCP 服务支持按工具白名单勾选（app_mcp_binding.selected_tools）'],
  },
  {
    version: 'v1.2.1',
    date: '2026-10-15',
    title: '并行预检索取代强制检索',
    type: '优化',
    items: ['应用对话首轮检索改为并行预检索，响应更快（ADR-PARALLEL-PRE-RETRIEVAL）'],
  },
  {
    version: 'v1.2.0',
    date: '2026-10-08',
    title: '引用卡片双通道修复',
    type: '修复',
    items: ['修复引用卡片 sources 接线与 references 回显', 'SSE 严格分帧，消除粘包导致的渲染错乱'],
  },
  {
    version: 'v1.1.0',
    date: '2026-09-28',
    title: '对话留痕引擎上线',
    type: '新功能',
    items: ['Agent 运行过程逐步留痕，支持步骤级回看（ConversationTraceRecorder）'],
  },
]
