package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginRequest {
    /** 小程序端 wx.login 获取的 code */
    @NotBlank(message = "code 不能为空")
    private String code;

    /** 用户昵称（小程序端用户信息，可选） */
    private String nickName;

    /** 用户头像 URL（可选） */
    private String avatarUrl;
}
