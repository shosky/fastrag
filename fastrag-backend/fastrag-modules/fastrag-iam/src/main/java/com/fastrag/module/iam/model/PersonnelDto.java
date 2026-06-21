package com.fastrag.module.iam.model;
import lombok.Data; import java.time.LocalDateTime;
@Data public class PersonnelDto { private String id,username,realName,phone,email,orgName,roleId,roleName,status; private LocalDateTime createdAt; }
