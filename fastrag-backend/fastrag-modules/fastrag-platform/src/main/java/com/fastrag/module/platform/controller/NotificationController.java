package com.fastrag.module.platform.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SysNotification;
import com.fastrag.module.platform.mapper.SysNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final SysNotificationMapper mapper;

    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String userId, @RequestParam(required=false) String status) {
        var w=new LambdaQueryWrapper<SysNotification>().orderByDesc(SysNotification::getCreatedAt);
        if(userId!=null) w.eq(SysNotification::getTargetUser,userId);
        if(status!=null) w.eq(SysNotification::getStatus,status);
        return ApiResponse.success(mapper.selectList(w));
    }
    @GetMapping("/unread-count") public ApiResponse<?> unreadCount(@RequestParam(required=false) String userId) {
        var w=new LambdaQueryWrapper<SysNotification>().eq(SysNotification::getStatus,"unread");
        if(userId!=null) w.eq(SysNotification::getTargetUser,userId);
        return ApiResponse.success(mapper.selectCount(w));
    }
    @PostMapping public ApiResponse<?> create(@RequestBody SysNotification n) { n.setCreatedAt(LocalDateTime.now()); mapper.insert(n); return ApiResponse.success(n); }
    @PutMapping("/{id}/read") public ApiResponse<?> markRead(@PathVariable String id) { var n=mapper.selectById(id); if(n!=null){n.setStatus("read");mapper.updateById(n);} return ApiResponse.success(); }
    @PutMapping("/read-all") public ApiResponse<?> markAllRead(@RequestParam(required=false) String userId) {
        var n=new SysNotification(); n.setStatus("read");
        mapper.update(n,new LambdaQueryWrapper<SysNotification>().eq(SysNotification::getStatus,"unread").eq(userId!=null,SysNotification::getTargetUser,userId));
        return ApiResponse.success();
    }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { mapper.deleteById(id); return ApiResponse.success(); }
}
