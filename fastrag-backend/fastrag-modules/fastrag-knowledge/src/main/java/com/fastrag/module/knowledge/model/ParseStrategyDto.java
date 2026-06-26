package com.fastrag.module.knowledge.model;
import lombok.Data; import java.time.LocalDateTime; import java.util.*;
@Data public class ParseStrategyDto { private String id,name,description,parseMethod,llmModel,vlmModel; private List<String> extensions; private boolean isDefault; private Map<String,Object> advanced; private LocalDateTime createdAt,updatedAt; }
