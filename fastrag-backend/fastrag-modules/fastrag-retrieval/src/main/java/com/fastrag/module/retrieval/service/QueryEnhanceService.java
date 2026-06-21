package com.fastrag.module.retrieval.service;
import java.util.*;
public interface QueryEnhanceService { Map<String,Object> suggest(String query); Map<String,Object> expandSynonyms(String query); Map<String,Object> applyQueryRules(String query); Map<String,Object> expandGraph(String kbId,String query,int depth,int maxEntities); }
