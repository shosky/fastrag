package com.fastrag.module.iam.model;
import com.fastrag.common.enums.KBRole; import lombok.Data; import java.time.LocalDateTime;
@Data public class KbAclDto { private Long id; private String kbId,userId,userName,grantedBy; private KBRole kbRole; private LocalDateTime grantedAt; }
