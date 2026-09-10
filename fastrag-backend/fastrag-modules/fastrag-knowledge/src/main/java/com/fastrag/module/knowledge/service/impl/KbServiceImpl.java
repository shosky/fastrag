package com.fastrag.module.knowledge.service.impl;
import cn.hutool.core.util.StrUtil; import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.module.knowledge.entity.KbChunk; import com.fastrag.module.knowledge.entity.KbFile; import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper; import com.fastrag.module.knowledge.mapper.KbFileMapper; import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.*; import com.fastrag.module.knowledge.service.KbService; import com.fastrag.module.knowledge.service.FileService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*; import java.util.stream.Collectors;
@Slf4j @Service @RequiredArgsConstructor
public class KbServiceImpl implements KbService {
    private final KnowledgeBaseMapper mapper; private final KbFileMapper fileMapper; private final KbChunkMapper chunkMapper; private final FileService fileService;
    @Override public Map<String,Object> list(String kw,String cat,int page,int pageSize) {
        var w=new LambdaQueryWrapper<KnowledgeBase>();
        if(StrUtil.isNotBlank(kw)) w.like(KnowledgeBase::getName,kw).or().like(KnowledgeBase::getDescription,kw);
        if(StrUtil.isNotBlank(cat)) w.eq(KnowledgeBase::getCategory,cat);
        w.orderByDesc(KnowledgeBase::getCreatedAt);
        var r=mapper.selectPage(new Page<>(page,pageSize),w);
        var result=new HashMap<String,Object>();
        result.put("list",r.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        result.put("total",r.getTotal()); result.put("page",page); result.put("pageSize",pageSize);
        return result;
    }
    @Override public KbDto get(String id) { var e=mapper.selectById(id); if(e==null)throw new RuntimeException("KB not found: "+id); return toDto(e); }
    @Override public KbDto create(KbCreateRequest req,String creator) {
        var e=new KnowledgeBase(); e.setName(req.getName()); e.setCategory(req.getCategory()); e.setDescription(req.getDescription());
        e.setTags(req.getTags()!=null?JSONUtil.toJsonStr(req.getTags()):null); e.setPermission(req.getPermission()!=null?req.getPermission():"private");
        e.setEmbeddingModel(req.getEmbeddingModel()); e.setParseMode(req.getParseMode()); e.setSplitMode(req.getSplitMode());
        e.setFileTypeConfig(req.getFileTypeConfig()!=null?JSONUtil.toJsonStr(req.getFileTypeConfig()):null);
        e.setRetrievalConfig(req.getRetrievalConfig()!=null?JSONUtil.toJsonStr(req.getRetrievalConfig()):null);
        e.setCreator(creator); e.setUsedSize(0L); e.setTotalSize(0L); e.setType("personal"); mapper.insert(e); return toDto(e);
    }
    @Override public KbDto update(String id,KbCreateRequest req) {
        var e=mapper.selectById(id); if(e==null)throw new RuntimeException("KB not found: "+id);
        if(req.getName()!=null)e.setName(req.getName()); if(req.getCategory()!=null)e.setCategory(req.getCategory());
        if(req.getDescription()!=null)e.setDescription(req.getDescription());
        if(req.getTags()!=null)e.setTags(JSONUtil.toJsonStr(req.getTags()));
        if(req.getEmbeddingModel()!=null)e.setEmbeddingModel(req.getEmbeddingModel());
        if(req.getParseMode()!=null)e.setParseMode(req.getParseMode());
        if(req.getSplitMode()!=null)e.setSplitMode(req.getSplitMode());
        if(req.getPermission()!=null)e.setPermission(req.getPermission());
        if(req.getFileTypeConfig()!=null)e.setFileTypeConfig(JSONUtil.toJsonStr(req.getFileTypeConfig()));
        if(req.getRetrievalConfig()!=null)e.setRetrievalConfig(JSONUtil.toJsonStr(req.getRetrievalConfig()));
        mapper.updateById(e); return toDto(e);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
	    @Override public List<Map<String,Object>> getCategories() {
	        var all=mapper.selectList(null);
	        var grouped=all.stream().filter(e->StrUtil.isNotBlank(e.getCategory())).collect(Collectors.groupingBy(KnowledgeBase::getCategory,Collectors.counting()));
	        var result=new ArrayList<Map<String,Object>>();
	        grouped.forEach((k,v)->{ var m=new HashMap<String,Object>(); m.put("id",k); m.put("name",k); m.put("count",v); result.add(m); });
	        return result;
	    }
    // ===== 知识库导出 / 导入 =====
    @Override public Map<String,Object> exportKb(String id) {
        var e=mapper.selectById(id); if(e==null) throw new RuntimeException("KB not found: "+id);
        var kb=new LinkedHashMap<String,Object>();
        kb.put("name",e.getName()); kb.put("category",e.getCategory()); kb.put("description",e.getDescription());
        kb.put("tags",StrUtil.isNotBlank(e.getTags())?JSONUtil.toList(e.getTags(),String.class):null);
        kb.put("permission",e.getPermission()); kb.put("embeddingModel",e.getEmbeddingModel());
        kb.put("parseMode",e.getParseMode()); kb.put("splitMode",e.getSplitMode());
        kb.put("fileTypeConfig",StrUtil.isNotBlank(e.getFileTypeConfig())?JSONUtil.parse(e.getFileTypeConfig()):null);
        kb.put("retrievalConfig",StrUtil.isNotBlank(e.getRetrievalConfig())?JSONUtil.parse(e.getRetrievalConfig()):null);
        // 文件按 chunk 顺序还原为全文文本，导入后可重新走解析/嵌入流水线
        var files=fileMapper.selectList(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId,id).isNull(KbFile::getDeletedAt).orderByAsc(KbFile::getCreatedAt));
        var fileArr=new ArrayList<Map<String,Object>>();
        for(var f:files) {
            var fm=new LinkedHashMap<String,Object>();
            fm.put("name",f.getName()); fm.put("category",f.getCategory()); fm.put("extension",f.getExtension()); fm.put("size",f.getSize());
            var chunks=chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId,id).eq(KbChunk::getFileId,f.getId()).orderByAsc(KbChunk::getChunkIndex));
            var sb=new StringBuilder();
            for(var c:chunks) { if(sb.length()>0) sb.append("\n\n"); if(c.getContent()!=null) sb.append(c.getContent()); }
            fm.put("content",sb.toString()); fm.put("chunkCount",chunks.size());
            fileArr.add(fm);
        }
        var r=new LinkedHashMap<String,Object>();
        r.put("version",1); r.put("type","fastrag-kb-export"); r.put("exportedAt",LocalDateTime.now().toString());
        r.put("knowledgeBase",kb); r.put("files",fileArr);
        return r;
    }
    @Override public KbDto importKb(Map<String,Object> data,String creator) {
        if(!(data.get("knowledgeBase") instanceof Map)) throw new RuntimeException("导入文件格式错误：缺少 knowledgeBase");
        @SuppressWarnings("unchecked") var kb=(Map<String,Object>)data.get("knowledgeBase");
        var req=new KbCreateRequest();
        req.setName(kb.get("name")!=null&&StrUtil.isNotBlank(kb.get("name").toString())?kb.get("name").toString():"导入的知识库");
        req.setCategory(kb.get("category")!=null?kb.get("category").toString():null);
        req.setDescription(kb.get("description")!=null?kb.get("description").toString():null);
        req.setPermission(kb.get("permission")!=null?kb.get("permission").toString():null);
        req.setEmbeddingModel(kb.get("embeddingModel")!=null?kb.get("embeddingModel").toString():null);
        req.setParseMode(kb.get("parseMode")!=null?kb.get("parseMode").toString():null);
        req.setSplitMode(kb.get("splitMode")!=null?kb.get("splitMode").toString():null);
        if(kb.get("tags") instanceof List) { @SuppressWarnings("unchecked") var tags=((List<Object>)kb.get("tags")).stream().map(String::valueOf).collect(Collectors.toList()); req.setTags(tags); }
        req.setFileTypeConfig(kb.get("fileTypeConfig")); req.setRetrievalConfig(kb.get("retrievalConfig"));
        var created=create(req,creator);
        int imported=0,skipped=0;
        if(data.get("files") instanceof List) {
            for(Object o:(List<Object>)data.get("files")) {
                if(!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked") var m=(Map<String,Object>)o;
                var content=m.get("content")!=null?m.get("content").toString():"";
                if(StrUtil.isBlank(content)) { skipped++; continue; }
                var name=m.get("name")!=null&&StrUtil.isNotBlank(m.get("name").toString())?m.get("name").toString():"imported-"+(imported+1)+".txt";
                try {
                    var f=fileService.uploadText(created.getId(),name,content);
                    fileService.process(created.getId(),f.getId());
                    imported++;
                } catch (Exception ex) { log.warn("导入文件失败: {}",name,ex); skipped++; }
            }
        }
        log.info("知识库导入完成: name={}, files={}, skipped={}",created.getName(),imported,skipped);
        return created;
    }
    private KbDto toDto(KnowledgeBase e) { var d=new KbDto(); d.setId(e.getId()); d.setName(e.getName()); d.setDescription(e.getDescription()); d.setCategory(e.getCategory()); d.setTags(StrUtil.isNotBlank(e.getTags())?JSONUtil.toList(e.getTags(),String.class):null); d.setEmbeddingModel(e.getEmbeddingModel()); d.setDimension(e.getDimension()); d.setCreator(e.getCreator()); d.setCreatedAt(e.getCreatedAt()); d.setUsedSize(e.getUsedSize()); d.setTotalSize(e.getTotalSize()); d.setType(e.getType()); d.setParseMode(e.getParseMode()); d.setSplitMode(e.getSplitMode()); d.setPermission(e.getPermission()); d.setFileTypeConfig(StrUtil.isNotBlank(e.getFileTypeConfig())?JSONUtil.parse(e.getFileTypeConfig()):null); d.setRetrievalConfig(StrUtil.isNotBlank(e.getRetrievalConfig())?JSONUtil.parse(e.getRetrievalConfig()):null); return d; }
}
