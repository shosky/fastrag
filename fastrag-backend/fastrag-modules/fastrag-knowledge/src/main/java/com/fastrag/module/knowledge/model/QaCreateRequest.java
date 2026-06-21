package com.fastrag.module.knowledge.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class QaCreateRequest { @NotBlank private String question; @NotBlank private String answer; private String source,fileId; }
