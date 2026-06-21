package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface ParseStrategyService { List<ParseStrategyDto> list(String kbId); ParseStrategyDto get(String kbId,String id); ParseStrategyDto create(String kbId,ParseStrategyRequest req); ParseStrategyDto update(String kbId,String id,ParseStrategyRequest req); void delete(String kbId,String id); void setDefault(String kbId,String id); ParseStrategyDto resolveByExtension(String kbId,String ext); List<String> detectConflicts(String kbId,List<String> extensions,String excludeId); }
