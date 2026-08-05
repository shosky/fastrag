package com.fastrag.module.iam.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatQrScene {
    /** 场景值，小程序通过 scene 参数回传给后端 */
    private String scene;
    /** 二维码过期时间（秒），前端据此倒计时 */
    private int expiresIn;
    /** 小程序码图片（base64 编码，不含 data:image 前缀） */
    private String qrImageBase64;
}
