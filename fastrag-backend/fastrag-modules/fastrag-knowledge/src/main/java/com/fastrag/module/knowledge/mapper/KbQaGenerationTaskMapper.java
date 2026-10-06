package com.fastrag.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.knowledge.entity.KbQaGenerationTask;

/**
 * 问答对提炼任务账本 Mapper（qa-generation 特性）。
 * 单行 upsert 语义由 QaGenerationService 编排（selectById 判存 + insert/updateById），
 * 本 Mapper 不含自定义 SQL。
 */
public interface KbQaGenerationTaskMapper extends BaseMapper<KbQaGenerationTask> {
}
