package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.OnlyOfficeCallbackPayload;

import java.util.Map;

/**
 * OnlyOffice Document Server 集成服务。
 *
 * <p>封装 OnlyOffice 编辑器与 FastRAG 文件系统之间的协议：
 * <ul>
 *   <li>构造编辑器配置（{@code config.token} 签名 + document.url + callbackUrl）</li>
 *   <li>处理 OnlyOffice 回调（status=2/6 触发重保存与重分片）</li>
 *   <li>为 document.url 签发下载 token（避免 @KbAuth 必须携带 user JWT）</li>
 *   <li>在编辑器保存后清理旧 chunk / Milvus / 图谱，并重跑完整 ingestion 流水线</li>
 * </ul>
 *
 * <p>线程模型：{@link #buildEditorConfig} 和 {@link #handleCallback} 均为请求内同步调用，
 * callback 内部触发 {@code reChunkFile} 后**不等 MQ 异步执行完成**直接返回
 * {@code {error: 0}}，避免阻塞 OnlyOffice Document Server 的回调线程。
 */
public interface OnlyOfficeService {

    /**
     * 构建 OnlyOffice 编辑器配置（包含 OO config.token 签名）。
     *
     * @param kbId   知识库 ID（用于 URL 拼接）
     * @param fileId 文件 ID
     * @param userId 当前用户 ID（用于 OO editorConfig.user.id）
     * @param userName 当前用户显示名
     * @param editable 是否可编辑（viewer 角色时为 false）
     * @return 包含 document / editorConfig / events / token 等字段的 Map，可直接 JSON 序列化
     */
    Map<String, Object> buildEditorConfig(String kbId, String fileId, String userId, String userName, boolean editable);

    /**
     * 生成 document.url 查询参数 token（用于 OO 服务端拉原始文件时附 {@code ?token=…}）。
     */
    String signRawFileToken(String kbId, String fileId);

    /**
     * 处理 OnlyOffice Document Server 回调。
     *
     * @return OnlyOffice 标准响应体：{@code {error: 0/1, actions: [...]}}
     */
    Map<String, Object> handleCallback(String kbId, String fileId, OnlyOfficeCallbackPayload payload);

    /**
     * 业务级：判断文件是否启用 OnlyOffice（按扩展名 + KB 设置）。
     */
    boolean isSupportedFile(String fileName);

    /**
     * 生成 document.key（基于 fileId + updatedAt，文件变更后 key 改变，
     * OO 强制 reload 编辑后的版本，避免浏览器缓存）。
     */
    String buildDocumentKey(String fileId, java.time.LocalDateTime updatedAt);

    /**
     * 对当前活跃编辑会话执行 forcesave（页面「保存」按钮）。
     *
     * <p>通过 OO CommandService（{@code c=forcesave}）触发：OO 收到命令后回调
     * /onlyoffice/callback（status=6），由 {@link #handleCallback} 完成落盘 + 重分片。
     *
     * @return result 字段：initiated（已发起）/ no-changes（无修改）/ no-session（会话不存在）
     *         / no-file / disabled / failed
     */
    Map<String, Object> forceSave(String kbId, String fileId);
}
