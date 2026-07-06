package com.fastrag.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

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
