# ADR: 沙箱凭据引用式存储——存引用不落密文（sandbox-credential-by-reference）

**Date:** 2026-09-28
**Status:** Accepted（2026-09-28 沙箱实装 grill 裁决；与 WeKnora 的落库加密方案刻意偏差）

## 背景（Context）

sandbox_config 配置实体需保存 Cube/E2B 的 API key 等凭据。参照系 WeKnora 的同位决策是**落库加密**（应用层加密存储 + 响应脱敏 + 更新空值不覆盖，internal/types/tenant.go「加密」标注、config_redaction.go）；fastrag 既有惯例是**引用式**——model 表 `api_key_ref` 存环境变量名/引用、运行时解析（schema.sql:547），全仓无应用层加密设施。凭据存储方案影响表结构、API 语义与部署形态，事后迁移有成本，故立 ADR。

## 决策（Decision）

**凭据存引用，不存密文**：sandbox_config 密钥类字段存引用名（环境变量名），运行时经 SandboxConfigModule.resolveCredentials 解析为实际值；读取端点只回显引用名、不回显值（脱敏照做）；更新时空引用不覆盖既有引用（对齐 WeKnora MergeSandboxConfigForUpdate 的空值语义）。不为本特性新建加密组件。

## 被否备选（Alternatives Considered）

**WeKnora 式落库加密（应用层加密 + 密钥管理）** — 否决：需为单一特性新建加密设施（主密钥管理、轮换、加密列迁移），范围显著膨胀；且与本仓 model.api_key_ref 既有惯例分裂，形成两套凭据存储语义并存的长期成本。

**明文落库** — 否决：凭据进入备份与潜在 API 响应面，安全不可接受；此选项仅作为论证边界记录。

## 后果（Consequences）

- 实现更轻：无加密设施、无密钥轮换流程；引用解析失败（环境变量缺失）在「验证/建连」时以业务错误暴露（use 时 fail-loud），不在保存时拦截。
- 部署约束变硬：每条带凭据的沙箱配置要求部署环境预置对应环境变量，配置与部署环境耦合（排障文档须列对照表）。
- 迁移预留：未来若引入通用加密设施，引用式→密文式为一次性数据迁移（读引用→解析→加密落库），代价中等、schema 不变（列语义从引用名变密文）。
