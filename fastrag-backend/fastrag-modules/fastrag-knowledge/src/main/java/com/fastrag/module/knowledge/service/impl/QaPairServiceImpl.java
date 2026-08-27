package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.*;
import com.fastrag.module.knowledge.service.QaPairService;
import com.fastrag.module.knowledge.util.ExcelQaPairImporter;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * QA 问答对服务实现。
 *
 * <p>提供问答对的 CRUD 及 Excel 批量导入能力。
 * 批量导入使用 {@link TransactionTemplate} 确保原子性。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QaPairServiceImpl implements QaPairService {

    private final KbQaPairMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final LogService logService;

    // ==================== 查询 ====================

    @Override
    public List<QaPairDto> list(String kbId) {
        return list(kbId, null);
    }

    @Override
    public List<QaPairDto> list(String kbId, String fileId) {
        var w = new LambdaQueryWrapper<KbQaPair>()
                .eq(KbQaPair::getKbId, kbId)
                .orderByDesc(KbQaPair::getCreatedAt);
        if (fileId != null && !fileId.isBlank()) {
            w.eq(KbQaPair::getFileId, fileId);
        }
        return mapper.selectList(w).stream().map(this::toDto).collect(Collectors.toList());
    }

    // ==================== 创建 ====================

    @Override
    public QaPairDto create(String kbId, QaCreateRequest req) {
        var e = new KbQaPair();
        e.setKbId(kbId);
        e.setQuestion(req.getQuestion());
        e.setAnswer(req.getAnswer());
        e.setSource(req.getSource() != null ? req.getSource() : "manual");
        e.setFileId(req.getFileId());
        e.setStatus("draft");
        mapper.insert(e);
        return toDto(e);
    }

    // ==================== 更新 ====================

    @Override
    public QaPairDto update(String kbId, String id, Map<String, Object> p) {
        var e = mapper.selectById(id);
        if (e == null) throw new RuntimeException("Not found");
        if (p.containsKey("question")) e.setQuestion((String) p.get("question"));
        if (p.containsKey("answer"))   e.setAnswer((String) p.get("answer"));
        mapper.updateById(e);
        return toDto(e);
    }

    // ==================== 删除 ====================

    @Override
    public void delete(String kbId, String id) {
        mapper.deleteById(id);
    }

    // ==================== 确认 ====================

    @Override
    public void confirm(String kbId, String id) {
        var e = mapper.selectById(id);
        if (e != null) {
            e.setStatus("confirmed");
            mapper.updateById(e);
        }
    }

    // ==================== Excel 批量导入 ====================

    @Override
    public QaImportResult importFromXlsx(String kbId, MultipartFile file, boolean overwrite) {
        long startMs = System.currentTimeMillis();

        // 1. 预加载该 kbId 下所有问题的归一化集合（去重后用于 O(1) 查找）
        Set<String> existingQuestions = mapper.selectList(
                        new LambdaQueryWrapper<KbQaPair>()
                                .eq(KbQaPair::getKbId, kbId)
                                .select(KbQaPair::getQuestion))
                .stream()
                .map(KbQaPair::getQuestion)
                .filter(Objects::nonNull)
                .map(q -> ExcelQaPairImporter.normalizeQuestion(q.trim()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2. 解析 Excel，得到逐行结果
        QaImportResult result = ExcelQaPairImporter.parse(file, existingQuestions, overwrite);

        // 3. 按行结果分类：insert / update / skip / fail
        List<ImportRowResult> successRows = result.getDetails().stream()
                .filter(r -> "success".equals(r.getStatus()))
                .toList();

        // 构建归一化 question → 现有 id 的映射（用于 overwrite 模式）
        Map<String, String> normalizedToId = mapper.selectList(
                        new LambdaQueryWrapper<KbQaPair>()
                                .eq(KbQaPair::getKbId, kbId)
                                .select(KbQaPair::getId, KbQaPair::getQuestion))
                .stream()
                .filter(e -> e.getQuestion() != null)
                .collect(Collectors.toMap(
                        e -> ExcelQaPairImporter.normalizeQuestion(e.getQuestion()),
                        KbQaPair::getId,
                        (a, b) -> a));

        // 4. 事务内批量写入
        transactionTemplate.executeWithoutResult(status -> {
            for (ImportRowResult row : successRows) {
                String normalizedQ = row.getNormalizedQuestion();
                String existingId = normalizedToId.get(normalizedQ);

                if (existingId != null && "覆盖更新（原记录将被更新）".equals(row.getReason())) {
                    // overwrite 模式：更新已有记录
                    KbQaPair existing = mapper.selectById(existingId);
                    if (existing != null) {
                        existing.setQuestion(row.getQuestion());
                        existing.setAnswer(row.getAnswer() != null ? row.getAnswer() : existing.getAnswer());
                        existing.setStatus(row.getQaStatus() != null ? row.getQaStatus() : existing.getStatus());
                        mapper.updateById(existing);
                    }
                } else {
                    // 插入新记录
                    KbQaPair entity = new KbQaPair();
                    entity.setId(generateSnowflakeId());
                    entity.setKbId(kbId);
                    entity.setQuestion(row.getQuestion());
                    entity.setAnswer(row.getAnswer() != null ? row.getAnswer() : "");
                    entity.setSource("manual");
                    entity.setStatus(row.getQaStatus() != null ? row.getQaStatus() : "draft");
                    entity.setFileId(null);
                    mapper.insert(entity);
                }
            }
        });

        // 5. 汇总计数
        result.setSuccessCount(
                (int) result.getDetails().stream().filter(r -> "success".equals(r.getStatus())).count());
        result.setSkipCount(
                (int) result.getDetails().stream().filter(r -> "skipped".equals(r.getStatus())).count());
        result.setFailCount(
                (int) result.getDetails().stream().filter(r -> "failed".equals(r.getStatus())).count());
        result.setTotalRows(result.getDetails().size());

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[QaImport] kbId={} 导入完成: 成功={}, 跳过={}, 失败={}, 耗时={}ms",
                kbId, result.getSuccessCount(), result.getSkipCount(),
                result.getFailCount(), elapsed);

        // 6. 审计日志
        logService.addLog(kbId,
                com.fastrag.common.enums.LogCategory.operation,
                com.fastrag.common.enums.ActionType.qa_pair_created,
                "qa-pairs",
                "批量导入QA对: 成功=" + result.getSuccessCount()
                        + ", 跳过=" + result.getSkipCount()
                        + ", 失败=" + result.getFailCount(),
                null,
                "success");

        return result;
    }

    // ==================== 内部方法 ====================

    /** 生成与 ASSIGN_ID 策略兼容的雪花 ID 字符串 */
    private static String generateSnowflakeId() {
        // 使用时间戳（毫秒）+ 随机数生成 24 位十六进制 ID
        long ts = System.currentTimeMillis();
        String hex = Long.toHexString(ts);
        while (hex.length() < 12) hex = "0" + hex;
        return hex + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private QaPairDto toDto(KbQaPair e) {
        var d = new QaPairDto();
        d.setId(e.getId());
        d.setKbId(e.getKbId());
        d.setFileId(e.getFileId());
        d.setFileName(e.getFileName());
        d.setQuestion(e.getQuestion());
        d.setAnswer(e.getAnswer());
        d.setSource(e.getSource());
        d.setStatus(e.getStatus());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }
}
