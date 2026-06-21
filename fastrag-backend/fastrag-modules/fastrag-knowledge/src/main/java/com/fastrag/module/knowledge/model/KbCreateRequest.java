package com.fastrag.module.knowledge.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data; import java.util.*;
@Data public class KbCreateRequest { @NotBlank private String name; private String category,description,permission,embeddingModel,parseMode,splitMode; private List<String> tags; private Object fileTypeConfig; private Object retrievalConfig; }
