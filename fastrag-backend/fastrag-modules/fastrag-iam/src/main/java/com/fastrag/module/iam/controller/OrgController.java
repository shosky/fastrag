package com.fastrag.module.iam.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.OrgService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*; import java.util.Map;
@RestController @RequestMapping("/api/org") @RequiredArgsConstructor
public class OrgController {
    private final OrgService svc;
    @GetMapping("/tree") public ApiResponse<List<OrgNodeDto>> tree() { return ApiResponse.success(svc.getOrgTree()); }
    @GetMapping("/flat") public ApiResponse<List<Map<String,Object>>> flat() { return ApiResponse.success(svc.getFlatList()); }
    @GetMapping("/departments") public ApiResponse<List<String>> depts() { return ApiResponse.success(svc.getDepartmentNames()); }
    @GetMapping("/{id}/members") public ApiResponse<List<PersonnelDto>> members(@PathVariable String id) { return ApiResponse.success(svc.getDepartmentMembers(id)); }
    @PostMapping public ApiResponse<OrgNodeDto> create(@RequestBody Map<String,String> b) { return ApiResponse.success(svc.createOrg(b.get("name"),b.get("alias"),b.get("parentId"))); }
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable String id,@RequestBody Map<String,String> b) { svc.updateOrg(id,b.get("name"),b.get("alias")); return ApiResponse.success(); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id) { svc.deleteOrg(id); return ApiResponse.success(); }
}
