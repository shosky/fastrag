// ===========================================================================
// 后端组件白名单快照（fixture，REQ-WG-A06 前端半边）
//
// 与 fastrag-backend WidgetTemplateValidator 的组件白名单常量同步（双份常量 +
// 守护测试钉住一致，MOD-WIDGET-FRONTEND ## seam「组件注册表缝」）。
// 后端白名单变更时：此处 + registry.ts 各加一项 + 双端测试各加一条，
// 渲染器与分发链零改动。
// ===========================================================================

/** 后端保存校验白名单（组件名集合，19 项 @ v1.0.0） */
export const BACKEND_WIDGET_COMPONENT_WHITELIST: readonly string[] = [
  // 容器
  'Card',
  'ListView',
  'ListViewItem',
  // 布局
  'Row',
  'Col',
  'Divider',
  'Form',
  // 文本
  'Title',
  'Text',
  'Caption',
  'Markdown',
  // 内容
  'Badge',
  'Icon',
  'Image',
  // 交互
  'Button',
  // 表单
  'Input',
  'Select',
  'RadioGroup',
  'Checkbox',
]
