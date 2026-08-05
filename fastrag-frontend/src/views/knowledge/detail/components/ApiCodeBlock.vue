<script setup lang="ts">
import { computed } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { ApiEndpoint } from '@/data/api-docs/types'

const props = defineProps<{
  endpoint: ApiEndpoint
  baseUrl: string
  kbId: string
  token?: string
}>()

type Lang = 'curl' | 'python' | 'java' | 'javascript'
const activeLang = ref<Lang>('curl')

function resolvePath(template: string): string {
  return template
    .replace('{kbId}', props.kbId)
    .replace('{id}', props.kbId)
    .replace('{tokenId}', '<tokenId>')
    .replace('{knowledgeId}', '<knowledgeId>')
    .replace('{fileId}', '<fileId>')
}

const code = computed(() => {
  const ep = props.endpoint
  const method = ep.method
  const url = `${props.baseUrl}${resolvePath(ep.path)}`
  const authHeader = props.token ? `Authorization: Bearer ${props.token}` : 'Authorization: Bearer <your-api-token>'

  const hasBody = ep.body && ep.body.length > 0
  const bodyStr = ep.requestExample || JSON.stringify(
    Object.fromEntries(ep.body?.map(p => [p.name, p.example ?? p.type === 'string' ? `示例${p.name}` : p.type === 'number' ? 1 : p.type === 'boolean' ? true : p.type]) || []),
    null,
    2
  )

  const queryParams = ep.queryParams?.map(p => `${p.name}=<${p.name}>`).join('&')
  const fullUrl = queryParams ? `${url}?${queryParams}` : url

  switch (activeLang.value) {
    case 'curl': {
      const parts = [`curl -X ${method} '${fullUrl}'`]
      parts.push(`  -H '${authHeader}'`)
      if (hasBody) parts.push(`  -H 'Content-Type: application/json'`)
      parts.push(`  -d '${bodyStr}'`)
      return parts.join(' \\\n')
    }
    case 'python': {
      let code = `import requests\n\n`
      code += `url = "${fullUrl}"\n`
      code += `headers = {\n    "Authorization": "Bearer ${props.token || '<your-api-token>'}",\n`
      if (hasBody) code += `    "Content-Type": "application/json",\n`
      code += `}\n`
      if (hasBody) {
        code += `\npayload = ${bodyStr}\n`
        code += `\nresponse = requests.${method.toLowerCase()}(url, headers=headers, json=payload)\n`
      } else {
        code += `\nresponse = requests.${method.toLowerCase()}(url, headers=headers)\n`
      }
      code += `print(response.json())`
      return code
    }
    case 'java': {
      const javaMethod = method === 'DELETE' ? 'delete' : method === 'POST' ? 'post' : method === 'PUT' ? 'put' : 'get'
      let code = `import java.net.http.*;\nimport java.net.URI;\n\n`
      code += `HttpClient client = HttpClient.newHttpClient();\n`
      code += `HttpRequest request = HttpRequest.newBuilder()\n`
      code += `    .uri(URI.create("${fullUrl}"))\n`
      code += `    .header("Authorization", "Bearer ${props.token || '<your-api-token>'}")\n`
      if (hasBody) {
        code += `    .header("Content-Type", "application/json")\n`
        code += `    .method("${method}", HttpRequest.BodyPublishers.ofString("${bodyStr.replace(/"/g, '\\"')}"))\n`
      } else {
        code += `    .method("${method}", HttpRequest.BodyPublishers.noBody())\n`
      }
      code += `    .build();\n\n`
      code += `HttpResponse<String> response = client.send(request,\n    HttpResponse.BodyHandlers.ofString());\n`
      code += `System.out.println(response.body());`
      return code
    }
    case 'javascript': {
      let code = `const url = "${fullUrl}";\n\n`
      code += `fetch(url, {\n  method: "${method}",\n  headers: {\n    "Authorization": "Bearer ${props.token || '<your-api-token>'}",\n`
      if (hasBody) code += `    "Content-Type": "application/json",\n`
      code += `  },\n`
      if (hasBody) {
        code += `  body: JSON.stringify(${bodyStr}),\n`
      }
      code += `})\n  .then(res => res.json())\n  .then(data => console.log(data));`
      return code
    }
  }
})

async function copyCode() {
  try {
    await navigator.clipboard.writeText(code.value)
    ElMessage.success('代码已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <div class="api-code-block">
    <!-- Language tabs -->
    <div class="api-code-block__header">
      <div class="api-code-block__langs">
        <button
          v-for="lang in (['curl', 'python', 'java', 'javascript'] as const)"
          :key="lang"
          class="api-code-block__lang-btn"
          :class="{ 'api-code-block__lang-btn--active': activeLang === lang }"
          @click="activeLang = lang"
        >
          {{ lang === 'javascript' ? 'JavaScript' : lang === 'python' ? 'Python' : lang === 'java' ? 'Java' : 'cURL' }}
        </button>
      </div>
      <el-button :icon="CopyDocument" size="small" link @click="copyCode">复制</el-button>
    </div>
    <!-- Code content -->
    <pre class="api-code-block__code"><code>{{ code }}</code></pre>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.api-code-block {
  border: 1px solid $border-base;
  border-radius: $radius-base;
  overflow: hidden;
  background: #1E293B;
  font-size: 13px;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-base;
    background: #0F172A;
    border-bottom: 1px solid #334155;
  }

  &__langs {
    display: flex;
    gap: 2px;
    background: #1E293B;
    border-radius: $radius-sm;
    padding: 2px;
  }

  &__lang-btn {
    padding: 4px 10px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: #94A3B8;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      color: #CBD5E1;
    }

    &--active {
      background: $color-primary;
      color: #fff;
    }
  }

  &__code {
    margin: 0;
    padding: $spacing-base;
    color: #E2E8F0;
    font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 13px;
    line-height: 1.6;
    overflow-x: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
