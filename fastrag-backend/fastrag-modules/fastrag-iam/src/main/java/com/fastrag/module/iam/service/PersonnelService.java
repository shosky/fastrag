package com.fastrag.module.iam.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.iam.model.*;
import java.util.List;

public interface PersonnelService {
    PageResult<PersonnelDto> listPersonnel(int page, int pageSize, String keyword);
    PersonnelDto createPersonnel(PersonnelCreateRequest req);
    PersonnelDto updatePersonnel(String id, PersonnelCreateRequest req);
    void assignRoles(String userId, List<String> roleIds);
    void updateStatus(String userId, String status);
    PersonnelDto findByUsername(String username);
}
