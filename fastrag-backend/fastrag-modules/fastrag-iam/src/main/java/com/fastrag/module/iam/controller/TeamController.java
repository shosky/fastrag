package com.fastrag.module.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.iam.entity.SysTeam;
import com.fastrag.module.iam.entity.SysTeamMember;
import com.fastrag.module.iam.entity.SysUser;
import com.fastrag.module.iam.mapper.SysTeamMapper;
import com.fastrag.module.iam.mapper.SysTeamMemberMapper;
import com.fastrag.module.iam.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final SysTeamMapper teamMapper;
    private final SysTeamMemberMapper memberMapper;
    private final SysUserMapper userMapper;

    @GetMapping
    public ApiResponse<?> list() {
        try {
            List<SysTeam> teams = teamMapper.selectList(
                    new LambdaQueryWrapper<SysTeam>().orderByDesc(SysTeam::getCreatedAt)
            );
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (SysTeam t : teams) {
                long memberCount = memberMapper.selectCount(
                        new LambdaQueryWrapper<SysTeamMember>().eq(SysTeamMember::getTeamId, t.getId())
                );
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", t.getId());
                map.put("name", t.getName() != null ? t.getName() : "");
                map.put("description", t.getDescription() != null ? t.getDescription() : "");
                map.put("memberCount", memberCount);
                result.add(map);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.serverError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        SysTeam team = teamMapper.selectById(id);
        if (team == null) return ApiResponse.notFound("团队不存在");
        return ApiResponse.success(team);
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, String> body) {
        SysTeam team = new SysTeam();
        team.setName(body.get("name"));
        team.setDescription(body.get("description"));
        teamMapper.insert(team);
        return ApiResponse.success(team);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        SysTeam team = teamMapper.selectById(id);
        if (team == null) return ApiResponse.notFound("团队不存在");
        team.setName(body.getOrDefault("name", team.getName()));
        team.setDescription(body.getOrDefault("description", team.getDescription()));
        teamMapper.updateById(team);
        return ApiResponse.success(team);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        teamMapper.deleteById(id);
        // 同时删除团队成员
        memberMapper.delete(new LambdaQueryWrapper<SysTeamMember>().eq(SysTeamMember::getTeamId, id));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<?> getMembers(@PathVariable String id) {
        List<SysTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<SysTeamMember>().eq(SysTeamMember::getTeamId, id)
        );
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SysTeamMember m : members) {
            SysUser user = userMapper.selectById(m.getUserId());
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", m.getUserId());
            map.put("name", user != null && user.getRealName() != null ? user.getRealName() : "");
            map.put("username", user != null && user.getUsername() != null ? user.getUsername() : "");
            result.add(map);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/members")
    public ApiResponse<?> addMember(@PathVariable String id, @RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        if (userId == null) return ApiResponse.badRequest("用户ID不能为空");
        // 检查是否已存在
        Long exists = memberMapper.selectCount(
                new LambdaQueryWrapper<SysTeamMember>()
                        .eq(SysTeamMember::getTeamId, id)
                        .eq(SysTeamMember::getUserId, userId)
        );
        if (exists > 0) return ApiResponse.badRequest("该用户已在团队中");
        SysTeamMember member = new SysTeamMember();
        member.setTeamId(id);
        member.setUserId(userId);
        memberMapper.insert(member);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<?> removeMember(@PathVariable String id, @PathVariable String userId) {
        memberMapper.delete(
                new LambdaQueryWrapper<SysTeamMember>()
                        .eq(SysTeamMember::getTeamId, id)
                        .eq(SysTeamMember::getUserId, userId)
        );
        return ApiResponse.success();
    }
}
