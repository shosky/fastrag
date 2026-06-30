package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledgeEdit; import com.fastrag.module.knowledge.mapper.KbKnowledgeEditMapper;
import com.fastrag.module.knowledge.service.KnowledgeEditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeEditServiceImpl implements KnowledgeEditService {
    private final KbKnowledgeEditMapper mapper;
    @Override public List<KbKnowledgeEdit> list(String kbId,String status,String editor) {
        var w=new LambdaQueryWrapper<KbKnowledgeEdit>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeEdit::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgeEdit::getStatus,status);
        if(editor!=null&&!editor.isEmpty()) w.eq(KbKnowledgeEdit::getEditor,editor);
        return mapper.selectList(w.orderByDesc(KbKnowledgeEdit::getCreatedAt));
    }
    @Override public KbKnowledgeEdit get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgeEdit create(KbKnowledgeEdit edit) {
        if(edit.getStatus()==null) edit.setStatus("draft");
        if(edit.getVersion()==null) edit.setVersion(1);
        if(edit.getEditType()==null) edit.setEditType("create");
        if(edit.getTags()!=null&&edit.getTags().isEmpty()) edit.setTags(null);
        mapper.insert(edit); return edit;
    }
    @Override public KbKnowledgeEdit update(String id,KbKnowledgeEdit edit) {
        edit.setId(id);
        if(edit.getTags()!=null&&edit.getTags().isEmpty()) edit.setTags(null);
        mapper.updateById(edit); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public KbKnowledgeEdit submit(String id) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("submitted"); mapper.updateById(e); }
        return e;
    }
    @Override public KbKnowledgeEdit approve(String id,String reviewer) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("approved"); e.setReviewer(reviewer); mapper.updateById(e); }
        return e;
    }
    @Override public KbKnowledgeEdit reject(String id,String reviewer,String comment) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("rejected"); e.setReviewer(reviewer); e.setReviewComment(comment); mapper.updateById(e); }
        return e;
    }
    @Override public Map<String,Object> export(String kbId,String ids,String status,String editor) {
        var all=list(kbId,status,editor);
        if(ids!=null&&!ids.isEmpty()){
            var idSet=Arrays.asList(ids.split(","));
            all=all.stream().filter(e->idSet.contains(e.getId())).toList();
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("format","json");
        result.put("count",all.size());
        result.put("items",all);
        return result;
    }
    // 生成 CSV 文件流导出；字段顺序与列与前端原 CSV 保持一致
    @Override public void exportCsv(String kbId,String ids,String status,String editor,HttpServletResponse resp) throws Exception {
        var all=list(kbId,status,editor);
        if(ids!=null&&!ids.isEmpty()){
            var idSet=Arrays.asList(ids.split(","));
            all=all.stream().filter(e->idSet.contains(e.getId())).toList();
        }
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition","attachment;filename=knowledge_edits.csv");
        var w=new java.io.PrintWriter(resp.getWriter());
        w.write(0xFEFF); // UTF-8 BOM，避免 Excel 打开中文乱码
        w.println("标题,类型,状态,编辑者,审核人,驳回原因,版本,标签,创建时间,更新时间");
        for(var e:all) w.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
            csv(e.getTitle()),
            csv(EDIT_TYPE_MAP.getOrDefault(e.getEditType(),e.getEditType())),
            csv(STATUS_MAP.getOrDefault(e.getStatus(),e.getStatus())),
            csv(e.getEditor()),
            csv(e.getReviewer()),
            csv(e.getReviewComment()),
            e.getVersion()==null?"":e.getVersion(),
            csv(e.getTags()),
            e.getCreatedAt(),e.getUpdatedAt());
        w.flush();
    }
    // CSV 字段转义：含逗号、引号、换行时用双引号包裹，内部引号双写
    private static String csv(String v){ if(v==null) return ""; return "\""+v.replace("\"","\"\"")+"\""; }
    private static final Map<String,String> EDIT_TYPE_MAP=Map.of("create","新建","update","更新","merge","合并","split","拆分");
    private static final Map<String,String> STATUS_MAP=Map.of("draft","草稿","submitted","待审核","approved","已通过","rejected","已驳回");
}
