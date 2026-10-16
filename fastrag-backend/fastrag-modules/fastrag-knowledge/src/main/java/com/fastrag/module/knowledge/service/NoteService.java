package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbNote;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
public interface NoteService {
    List<KbNote> list(String kbId,String keyword);
    KbNote get(String id);
    KbNote create(KbNote note);
    KbNote update(String id,KbNote note);
    void delete(String id);
    /** 导出笔记为 CSV 文件流（与知识采编导出同款写法：BOM + 附件头 + 写流） */
    void exportCsv(String kbId,String ids,HttpServletResponse resp) throws Exception;
    /** 批量导入笔记（JSON 数组），返回导入条数 */
    int importNotes(String kbId,List<KbNote> items);
}
