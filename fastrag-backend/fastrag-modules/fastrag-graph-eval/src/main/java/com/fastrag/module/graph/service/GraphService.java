package com.fastrag.module.graph.service;
import java.util.*;
public interface GraphService { Map<String,Object> getGraphData(String kbId,Integer maxNodes,Boolean excludeChunks); Map<String,Object> getGraphStats(String kbId); Map<String,Object> getIndexStatus(String kbId); void buildIndex(String kbId,String mode,List<String> fileIds); Map<String,Object> getBuildStatus(String kbId); Map<String,Object> getSettings(String kbId); void saveSettings(String kbId,Map<String,Object> settings); }
