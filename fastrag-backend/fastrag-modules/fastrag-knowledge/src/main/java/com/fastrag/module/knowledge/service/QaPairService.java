package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

public interface QaPairService {
    List<QaPairDto> list(String kbId);
    List<QaPairDto> list(String kbId, String fileId);
    QaPairDto create(String kbId, QaCreateRequest req);
    QaPairDto update(String kbId, String id, Map<String, Object> patch);
    void delete(String kbId, String id);
    void confirm(String kbId, String id);
    /**
     * 批量确认问答对（2026-11-02 REQ-B04：确认状态机的第二条写入路径）。
     *
     * <p>混选容忍：已 confirmed 行幂等跳过、不存在/跨库 id 计入跳过；
     * 全部行单事务整批原子（任一失败全回滚）；单次上限 10000 条。
     *
     * @param kbId 知识库 ID
     * @param ids  待确认 QA 对 id 集合（内部去重）
     * @return 确认数与跳过数
     */
    QaBatchConfirmResult batchConfirm(String kbId, List<String> ids);
    /**
     * 从 Excel 文件批量导入问答对。
     *
     * @param kbId             知识库 ID
     * @param file             上传的 Excel 文件（.xlsx 或 .xls）
     * @param overwrite        是否覆盖已存在的问答对（按问题文本匹配）
     * @return 导入结果，包含成功/跳过/失败统计及逐行明细
     */
    QaImportResult importFromXlsx(String kbId, MultipartFile file, boolean overwrite);
}
