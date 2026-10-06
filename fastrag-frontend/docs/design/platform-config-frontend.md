# 模型与平台配置 — Frontend Architecture Design（platform-config-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/platform-config-frontend.md`。

## 1. Page Map

| PAGE | 路由 | 侧边栏 |
|---|---|---|
| 模型管理 | `/admin/platform/model-management` | :201 ✅ |
| 开放密钥 | `/admin/platform/api-keys` | :202 ✅ |
| 系统五页 | `/admin/system/*`（kb-config 为 /admin redirect 落点） | :167-171 ✅ |
| 提示词模板 | `/application/prompt-templates` | ❌ 未登记 |
| ~~config-management / third-party~~ | 无路由 | 孤儿（INFERRED-1） |

## 2. Page/Layout/Component Map

```
model-management: [新增][导入 JSON][导出] 模型卡网格(状态开关/测试) 
  测试弹窗: 按 purpose 分支（Embedding 文档族/Rerank query+docs/其余 chat prompt）
  编辑弹窗: 基本信息 + apiKey(非空才提交) + 参数
api-keys: [新增密钥] 表格(名称/明文 value+复制/创建时间/操作) —— 实管字典
kb-config: KB 表格(行内空间限制编辑) + [默认配置][批量修改]弹窗（假成功）
general-settings: 三段表单（品牌含 Logo 上传/语言时区/通用参数）[保存=12 并发][重置]
sensitive-words: [模板][导入] 表格 CRUD + 屏蔽开关(disabled)
dictionary: 类型左栏 + 条目表格(前端过滤) + CRUD
terminology: 双 Tab(库卡片/词条表格,库筛选走后端) + [批量导入(开发中)]
templates: mock 卡片列表（无空态）
```

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 各页数据 | server → 页内 ref + v-loading | 标准三态 |
| 模型表单 apiKey | form（提交裁剪：空值跳过） | 防清空语义 |
| 字典类型切换 | client（左栏单选） | — |
| 品牌 | **pinia system store**（唯一跨页状态：未登录跳过加载） | App/Logo/门户三页消费 |
| Logo 文件 | form → 直连 POST /api/upload | 非 axios 实例 |

## 4. Data Flow

```
五组 API（models/config/dictionaries/sensitive-words/terminology）→ request 解包 → 各页
测试流: purpose 分支 → 三端点之一 → el-descriptions 结果渲染
保存流(general): Promise.all(12 × saveSysConfig) — 无聚合失败处理
品牌流: getSysConfigs('brand') → system store → Logo/门户标题消费
命名转换点: 密钥生成（前端 Math.random——INFERRED-4）；configValue String() 归一
```

## 5. Visual System

管理后台标准三态（v-loading/el-empty/toast）；模型卡网格；术语双 Tab。断点无。

## 6. Parent Trace

- 品牌 store 跨页消费 → spec §3（唯一 pinia 跨页状态之一）
- 死链回退（templates）→ spec INFERRED-9
- 孤儿页与 config 组孤儿端点 → INFERRED-1/6
