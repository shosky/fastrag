# 问答对批量确认（qa-pairs 批量操作）— idd-grill 裁决记录

- 日期：2026-11-02
- 阶段：idd-grill（批次 1-2 全部锁定，决策树走完）
- 涉及文档：`docs/spec/qa-pairs.md`（REQ-B04/B05 + Out of Scope + §8 UI Decisions）；后续链 design → contract（新批量端点）→ tdd → implement

## 裁决（用户逐项确认）

- **D1 操作范围**：仅**批量确认**；批量删除 / 批量导出 Excel / 批量修改扩展字段一期不做（登记 Out of Scope；批量改分类/优先级受 REQ-C09 债务制约——后端不落这些字段）。
- **D2 全选语义**：表头全选 = **当前筛选结果全选**（搜索/状态/来源过滤命中行），显示「已选 N 条」，跨页保留选中。前提：列表全量返回前端（REQ-A02 现状决议）。
- **D3 混选语义**：**容忍混选，自动跳过**——已 confirmed 行幂等跳过（confirm 对已确认行重复执行无副作用，QaPairServiceImpl.java:89-95），结果中提示跳过数。
- **D4 确认交互**：二次确认弹窗，显示「共 N 条 / 其中 M 条已确认将跳过 / 本次确认 K 条」+ 确认不可逆提示（REQ-B01 无撤销）。
- **D5 审计粒度**：**一条汇总日志**（含影响条数与跳过数），不逐条记录——对齐 Excel 导入汇总审计先例（REQ-C08）。
- **D6 原子性**：**单事务整批原子**，任一失败全回滚，失败时列表无变化可直接重试——对齐导入写入先例（REQ-C07，QaPairServiceImpl.java:135-162）。
- **D7 上限**：单次 **≤ 10000 条**，超限业务错误拒绝——对齐导入上限先例（REQ-C04，MAX_DATA_ROWS）。
- **D8 顺带修正**：QaPanel 状态筛选项的 active/disabled **死选项清理为 draft/confirmed 两值**（前端偏差，对齐 spec §4.2 与 KbQaPair 实体现状；QaPanel.vue:36-43）。

## 锁定项（惯例与既有文档直接决定，未另行提问）

- 操作栏形态：选中后浮现批量操作栏，沿 FileManager 先例（FileManager.vue:679-687）
- 权限：editor 及以上（REQ-A05，@KbAuth 沿用）；viewer 不渲染批量控件
- 反馈闭环：成功 toast「已确认 X 条，跳过 Y 条」+ 刷新列表 + 清空选中；失败走统一 request 拦截器报错、保留选中可重试；执行中 loading 防重复
- 状态机不变：仍单向 draft→confirmed；批量确认是确认状态的**第二条写入路径**（REQ-B01 修订注记）
- 契约缺口：ui.md / contract.md 均无问答对页与 QA 端点条目（grep 零命中），随本特性补录
