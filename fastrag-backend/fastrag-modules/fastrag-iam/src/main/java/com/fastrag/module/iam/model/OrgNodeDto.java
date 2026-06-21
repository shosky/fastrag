package com.fastrag.module.iam.model;
import lombok.Data; import java.util.List;
@Data public class OrgNodeDto { private String id,name,alias,parentId; private Integer level,memberCount; private List<OrgNodeDto> children; }
