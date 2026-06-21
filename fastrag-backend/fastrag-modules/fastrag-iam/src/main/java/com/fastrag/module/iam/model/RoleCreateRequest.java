package com.fastrag.module.iam.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data; import java.util.List;
@Data public class RoleCreateRequest { @NotBlank private String name; private String description; private List<String> permissions; }
