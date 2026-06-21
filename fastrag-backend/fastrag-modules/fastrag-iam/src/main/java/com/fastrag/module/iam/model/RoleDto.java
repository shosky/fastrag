package com.fastrag.module.iam.model;
import lombok.Data; import java.time.LocalDateTime; import java.util.List;
@Data public class RoleDto { private String id,roleKey,name,description; private boolean isDefault,isSystem; private List<String> permissions; private LocalDateTime createdAt,updatedAt; }
