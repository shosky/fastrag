package com.fastrag.module.iam.entity;

/**
 * 邮箱验证码实体类，对应数据库表 {@code email_verification}。
 *
 * <p>用于存储用户注册和密码重置场景下的邮箱验证码信息。
 * 系统发送验证码后，将记录写入此表，用户提交验证码时进行匹配校验。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code email} —— 接收验证码的目标邮箱地址</li>
 *   <li>{@code code} —— 6 位随机验证码</li>
 *   <li>{@code purpose} —— 验证码用途，取值为 {@code register}（注册）或 {@code reset}（重置密码）</li>
 *   <li>{@code expiresAt} —— 验证码过期时间</li>
 *   <li>{@code used} —— 是否已使用，防止验证码被重复使用</li>
 * </ul>
 *
 * <p>由 {@code AuthServiceImpl} 在发送验证码时创建记录，在注册或重置密码时校验并标记为已使用。
 *
 * @see com.fastrag.module.iam.service.impl.AuthServiceImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("email_verification")
public class EmailVerification {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String email;
    private String code;
    private String purpose; // register / reset
    private LocalDateTime expiresAt;
    private Boolean used;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
