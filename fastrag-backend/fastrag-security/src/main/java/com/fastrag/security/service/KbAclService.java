package com.fastrag.security.service;
import com.fastrag.common.enums.KBRole; import com.fastrag.security.model.KbAclDto; import java.util.List;
public interface KbAclService { List<KbAclDto> getKbAcl(String kbId); void setKbAcl(String kbId,List<KbAclDto> entries); void addAclEntry(String kbId,String userId,KBRole role,String grantedBy); void removeAclEntry(String kbId,String userId); List<String> getAccessibleKbIds(String userId); KBRole getKbRole(String userId,String kbId); }
