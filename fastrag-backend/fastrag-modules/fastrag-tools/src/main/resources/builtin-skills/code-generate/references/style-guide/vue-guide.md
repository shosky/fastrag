# Vue 3 组件规范

## 基础要求
- Composition API + `<script setup>`
- TypeScript
- Scoped styles with SCSS

## 组件结构
```vue
<script setup lang="ts">
// 1. imports
// 2. props/emits
// 3. state
// 4. computed
// 5. methods
// 6. lifecycle
</script>

<template>
  <!-- template -->
</template>

<style scoped lang="scss">
/* styles */
</style>
```

## 命名
- 组件文件: PascalCase
- 事件名: kebab-case
- 插槽名: camelCase
