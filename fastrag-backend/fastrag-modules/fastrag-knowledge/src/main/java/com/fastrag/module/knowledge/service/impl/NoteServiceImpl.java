package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbNote; import com.fastrag.module.knowledge.mapper.KbNoteMapper;
import com.fastrag.module.knowledge.service.NoteService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.util.*;
@Slf4j @Service @RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {
    private final KbNoteMapper mapper;
    @Override public List<KbNote> list(String kbId,String keyword) {
        var w=new LambdaQueryWrapper<KbNote>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbNote::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbNote::getTitle,keyword);
        return mapper.selectList(w.orderByDesc(KbNote::getCreatedAt));
    }
    @Override public KbNote get(String id) { return mapper.selectById(id); }
    @Override public KbNote create(KbNote note) { mapper.insert(note); return note; }
    @Override public KbNote update(String id,KbNote note) { note.setId(id); mapper.updateById(note); return mapper.selectById(id); }
    @Override public void delete(String id) { mapper.deleteById(id); }
    // 导出为 CSV 文件流（真实文件下载）：支持按 ids 勾选导出，无 ids 时全量导出
    @Override public void exportCsv(String kbId,String ids,HttpServletResponse resp) throws Exception {
        var all=list(kbId,null);
        if(ids!=null&&!ids.isEmpty()){
            var idSet=Arrays.asList(ids.split(","));
            all=all.stream().filter(n->idSet.contains(n.getId())).toList();
        }
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition","attachment;filename=knowledge_notes.csv");
        var w=new java.io.PrintWriter(resp.getWriter());
        w.write(0xFEFF); // UTF-8 BOM，避免 Excel 打开中文乱码
        w.println("标题,内容,关联类型,关联ID,标签,创建者,创建时间");
        for(var n:all) w.printf("%s,%s,%s,%s,%s,%s,%s%n",
            csv(n.getTitle()), csv(n.getContent()), csv(n.getTargetType()), csv(n.getTargetId()),
            csv(n.getTags()), csv(n.getCreatedBy()), n.getCreatedAt());
        w.flush();
    }
    // 批量导入笔记：标题为空的行跳过，tags 归一化由实体 setTags 负责
    @Override public int importNotes(String kbId,List<KbNote> items) {
        int imported=0;
        if(items==null) return 0;
        for(var item:items){
            if(item==null||item.getTitle()==null||item.getTitle().isBlank()) continue;
            item.setId(null); item.setKbId(kbId); item.setCreatedAt(null);
            mapper.insert(item); imported++;
        }
        return imported;
    }
    private static String csv(String v){ if(v==null) return ""; return "\""+v.replace("\"","\"\"")+"\""; }
}
