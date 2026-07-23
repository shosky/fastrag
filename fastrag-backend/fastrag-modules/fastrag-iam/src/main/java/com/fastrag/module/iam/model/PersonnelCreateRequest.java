package com.fastrag.module.iam.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data; import java.util.List;

@Data
public class PersonnelCreateRequest {
    @NotBlank private String username;
    @NotBlank private String realName;
    private String phone, email, password, orgId;
    private List<String> roleIds;
}
