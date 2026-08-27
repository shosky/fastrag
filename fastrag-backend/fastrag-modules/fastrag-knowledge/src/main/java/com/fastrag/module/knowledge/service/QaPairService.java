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
     * 从 Excel 文件批量导入问答对。
     *
     * @param kbId             知识库 ID
     * @param file             上传的 Excel 文件（.xlsx 或 .xls）
     * @param overwrite        是否覆盖已存在的问答对（按问题文本匹配）
     * @return 导入结果，包含成功/跳过/失败统计及逐行明细
     */
    QaImportResult importFromXlsx(String kbId, MultipartFile file, boolean overwrite);
}
