# 问答对管理 — Frontend Architecture Design（qa-pairs-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/qa-pairs-frontend.md`。

## 1. Page Map

| PAGE | 宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| QaPanel | 详情 Tab「qa」 | 宿主权限 | 详情 Tab | Tab 切换 |
| QaImportDialog | QaPanel 内 | 随宿主 | 工具栏导入钮 | 关闭/导入成功 emit |

## 2. Page/Layout/Component Map

```
QaPanel:
┌───────────────────────────────────────────────┐
│ 筛选: 🔍关键词 [状态▾][来源▾(仅 manual)] [导入][新增]│
│ 表格: 问题|答案|状态|来源(draft/ai tag)|操作       │
│   操作: [确认](仅 draft) [编辑] [删除]            │
│ qa-panel 分页（前端切片，total>pageSize 才显示）    │
│ 弹窗: 新增/编辑（question* answer* + 扩展字段族）   │
└───────────────────────────────────────────────┘
QaImportDialog: 文件(.xlsx/.xls,limit1,手动上传) + 覆盖checkbox
  → 结果面板: 汇总消息 + 三 Tab[成功 N|跳过 N|失败 N]（表格+空态）
```

组件判定：两组件均单消费者；不抽共享件。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 列表/筛选/分页 | server → 页内 computed（纯前端过滤切片） | total=list.length |
| 编辑表单 | form（dialog） | 扩展字段收集但不提交（INFERRED-2） |
| 导入结果 | server 响应 → 页内三 Tab | importing 锁 |

## 4. Data Flow

```
api qa-pairs 组 → request 解包 → QaPanel（loadData try/finally）
导入: FormData(file,overwrite) → QaImportResult 三分桶渲染 → emit success → 父 loadData
命名转换点: keywords/similarQuestions join('，') 回填（保存方向仅 question/answer——INFERRED-2）
```

## 5. Visual System

BEM `qa__pagination` 形态沿用；状态/来源 tag；三 Tab 结果面板。桌面优先。

## 6. Parent Trace

- 前端过滤分页 → spec TASK-001
- 提交字段裁剪 → spec INFERRED-2（契约按现状）
- 导入三 Tab → spec TASK-005
