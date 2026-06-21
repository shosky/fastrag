package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.OrgService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {
    private final SysOrgMapper orgMapper; private final SysUserMapper userMapper;
    @Override public List<OrgNodeDto> getOrgTree() {
        var all=orgMapper.selectList(new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getSort));
        var bp=all.stream().collect(Collectors.groupingBy(o->o.getParentId()!=null?o.getParentId():"root"));
        return buildTree(bp,"root");
    }
    private List<OrgNodeDto> buildTree(Map<String,List<SysOrg>> bp,String pid) {
        return bp.getOrDefault(pid,List.of()).stream().map(o->{
            var d=new OrgNodeDto(); d.setId(o.getId()); d.setName(o.getName()); d.setAlias(o.getAlias());
            d.setParentId(o.getParentId()); d.setLevel(o.getLevel());
            d.setMemberCount(userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId,o.getId())).intValue());
            d.setChildren(buildTree(bp,o.getId())); return d;
        }).collect(Collectors.toList());
    }
    @Override public List<Map<String,Object>> getFlatList() {
        var result=new ArrayList<Map<String,Object>>();
        orgMapper.selectList(null).forEach(o->{
            var m=new HashMap<String,Object>(); m.put("id",o.getId()); m.put("name",o.getName()); m.put("level",o.getLevel());
            result.add(m);
        });
        return result;
    }
    @Override public List<String> getDepartmentNames() { return orgMapper.selectList(null).stream().map(SysOrg::getName).collect(Collectors.toList()); }
    @Override public List<PersonnelDto> getDepartmentMembers(String deptId) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId,deptId)).stream().map(u->{
            var d=new PersonnelDto(); d.setId(u.getId()); d.setUsername(u.getUsername()); d.setRealName(u.getRealName()); return d;
        }).collect(Collectors.toList());
    }
    @Override public OrgNodeDto createOrg(String name,String alias,String parentId) {
        var o=new SysOrg(); o.setName(name); o.setAlias(alias); o.setParentId(parentId!=null?parentId:"root"); o.setLevel(parentId!=null?2:1); o.setSort(0); orgMapper.insert(o);
        var d=new OrgNodeDto(); d.setId(o.getId()); d.setName(o.getName()); d.setChildren(List.of()); return d;
    }
    @Override public void updateOrg(String id,String name,String alias) { var o=orgMapper.selectById(id); if(o==null)throw BusinessException.notFound("组织不存在"); o.setName(name); o.setAlias(alias); orgMapper.updateById(o); }
    @Override public void deleteOrg(String id) { if(orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getParentId,id))>0)throw BusinessException.badRequest("存在子组织"); orgMapper.deleteById(id); }
}
