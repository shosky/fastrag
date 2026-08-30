package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.AiChunkApplyRequest;
import com.fastrag.module.knowledge.model.AiChunkResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI分片 服务（预览 + OCR 解析 + 跨页合并 + 自动/手动分片 + 应用落库）。
 *
 * <p>职责（设计文档 ai-chunk-preview-semantic.md §3.3）：
 * <ul>
 *   <li>analyze — 按统一段落模型解析 + 结构级跨页合并 + LLM 自动分片（Embedding/规则回退）</li>
 *   <li>stream — SSE 渐进推送（progress/fragment/chunk/done/error，协议见 §7.2）</li>
 *   <li>apply — 尊重用户确认的分片边界落库：删旧 chunks → 向量化 → 写 parsed.md/parsed.json；
 *       <b>不</b>重新切分、<b>不</b>重新解析原文件</li>
 * </ul>
 */
public interface AiChunkService {

    /** 同步分析：返回段落对齐模型 + 自动分片结果（SSE 不可用时的兜底） */
    AiChunkResult analyze(String kbId, String fileId);

    /** 同步分析（按需分片）：withChunks=false 仅返回段落对齐模型（左侧原件渲染用，不触发 LLM/Embedding） */
    AiChunkResult analyze(String kbId, String fileId, boolean withChunks);

    /** SSE 流式分析：fragment/chunk 逐个推送（右侧分片卡逐个出现） */
    SseEmitter stream(String kbId, String fileId);

    /**
     * SSE 版面分析：逐页 VLM 识别内容块（标题/正文/表格/图片/代码/公式 + 归一化坐标）。
     * MinIO layout.json 缓存命中时一次全量推送；未命中逐页生成并回写缓存。
     */
    SseEmitter streamLayout(String kbId, String fileId);

    /** 应用：以用户确认的分片列表为唯一事实源落库，返回落库分片数 */
    int apply(String kbId, String fileId, AiChunkApplyRequest request);

    /**
     * 区域内容提取（结构化优先，OCR 兜底）：
     * 文字层行命中区域 → 结构化块（表格/段落/标题，精确文本零 OCR）；
     * 区域内内容图片 → 裁剪调 OCR 模型；两者皆无（纯图形/扫描页）→ 整区域渲染 OCR。
     *
     * @param page   页码（1-based）
     * @param x      区域左上角 x（归一化 0~1，cropBox 顶左原点，与 preview imageBoxes 同坐标系）
     * @param y      区域左上角 y
     * @param width  区域宽
     * @param height 区域高
     * @return {blocks: [{type: heading|paragraph|table|image, text}]}
     */
    java.util.Map<String, Object> ocrImageRegion(String kbId, String fileId, int page,
                                                 float x, float y, float width, float height);
}
