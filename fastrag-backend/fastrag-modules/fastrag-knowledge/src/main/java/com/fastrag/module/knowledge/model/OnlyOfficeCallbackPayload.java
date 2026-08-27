package com.fastrag.module.knowledge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OnlyOffice Document Server 回调请求体。
 *
 * <p>由 OnlyOffice Document Server 在以下时机以 application/json 方式 POST 到
 * {@code /api/kb/{kbId}/files/{id}/onlyoffice/callback}：
 * <ul>
 *   <li>status = 1：正在编辑（不需保存）</li>
 *   <li>status = 2：编辑完成，准备保存（响应时需告知 OO 是否接受；accept=true 时 OO
 *       会保留打开文件状态、关闭编辑器；accept=false 时继续编辑）</li>
 *   <li>status = 3：保存出错</li>
 *   <li>status = 4：关闭文件，无修改</li>
 *   <li>status = 6：强制保存（无修改）</li>
 *   <li>status = 7：强制保存出错</li>
 * </ul>
 *
 * <p>服务端响应 {@code {"error": 0, "actions": [...]}} 表示接受，{@code error: 1} 表示拒绝。
 *
 * <p>字段说明（OnlyOffice 官方约定）：
 * <ul>
 *   <li>{@link #status} — 见上方枚举</li>
 *   <li>{@link #url} — 仅 status=2 时有效，OO 把已保存的文档临时放在该 URL 上，
 *       FastRAG 需主动 HTTP GET 拉取后存到 MinIO</li>
 *   <li>{@link #key} — 与 config.document.key 一致，防止伪造回调替换文件</li>
 *   <li>{@link #token} — OO 用同一密钥签名的回调 token（防重放）</li>
 *   <li>{@link #users} — 当前编辑协作者列表（feature only，暂忽略）</li>
 *   <li>{@link #history} — OO 历史版本（feature only，暂忽略）</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallbackPayload {

    /** 整数状态码：1/2/3/4/6/7 */
    private Integer status;

    /** status=2 时 OO 提供的"已保存文件"下载 URL（短时有效） */
    private String url;

    /** 文档唯一 key，与 config.document.key 一致 */
    private String key;

    /** 文档类型字符串：word / cell / slide */
    private String type;

    /** OO 用相同 JWT 密钥签名的回调 token */
    private String token;

    /** 文档 base64 文件名（OO 内部使用，前端显示可读） */
    private String title;

    /** 当前编辑用户列表 */
    private List<String> users;

    /** OO 历史版本信息（status=2 时） */
    private Map<String, Object> history;

    /** status=2 时附带的用户操作类型 */
    @JsonProperty("actionType")
    private Integer actionType;

    /** status=2 时是否需要后续协作编辑 */
    @JsonProperty("actions")
    private List<Map<String, Object>> actions;

    /** 兜底：OO 可能用其它字段 */
    @JsonProperty("userdata")
    private String userdata;
}
