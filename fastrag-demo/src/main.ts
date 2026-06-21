import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { vPermission } from './directives/v-permission'

import './assets/styles/reset.scss'
import './assets/styles/global.scss'

const app = createApp(App)

// 注册 Element Plus
app.use(ElementPlus, { locale: zhCn })

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册权限指令
app.directive('permission', vPermission)

// 注册 Pinia
app.use(createPinia())

// 注册路由
app.use(router)

app.mount('#app')
