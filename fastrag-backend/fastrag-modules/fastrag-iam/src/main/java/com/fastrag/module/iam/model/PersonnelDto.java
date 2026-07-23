package com.fastrag.module.iam.model;
import lombok.Data; import java.time.LocalDateTime; import java.util.List;

@Data
public class PersonnelDto {
    private String id, username, realName, phone, email, orgName, status;
    private List<String> roleIds;
    private List<String> roleNames;
    private LocalDateTime createdAt;
}
