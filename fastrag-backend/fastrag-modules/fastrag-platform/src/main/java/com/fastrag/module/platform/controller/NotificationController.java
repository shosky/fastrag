package com.fastrag.module.platform.controller;

/**
 * 系统通知管理控制器
 * <p>
 * 提供系统通知消息的查询、创建、已读标记和删除功能。
 * 支持按用户和状态过滤通知列表，并提供未读消息计数接口。
 * 该控制器直接使用 {@link com.fastrag.module.platform.mapper.SysNotificationMapper} 进行数据操作，未抽象 Service 层。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>GET /api/notifications — 查询通知列表，支持按 userId 和 status 过滤，按创建时间倒序排列</li>
 *   <li>GET /api/notifications/unread-count — 获取未读通知数量，支持按 userId 过滤</li>
 *   <li>POST /api/notifications — 创建通知消息，自动设置创建时间</li>
 *   <li>PUT /api/notifications/{id}/read — 标记单条通知为已读</li>
 *   <li>PUT /api/notifications/read-all — 批量标记所有未读通知为已读，支持按 userId 过滤</li>
 *   <li>DELETE /api/notifications/{id} — 删除通知</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.entity.SysNotification
 * @see com.fastrag.module.platform.mapper.SysNotificationMapper
 */
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
