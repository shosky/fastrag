# 模型与平台配置 — Implementation Document（platform-config-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/admin/platform/{model-management 567, api-keys 177}
src/views/admin/system/{kb-config 253, general-settings 362, sensitive-words 231, dictionary 246, terminology 342}
src/views/answer-kb/templates.vue        — 整页 mock（108）
src/views/admin/system/config-management.vue — ~~孤儿~~（安全/发布策略/配置历史）
src/views/admin/platform/third-party.vue     — ~~孤儿~~（三方平台走字典 API）
src/stores/system.ts                    — 品牌 store（49；未登录跳过）
```

## 2. Interface Implementations

### 模型测试分支

**Logic Flow:**
```
openTestDialog(model):
1. purpose UPPER
2. EMBEDDING → testModelEmbedding（docs 非空校验）
3. RERANK → testModelRerank(query, docs[])
4. 其余 → testModelChat(prompt)
5. 结果 el-descriptions 分支渲染
保存: payload 组装——apiKey 非空才进（防清空已有密钥）
```

### 通用设置保存

**Logic Flow:**
```
saveAll: Promise.all(12 × saveSysConfig({configKey, configValue: String(v), configType}))
重置: getDefaultConfigs() → keys → resetDefaultConfig(configKeys)   // 数组传单 key URL（INFERRED-8）
Logo: FormData → fetch POST /api/upload（Bearer）→ logoUrl 回填
```

### 页面登记表

| 页 | 要点 | 证据 |
|---|---|---|
| api-keys | key=前端 Math.random；expiry/permissions 不提交；明文+复制 vs alert 矛盾 | :67-105,91 |
| kb-config | 行内编辑改 spaceLimit 提交 totalSize；两弹窗假成功 | :75-105 |
| terminology | 库筛选走后端；批量导入 stub | :67-86,173-176 |
| templates | 复数函数名不存在 → 回退 FAQ mock（写错 store） | :39-57 |
| 品牌 store | 未登录 loadConfig 直接返回默认 | system.ts:17 |

## 3. Decisions & Deviations

- **假成功清单**（INFERRED-7）：kb-config 两弹窗；templates 死链回退。
- **语义矛盾**（INFERRED-4）：密钥明文展示 vs「不再显示」。
- **孤儿页**（INFERRED-1）：config-management（消费 config 组 history/export/import/review-flow 等孤儿端点）、third-party（每次保存 create 无更新）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：模型测试 purpose 分支、apiKey 裁剪、configValue String 归一、品牌 store 未登录分支。

## 5. Implementation Trace

```
TASK-001 → 模型管理 → model-management:75-418
TASK-002 → 开放密钥 → api-keys:29-105
TASK-003 → kb-config → :33-105
TASK-004 → general-settings → :20-174
TASK-005..007 → 敏感词/字典/术语 → 各页
TASK-008 → templates mock → :4-57
```
