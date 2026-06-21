package com.fastrag.module.iam.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.iam.model.*;
public interface PersonnelService { PageResult<PersonnelDto> listPersonnel(int page,int pageSize,String keyword); PersonnelDto createPersonnel(PersonnelCreateRequest req); PersonnelDto updatePersonnel(String id,PersonnelCreateRequest req); void assignRole(String userId,String roleId); PersonnelDto findByUsername(String username); }
