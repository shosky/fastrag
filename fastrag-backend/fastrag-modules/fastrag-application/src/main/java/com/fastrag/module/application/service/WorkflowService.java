package com.fastrag.module.application.service;
import com.fastrag.module.application.entity.Workflow; import java.util.*;
public interface WorkflowService { List<Workflow> list(); Workflow get(String id); Workflow create(Map<String,Object> form); Workflow update(String id,Map<String,Object> form); void delete(String id); void publish(String id); void addNode(String wfId,String type,Double x,Double y); void deleteNode(String wfId,String nodeId); void addEdge(String wfId,Map<String,Object> edge); void deleteEdge(String wfId,String edgeId); }
