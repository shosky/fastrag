package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.KBRole;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.KbAclDto; import com.fastrag.module.iam.service.KbAclService;
import lombok.RequiredArgsConstructor; import org.springframework.data.redis.core.StringRedisTemplate; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*; import java.util.concurrent.TimeUnit; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class KbAclServiceImpl implements KbAclService {
    private final KbAclMapper aclMapper; private final SysUserMapper userMapper; private final StringRedisTemplate redis;
    @Override public List<KbAclDto> getKbAcl(String kbId) {
        return aclMapper.selectList(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getKbId,kbId)).stream().map(e->{
            var d=new KbAclDto(); d.setId(e.getId()); d.setKbId(e.getKbId()); d.setUserId(e.getUserId());
            d.setKbRole(KBRole.valueOf(e.getKbRole())); d.setGrantedBy(e.getGrantedBy()); d.setGrantedAt(e.getGrantedAt());
            var u=userMapper.selectById(e.getUserId()); d.setUserName(u!=null?u.getRealName():e.getUserId()); return d;
        }).collect(Collectors.toList());
    }
    @Override public void setKbAcl(String kbId,List<KbAclDto> entries) { aclMapper.delete(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getKbId,kbId)); entries.forEach(e->addAclEntry(kbId,e.getUserId(),e.getKbRole(),e.getGrantedBy())); }
    @Override public void addAclEntry(String kbId,String userId,KBRole role,String grantedBy) {
        aclMapper.delete(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getKbId,kbId).eq(KbAcl::getUserId,userId));
        var a=new KbAcl(); a.setKbId(kbId); a.setUserId(userId); a.setKbRole(role.name()); a.setGrantedBy(grantedBy); a.setGrantedAt(LocalDateTime.now()); aclMapper.insert(a);
        redis.opsForValue().set("kb:acl:"+kbId+":"+userId,role.name(),24,TimeUnit.HOURS);
    }
    @Override public void removeAclEntry(String kbId,String userId) { aclMapper.delete(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getKbId,kbId).eq(KbAcl::getUserId,userId)); redis.delete("kb:acl:"+kbId+":"+userId); }
    @Override public List<String> getAccessibleKbIds(String userId) { return aclMapper.selectList(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getUserId,userId)).stream().map(KbAcl::getKbId).distinct().collect(Collectors.toList()); }
    @Override public KBRole getKbRole(String userId,String kbId) {
        String c=redis.opsForValue().get("kb:acl:"+kbId+":"+userId); if(c!=null)return KBRole.valueOf(c);
        var a=aclMapper.selectOne(new LambdaQueryWrapper<KbAcl>().eq(KbAcl::getKbId,kbId).eq(KbAcl::getUserId,userId));
        if(a!=null){redis.opsForValue().set("kb:acl:"+kbId+":"+userId,a.getKbRole(),24,TimeUnit.HOURS);return KBRole.valueOf(a.getKbRole());} return null;
    }
}
