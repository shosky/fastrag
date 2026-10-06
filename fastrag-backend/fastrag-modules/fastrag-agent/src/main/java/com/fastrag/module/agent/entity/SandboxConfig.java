package com.fastrag.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙箱后端配置实体，对应数据库表 {@code sandbox_config}（data.md「sandbox_config」节）。
 *
 * <p>核心职责：沙箱资源的命名配置台账——把「哪个后端、连哪里、用什么镜像/模板、给多少资源、
 * 准不准出网」折叠为一行配置，供应用引用（app.sandbox_config_id）与生命周期消费
 * （契约 agent.SandboxConfigStore，SandboxConfigModule，REQ-H04~H07）。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>id — 主键，ASSIGN_ID 雪花策略（对齐本仓全局 id 策略）</li>
 *   <li>userId — 归属者；写权限 = 归属者或管理员（REQ-H04）</li>
 *   <li>sandboxType — ∈ {docker, cube, e2b, disabled}；disabled 为显式关闭态保留行</li>
 *   <li>enabled — 配置级细粒度开关；平台总开关（REQ-H07）是独立上层</li>
 *   <li>allowPrivateEndpoints — 私网 endpoint 显式放行（REQ-H16）；云 metadata/link-local 恒拒</li>
 *   <li>networkPolicy / envVars / dockerConfig / cubeConfig / e2bConfig — JSON 列，
 *       以原始 JSON 字符串承载（读写经服务的 Jackson 序列化边界转换）；
 *       密钥类字段（api_key_ref）只存引用名不存值（ADR-0007 凭据引用式）</li>
 *   <li>identityFingerprint — 身份要素（type + endpoint + api_key_ref + 镜像/模板）确定性哈希，
 *       update 重算；非身份字段变更不动此列（REQ-H09 绑定侧重建比对键）</li>
 *   <li>cordonAt — 隔离标记：换凭据/换底模时置位（该配置不再新建实例，存量下次 resolve 重建）；NULL = 正常</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.mapper.SandboxConfigMapper 数据库映射器
 * @see com.fastrag.module.agent.sandbox.config.SandboxConfigService 业务服务
 */
@Data
@TableName("sandbox_config")
public class SandboxConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 归属者（创建者）用户 ID */
    private String userId;

    /** 人读名；同 user_id 下唯一（应用层校验，无 DB 唯一索引） */
    private String name;

    /** 后端类型：docker / cube / e2b / disabled */
    private String sandboxType;

    /** 配置级开关（true=启用） */
    private Boolean enabled;

    /** 单次执行超时秒数；0 = 内置默认 60s */
    private Integer defaultTimeoutSec;

    /** 私网 endpoint 显式放行（REQ-H16） */
    private Boolean allowPrivateEndpoints;

    /** JSON 列：Cube/E2B {allow_internet, allow_domains[], deny_domains[], inbound_closed}；docker 携带域名列表保存被拒 */
    private String networkPolicy;

    /** JSON 列：注入沙箱的环境变量（非密钥类；密钥走 *_config 内引用字段） */
    private String envVars;

    /** JSON 列：{image, host, tls_cert_path, cpu_limit, memory_limit_mb, pids_limit, network_mode, idle_ttl_sec} */
    private String dockerConfig;

    /** JSON 列：{api_url, proxy_url, sandbox_domain, api_key_ref, template_id, http_timeout_sec, ttl_sec, dns_servers[]} */
    private String cubeConfig;

    /** JSON 列：{api_url, sandbox_domain, api_key_ref, template_id, proxy_url, http_timeout_sec, ttl_sec} */
    private String e2bConfig;

    /** 身份要素确定性哈希（SHA-256，REQ-H09 重建比对键）；非身份字段变更不动 */
    private String identityFingerprint;

    /** 隔离标记：换凭据/换底模时置位；NULL = 正常 */
    private LocalDateTime cordonAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
