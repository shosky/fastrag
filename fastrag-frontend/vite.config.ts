import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    // 双栈监听：'::' 同时覆盖 IPv4(127.0.0.1)/IPv6(::1)/局域网。
    // Node 在 Windows 上对 host:'localhost' 只绑 ::1，导致 127.0.0.1:3000
    // 连接被拒——OnlyOffice 插件桥的脚本/中继全部供给自本端口，必须稳定
    host: '::',
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  optimizeDeps: {
    // 不排除任何包，全部由 Vite 预构建，避免 UMD/CJS 兼容问题
    // EPERM 文件锁问题已通过 Windows Defender 排除项解决
    //
    // 显式声明全部依赖（尤其是 Element Plus 按组件样式入口），
    // 避免运行时按需发现新依赖触发二次预构建（曾导致 504 Outdated Optimize Dep，
    // 且 Windows 杀毒软件实时扫描会与预构建的目录重命名冲突产生 EPERM）
    include: [
      'element-plus',
      'element-plus/es',
      'element-plus/es/locale/lang/zh-cn',
      'element-plus/es/components/base/style/css',
      'element-plus/es/components/loading/style/css',
      'element-plus/es/components/message/style/css',
      'element-plus/es/components/message-box/style/css',
      'element-plus/es/components/alert/style/css',
      'element-plus/es/components/autocomplete/style/css',
      'element-plus/es/components/badge/style/css',
      'element-plus/es/components/breadcrumb/style/css',
      'element-plus/es/components/breadcrumb-item/style/css',
      'element-plus/es/components/button/style/css',
      'element-plus/es/components/card/style/css',
      'element-plus/es/components/check-tag/style/css',
      'element-plus/es/components/checkbox/style/css',
      'element-plus/es/components/checkbox-group/style/css',
      'element-plus/es/components/col/style/css',
      'element-plus/es/components/collapse/style/css',
      'element-plus/es/components/collapse-item/style/css',
      'element-plus/es/components/color-picker/style/css',
      'element-plus/es/components/date-picker/style/css',
      'element-plus/es/components/descriptions/style/css',
      'element-plus/es/components/descriptions-item/style/css',
      'element-plus/es/components/dialog/style/css',
      'element-plus/es/components/divider/style/css',
      'element-plus/es/components/drawer/style/css',
      'element-plus/es/components/dropdown/style/css',
      'element-plus/es/components/dropdown-item/style/css',
      'element-plus/es/components/dropdown-menu/style/css',
      'element-plus/es/components/empty/style/css',
      'element-plus/es/components/form/style/css',
      'element-plus/es/components/form-item/style/css',
      'element-plus/es/components/icon/style/css',
      'element-plus/es/components/image/style/css',
      'element-plus/es/components/input/style/css',
      'element-plus/es/components/input-number/style/css',
      'element-plus/es/components/link/style/css',
      'element-plus/es/components/option/style/css',
      'element-plus/es/components/pagination/style/css',
      'element-plus/es/components/popconfirm/style/css',
      'element-plus/es/components/popover/style/css',
      'element-plus/es/components/progress/style/css',
      'element-plus/es/components/radio/style/css',
      'element-plus/es/components/radio-button/style/css',
      'element-plus/es/components/radio-group/style/css',
      'element-plus/es/components/rate/style/css',
      'element-plus/es/components/row/style/css',
      'element-plus/es/components/scrollbar/style/css',
      'element-plus/es/components/select/style/css',
      'element-plus/es/components/skeleton/style/css',
      'element-plus/es/components/slider/style/css',
      'element-plus/es/components/step/style/css',
      'element-plus/es/components/steps/style/css',
      'element-plus/es/components/switch/style/css',
      'element-plus/es/components/tab-pane/style/css',
      'element-plus/es/components/table/style/css',
      'element-plus/es/components/table-column/style/css',
      'element-plus/es/components/tabs/style/css',
      'element-plus/es/components/tag/style/css',
      'element-plus/es/components/timeline/style/css',
      'element-plus/es/components/timeline-item/style/css',
      'element-plus/es/components/tooltip/style/css',
      'element-plus/es/components/tree/style/css',
      'element-plus/es/components/tree-select/style/css',
      'element-plus/es/components/upload/style/css',
    ],
  },
})
