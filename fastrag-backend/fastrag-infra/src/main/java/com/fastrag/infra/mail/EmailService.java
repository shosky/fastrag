package com.fastrag.infra.mail;

/**
 * 邮件发送服务，封装基于 Spring Mail 的邮件发送能力。
 *
 * <p>核心职责：提供用户注册验证码和密码重置验证码的邮件发送功能。
 * 所有发送方法均标注 {@code @Async}，通过 Spring 异步线程池执行，不阻塞业务主流程。
 *
 * <p>依赖的外部系统：
 * <ul>
 *   <li>SMTP 邮件服务器：通过 Spring {@link org.springframework.mail.javamail.JavaMailSender} 连接，
 *       配置项来自 {@code application.yml} 中的 {@code spring.mail.*} 前缀（host、port、username、password 等）</li>
 * </ul>
 *
 * <p>提供的核心能力：
 * <ul>
 *   <li>{@code sendRegisterCode} — 发送注册验证码邮件</li>
 *   <li>{@code sendResetPasswordCode} — 发送密码重置验证码邮件</li>
 * </ul>
 *
 * <p>与其他模块的交互：被 fastrag-iam 模块的用户认证服务调用，用于用户注册和密码找回场景。
 */
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 发送邮箱注册验证码
     */
    @Async
    public void sendRegisterCode(String toEmail, String code) {
        String subject = "AIS 智能知识服务平台 — 注册验证码";
        String content = "您好！\n\n" +
                "您正在注册 AIS 智能知识服务平台账号，验证码为：\n\n" +
                "  " + code + "\n\n" +
                "验证码有效期为 5 分钟，请勿泄露给他人。\n\n" +
                "如非本人操作，请忽略此邮件。\n\n" +
                "AIS 团队";
        sendMail(toEmail, subject, content);
    }

    /**
     * 发送密码重置验证码
     */
    @Async
    public void sendResetPasswordCode(String toEmail, String code) {
        String subject = "AIS 智能知识服务平台 — 密码重置验证码";
        String content = "您好！\n\n" +
                "您正在重置 AIS 智能知识服务平台账号的密码，验证码为：\n\n" +
                "  " + code + "\n\n" +
                "验证码有效期为 5 分钟，请勿泄露给他人。\n\n" +
                "如非本人操作，请忽略此邮件并尽快修改密码。\n\n" +
                "AIS 团队";
        sendMail(toEmail, subject, content);
    }

    private void sendMail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败，请检查邮箱配置或稍后重试");
        }
    }
}
