package com.fastrag.module.iam.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class PersonnelCreateRequest { @NotBlank private String username; @NotBlank private String realName; private String phone,email; @NotBlank private String password; private String orgId,roleId; }
