package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbKnowledgeEdit; import jakarta.servlet.http.HttpServletResponse; import java.util.*;
public interface KnowledgeEditService {
    List<KbKnowledgeEdit> list(String kbId,String status,String editor);
    KbKnowledgeEdit get(String id);
    KbKnowledgeEdit create(KbKnowledgeEdit edit);
    KbKnowledgeEdit update(String id,KbKnowledgeEdit edit);
    void delete(String id);
    KbKnowledgeEdit submit(String id);
    KbKnowledgeEdit approve(String id,String reviewer);
    KbKnowledgeEdit reject(String id,String reviewer,String comment);
    Map<String,Object> export(String kbId,String ids,String status,String editor);
    // 生成 CSV 文件流导出（支持按 ids 勾选导出），与项目内 exportDocs/exportReviewRecords 范式一致
    void exportCsv(String kbId,String ids,String status,String editor,HttpServletResponse resp) throws Exception;
}
