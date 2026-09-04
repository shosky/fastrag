package com.fastrag.module.bpm.dto;
import lombok.Data;

@Data
public class PermissionRequest {
    /** user/role */
    private String subjectType;
    private String subjectId;
    /** view/edit/execute/publish/delete */
    private String permission;
}