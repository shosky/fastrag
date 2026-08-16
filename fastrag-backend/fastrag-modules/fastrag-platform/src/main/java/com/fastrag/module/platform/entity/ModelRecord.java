package com.fastrag.module.platform.entity;

/**
 * AI模型记录实体
 * <p>
 * 对应数据库表 {@code model}，存储平台中注册的AI模型信息，包括模型的名称、编码、用途、
 * 品牌、API地址和密钥引用等。支持DeepSeek等模型的思考模式（enableThinking）配置。
 * 模型ID使用雪花算法（ASSIGN_ID）自动生成。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} — 模型唯一标识（雪花算法生成）</li>
 *   <li>{@code name} — 模型显示名称</li>
 *   <li>{@code code} — 模型编码（如 deepseek-chat、gpt-4o 等）</li>
 *   <li>{@code purpose} — 模型用途（如 chat、embedding、rerank）</li>
 *   <li>{@code brand} — 模型品牌/供应商（如 openai、deepseek）</li>
 *   <li>{@code apiUrl} — 模型API服务地址</li>
 *   <li>{@code apiKeyRef} — API密钥的引用标识（不直接存储密钥）</li>
 *   <li>{@code status} — 模型状态（如 active、inactive）</li>
 *   <li>{@code contextWindow} — 上下文窗口大小（Token 数）</li>
 *   <li>{@code enableThinking} — 是否启用思考模式（DeepSeek等模型支持）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.ModelService
 * @see com.fastrag.module.platform.mapper.ModelRecordMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("model") public class ModelRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,code,purpose,brand,apiUrl,apiKeyRef,status;
    /** 上下文窗口大小（Token 数） */
    private Integer contextWindow;
    /** 启用思考模式（DeepSeek 等模型支持），默认关闭 */
    private Boolean enableThinking;
}
