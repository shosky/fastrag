package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatQrConfirmRequest {
    /** 前端生成的扫码场景值 */
    @NotBlank(message = "scene 不能为空")
    private String scene;

    /** 小程序端 wx.login 获取的 code */
    @NotBlank(message = "code 不能为空")
    private String code;

    /** 用户昵称（可选） */
    private String nickName;
}
