package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.model.FileProcessRequest;
import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库文件管理服务接口。
 *
 * <p>定义知识库文件完整生命周期的管理操作，包括文件上传、处理触发、
 * 信息更新、软删除/恢复/彻底删除、回收站清空、复制、跨知识库移动、
 * 处理状态查询和分块预览。是知识库模块的核心服务之一。</p>
 *
 * <p>核心功能分组：</p>
 * <ul>
 *   <li>查询：list（文件列表）、listDeleted（回收站列表）、getProcessingStatus（处理状态）、
 *       previewChunks（分块预览）</li>
 *   <li>上传处理：upload（上传文件到MinIO并保存元数据）、process（触发解析/分块/向量化处理）、
 *       retryFile（重试处理）</li>
 *   <li>修改：update（更新文件信息）、rename相关操作、copy（复制）、moveToKb（跨知识库移动）</li>
 *   <li>删除：delete（软删除）、restore（恢复）、permanentDelete（彻底删除）、emptyRecycleBin（清空回收站）</li>
 * </ul>
 */
public interface FileService {
    List<FileDto> list(String kbId);
    List<FileDto> listDeleted(String kbId);
    FileDto upload(String kbId, MultipartFile file, String folderId);
    void process(String kbId, String fileId, FileProcessRequest req);
    FileDto update(String kbId, String fileId, Map<String, Object> patch);
    void delete(String kbId, String fileId);
    void restore(String kbId, String fileId);
    void permanentDelete(String kbId, String fileId);
    void emptyRecycleBin(String kbId);
    FileDto copy(String kbId, String fileId);
    Map<String, Object> getProcessingStatus(String kbId, String fileId);

    /**
     * 分块预览。
     *
     * @param strategyId   策略 id（可为空，空时按扩展名自动匹配；customConfig 非空时忽略）
     * @param customConfig 自定义临时策略（未落库预览）：{parseMethod, advanced}；非空时优先于 strategyId
     */
    Map<String, Object> previewChunks(String kbId, String fileId, String strategyId, Map<String, Object> customConfig);
    FileDto retryFile(String kbId, String fileId);

    /**
     * 重新分片。strategyId 三态：null=沿用当前绑定；非空 id=换绑+重切；空串=清除覆盖回自动匹配+重切。
     * 见 ADR-0001（策略绑定变更必须与重新分片原子完成）。
     */
    FileDto reChunkFile(String kbId, String fileId, String strategyId);

    /**
     * 重新分片（带预设策略）。{@code presetStrategy} 为分片策略 key（如 {@code structure_aware}），
     * 仅当未显式传 {@code strategyId} 时生效：自动确保文件绑定该策略——已有则复用，
     * 无则创建文件级预设策略并绑定。用于「按结构分片」等一键预设入口。
     */
    FileDto reChunkFile(String kbId, String fileId, String strategyId, String presetStrategy);
    FileDto moveToKb(String sourceKbId, String fileId, String targetKbId, String targetFolderId);

    /**
     * 保存文件级专属自定义策略并重新分片（原子完成，ADR-0001）。
     * 保存为绑定 file_id 的隐藏策略（不出现在策略管理列表），随后按该策略重切。
     */
    FileDto saveFileStrategy(String kbId, String fileId, ParseStrategyRequest req);

    /**
     * 替换文件原始二进制（用于 OnlyOffice 编辑后回调保存）。
     * 行为：覆盖 MinIO 上 {@link KbFile#getObjectKey()} 指向的对象，更新 size / updatedAt。
     * 不修改 file.status，由调用方决定后续是否触发重分片。
     *
     * @return 更新后的 {@link KbFile}
     */
    KbFile replaceOriginalFile(String kbId, String fileId, byte[] newBytes, String contentType);
}
