package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface ParseStrategyService {
    List<ParseStrategyDto> list(String kbId);
    ParseStrategyDto get(String kbId, String id);
    ParseStrategyDto create(String kbId, ParseStrategyRequest req);
    ParseStrategyDto update(String kbId, String id, ParseStrategyRequest req);
    void delete(String kbId, String id);
    void setDefault(String kbId, String id);
    ParseStrategyDto resolveByExtension(String kbId, String ext);
    List<String> detectConflicts(String kbId, List<String> extensions, String excludeId);

    /** 保存文件级专属自定义策略（upsert，绑定 file_id，不出现在策略管理列表） */
    ParseStrategyDto saveFileStrategy(String kbId, String fileId, ParseStrategyRequest req);

    /** 删除文件级专属策略（文件删除时级联清理） */
    void deleteFileStrategy(String kbId, String fileId);
}
