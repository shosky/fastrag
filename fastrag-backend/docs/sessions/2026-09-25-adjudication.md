# 会话归档 — 2026-09-25 INFERRED/GAP 全量代裁（230 条闭环）

## 需求

用户指示「为什么要我拍板，你分析代码实现拍板」——裁决权由用户移交代理：INFERRED 深证代码后拍板，GAP 给工程决议。裁决协议 R1-R4（深证升级/现状裁定/实证消解/代裁决议）见 `.tmp/idd-adjudicate-task.md`；所有代裁均标注「代裁，用户可推翻」。

## 追加：A 类纯死物清理（同日，用户指令「A」）

- 删除 `fastrag-modules/fastrag-bpm/` 整目录（0 git 追踪、0 pom 引用、target 残留 137 个陈旧 .class）。
- 删除 **16 个空目录**：common/{dto,model,resources}、infra/{mysql,redis}、retrieval/engine、application|platform|publish|tools 的 model、knowledge/{event,manager}、security/{handler,resolver}、security 测试空包链（`fastrag-security/src/test/java` 按 Maven 惯例保留空壳）。
- 删除 `scripts/dbg-pdf-render.mjs`（0 字节）、`scripts/merge-mapping.cjs`（引用已删文档；git 可恢复）。
- 预检：pom 零引用、merge-mapping 零被引；删后复扫空目录 0 残留。feature-map 孤儿区与 #16 条目已同步改写。

## 执行

7 组并行代理按特性域裁决（文件集互不重叠），后主控补漏 6 处（contract/data.md 各域枚举遗漏行）+ 记账。全程只改文档。

## 裁决总量与分布

| 组 | 特性 | INFERRED（R1/R2） | GAP（R3/R4/R2） |
|---|---|---|---|
| 1 | iam、kb-core、qa-pairs | 23（11/12） | 18（2/13/3）+iam 搬入复核 5 项 |
| 2 | parse-strategy、file-ingestion、chunk-management | 14（9/5） | 12（2/10/0） |
| 3 | onlyoffice-editing、ai-chunking、retrieval | 15（14/1） | 11（1/10/0） |
| 4 | knowledge-graph、benchmark-evaluation | 5（5/0） | 7（1/4/2） |
| 5 | publish-review、platform-config、tools-skills-mcp | 30（26/4） | 26（3/23/0） |
| 6 | workflow、agent | 11（11/0） | 16（5/10/1） |
| 7 | app-center、operation-analytics | 22（20/2） | 10（1/9/0） |
| **合计** | | **130（96/34）** | **100（15/79/6）** |

## 影响语义的关键裁决（摘要，全文在对应文档）

- **iam REQ-021**：未认证请求深证为 **403**（Spring Security 6.2 默认 EntryPoint，无定制）。
- **iam 复-1**：同组织兜底按现状分级语义为准（private 无/管理员 owner/成员 editor，max 合并）——tdd 切片 0 HITL 解除。
- **workflow 引擎语义定稿（R4 代裁）**：fail-fast 单节点、拓扑序+节点数步数上限+结构校验拒环、单节点 30s/整图 300s、并发不设限；tdd E1~E5 已填实。
- **publish 新缺陷**：前端建计划传 `scheduledAt`、后端绑定 `scheduledTime`（PublishPanel.vue:239 vs KbPublishPlan.java:29）——静默失配，`scheduled_time` 实际亦恒 NULL。
- **断言纠正 ×5**：benchmark 不经注入消费 RetrievalService（自带管线）；mcp_call_log 为只写死表（operation 零引用）；MCP 写操作无 @Loggable（原文档误记有）；agent model_override 仅落库不生效；app run() 通道仍被调试对话/测试录制消费（非死代码）。
- **实证消解（R3 代表）**：上传体积=application.yml 200MB；区域框选仅 PDF 双端实证；term 缓存 invalidate() 全写路径调用（单实例即时生效）；鉴权兜底=SecurityConfig 全局 authenticated（KB 级 ACL 无兜底债务维持）；agent.id=雪花、内置 6 slug 开机播种。
- **多数 R2/R4 的共同结论**：大量「是否有意」不可考处，一律按现状为准登记（现状即事实源），现状与目标态差距均已入债务清单。

## 终态

- 链文档内 INFERRED/GAP 标注清零（仅存图例说明与 ADR 重构头的合法 [GAP] 元引用）。
- feature-map 18 条目 pending 改「已代裁闭环（2026-09-24）」；#9 Shared by 勘误（benchmark 移除）、#12 增补 scheduledAt 新缺陷；README 待办清单同步。
- **待用户事项收敛为 4 项**：① 根 docs/ 树处置（恢复/放弃）；② tdd 红绿循环是否执行；③ 40+ 缺陷/债务修复（新增 scheduledAt 失配）；④ 文档入库提交。
