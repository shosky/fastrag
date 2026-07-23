package com.fastrag.module.iam.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data;

@Data
public class PermissionCreateRequest {
    @NotBlank private String permKey;
    @NotBlank private String name;
    @NotBlank private String type;   // menu / action
    private String group;
    private String parentKey;
    private String category; // menu / page_action / api
    private String description;
}
