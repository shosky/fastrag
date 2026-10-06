package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.model.QaGenerationTaskView;

/**
 * 问答对提炼编排服务（qa-generation 契约接口，contract/interfaces.md `## qa-generation`）。
 *
 * <p>三个调用方：QaGenerationController（trigger/status，HTTP 面）、
 * IngestionConsumer 摄入挂钩（trigger，qa 模式文件自动触发）、
 * QaGenerationConsumer（execute，MQ 消费调度）。
 * 调用方对 prompt、闸门口径、事务边界零感知。
 */
public interface QaGenerationService {

    /**
     * 受理一次问答对提炼：参数校验 → 文件级互斥判定 → 任务行落账（running）→ 投递消息。
     *
     * @param config 可空（全缺省：count=3、mode=replace、model=默认网关）
     * @return 创建的任务视图（status=running、计数全 0）
     * @throws com.fastrag.common.exception.BusinessException
     *         E_QA_GEN_PARAM_INVALID / E_QA_GEN_FILE_INVALID / E_QA_GEN_NOT_READY /
     *         E_QA_GEN_TASK_RUNNING / E_QA_GEN_QUEUE_UNAVAILABLE
     */
    QaGenerationTaskView trigger(String kbId, String fileId, QaGenerationConfig config);

    /**
     * 查询该文件最近一次提炼任务（每文件单行 upsert）；从未触发返回 null。
     */
    QaGenerationTaskView status(String kbId, String fileId);

    /**
     * 消费者专用执行入口：跑完整提炼流水线（父分片迭代 → LLM 提炼 → 防御解析 →
     * 静态闸门 → replace/append 单事务落库 → 统计回写）。
     * 调用前须持有进程内文件互斥（消费者职责）；行态门——任务行非 running 时消息静默丢弃。
     * 不向调用方抛异常（内部失败全部转为任务行终态）。
     */
    void execute(String kbId, String fileId, QaGenerationConfig config);
}
