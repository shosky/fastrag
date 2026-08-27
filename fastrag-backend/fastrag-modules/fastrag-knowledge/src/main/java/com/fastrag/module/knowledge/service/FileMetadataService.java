package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.FileBatchTagRequest;
import com.fastrag.module.knowledge.model.FileMetadataUpdateRequest;
import com.fastrag.module.knowledge.model.FileMetadataVO;

import java.util.List;
import java.util.Map;

/**
 * 文件元数据管理服务（分册四 rag-file-metadata-management.md）。
 *
 * <p>提供文档（文件）级元数据的人工管理能力：查看、录入/编辑、批量打标、存量补抽，
 * 并与检索侧消费形成闭环：写库同时回填 kb_chunk 冗余列（region/publish_date/doc_level）。
 *
 * <p>状态机：人工写入→ metadata_status=revised（metadata_source=manual）；
 * 自动抽取（extractMetadata）仅覆盖 none/partial，不覆盖 revised；见 A7 验收项。</p>
 */
public interface FileMetadataService {

    /** 查看单个文件的完整元数据（固定字段 + 标签 + KB schema + 自定义属性取值 + 状态） */
    FileMetadataVO getMetadata(String kbId, String fileId);

    /** 全量更新固定字段 + 自定义属性取值（写入即视为人工校订：revised/manual）；事务内回填 chunk */
    FileMetadataVO updateMetadata(String kbId, String fileId, FileMetadataUpdateRequest req);

    /** 替换式设置单文件标签（差量维护 kb_tag_relation + usage_count） */
    void setFileTags(String kbId, String fileId, List<String> tagIds);

    /** 批量打标：增量加/删文件标签，返回处理汇总（A4 验收项） */
    Map<String, Object> batchSetTags(String kbId, FileBatchTagRequest req);

    /** 存量补抽：对指定文件（或全库）做规则抽取并回填；仅覆盖 none/partial，force=true 时强制重抽（A8 验收项） */
    Map<String, Object> extractMetadata(String kbId, List<String> fileIds, boolean force);
}