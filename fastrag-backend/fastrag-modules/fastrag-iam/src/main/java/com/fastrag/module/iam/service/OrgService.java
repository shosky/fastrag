package com.fastrag.module.iam.service;
import com.fastrag.module.iam.model.*; import java.util.*;
public interface OrgService { List<OrgNodeDto> getOrgTree(); List<Map<String,Object>> getFlatList(); List<String> getDepartmentNames(); List<PersonnelDto> getDepartmentMembers(String deptId); OrgNodeDto createOrg(String name,String alias,String parentId); void updateOrg(String id,String name,String alias); void deleteOrg(String id); }
