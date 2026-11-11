import { defineConfig } from 'vitest/config'
import path from 'node:path'
import vue from '@vitejs/plugin-vue'

// 前端单测基建（2026-09-21 idd-tdd 解阻引入）。
// environment: node —— 首批切片均为纯函数/常量契约测试，无需 DOM；
// 引入组件级测试时再切换 happy-dom 并按需补充配置。
// 2026-09-25: 补 @ 别名（与 vite.config.ts 一致）——composable 层测试需要解析 @/utils/storage。
// 2026-09-26: 补 @vitejs/plugin-vue——组件级测试（MessageBubble/EffectivenessBar）
// 直接 mount .vue SFC；DOM 环境由文件头 // @vitest-environment jsdom 按文件声明。
export default defineConfig({
  // transformAssetUrls 关闭（2026-11-04）：portal 首页 SFC 的 <img src="/portal/...">
  // public 绝对路径会被插件转成模块导入，vite-node 解析 public 资源时抛 pathToFileURL 错误；
  // 测试只需要字面量字符串，真实构建的资产处理由 vite.config.ts 自己的插件实例负责。
  plugins: [vue({ template: { transformAssetUrls: false } })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    reporters: 'default',
  },
})
