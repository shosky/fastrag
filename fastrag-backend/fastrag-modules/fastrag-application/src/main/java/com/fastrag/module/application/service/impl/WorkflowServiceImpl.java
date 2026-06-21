package com.fastrag.module.application.service.impl;
import cn.hutool.json.JSONUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.entity.Workflow; import com.fastrag.module.application.mapper.WorkflowMapper;
import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {
    private final WorkflowMapper mapper;
    @Override public List<Workflow> list() { return mapper.selectList(null); }
    @Override public Workflow get(String id) { return mapper.selectById(id); }
    @Override public Workflow create(Map<String,Object> f) { var w=new Workflow(); w.setName((String)f.get("name")); w.setDescription((String)f.get("description")); w.setStatus("draft"); w.setNodes("[]"); w.setEdges("[]"); mapper.insert(w); return w; }
    @Override public Workflow update(String id,Map<String,Object> f) { var w=mapper.selectById(id); if(w!=null){if(f.containsKey("name"))w.setName((String)f.get("name")); if(f.containsKey("nodes"))w.setNodes(JSONUtil.toJsonStr(f.get("nodes"))); if(f.containsKey("edges"))w.setEdges(JSONUtil.toJsonStr(f.get("edges"))); mapper.updateById(w);} return w; }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void publish(String id) { var w=mapper.selectById(id); if(w!=null){w.setStatus("published");mapper.updateById(w);} }
    @Override public void addNode(String wfId,String type,Double x,Double y) { }
    @Override public void deleteNode(String wfId,String nodeId) { }
    @Override public void addEdge(String wfId,Map<String,Object> edge) { }
    @Override public void deleteEdge(String wfId,String edgeId) { }
}
