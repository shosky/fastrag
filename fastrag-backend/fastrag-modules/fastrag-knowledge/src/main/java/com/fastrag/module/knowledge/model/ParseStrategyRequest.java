package com.fastrag.module.knowledge.model;
import lombok.Data; import java.util.*;
@Data public class ParseStrategyRequest { private String name,description,parseMethod,llmModel,vlmModel; private List<String> extensions; private Map<String,Object> advanced; }
