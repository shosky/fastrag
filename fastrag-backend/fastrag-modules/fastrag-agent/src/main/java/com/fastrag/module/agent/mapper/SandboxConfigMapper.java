package com.fastrag.module.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.agent.entity.SandboxConfig;
import org.apache.ibatis.annotations.Select;

/**
 * 沙箱配置 Mapper（@MapperScan("com.fastrag.module.*.mapper") 汇聚）。
 * <p>
 * {@link #selectSysConfigValue} 为模块内只读跨表查询：读取 sys_config 的
 * {@code sandbox.enabled} 键，实现沙箱总开关三级取值的平台配置层（REQ-H07，
 * 取值优先级 SysConfig > application.yml 回退 > 内置默认 false）。
 * platform-config 侧的键管理界面接线归 platform-config 链（后续批次）。
 */
public interface SandboxConfigMapper extends BaseMapper<SandboxConfig> {

    /**
     * 读平台配置键 {@code sandbox.enabled} 的原始值（TEXT，"true"/"false"/JSON 布尔等）。
     *
     * @return 配置值；键不存在返回 null（调用方降级到 yml 回退层）
     */
    @Select("SELECT config_value FROM sys_config WHERE config_key = 'sandbox.enabled' LIMIT 1")
    String selectSysConfigValue();
}
