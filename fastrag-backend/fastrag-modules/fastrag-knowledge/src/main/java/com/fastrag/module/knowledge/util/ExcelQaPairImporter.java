package com.fastrag.module.knowledge.util;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.model.ImportRowResult;
import com.fastrag.module.knowledge.model.QaImportResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel 问答对导入解析工具。
 *
 * <p>负责将用户上传的 Excel 文件解析为问答对数据，进行列映射、行校验、
 * 重复检测和归一化处理。不涉及数据库写入，纯解析职责。</p>
 *
 * <p>支持的列名（大小写不敏感，支持中英文变体）：</p>
 * <ul>
 *   <li>问题 / question — 必填</li>
 *   <li>答案 / answer — 必填</li>
 *   <li>分类 / category — 可选</li>
 *   <li>关键词 / keywords — 可选</li>
 *   <li>优先级 / priority — 可选，整数 1-10，默认 5</li>
 *   <li>状态 / status — 可选，draft / confirmed，默认 draft</li>
 *   <li>生效时间 / effectiveTime — 可选</li>
 *   <li>失效时间 / expireTime — 可选</li>
 * </ul>
 */
@Slf4j
public class ExcelQaPairImporter {

    private static final int MAX_DATA_ROWS = 10_000;
    private static final int MAX_QUESTION_TRUNCATE = 80;

    /** 列名归一化映射：各种写法 → 标准字段名 */
    private static final Map<String, String> COLUMN_ALIASES;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("问题", "question"); m.put("问题（必填）", "question");
        m.put("question", "question"); m.put("Question", "question");
        m.put("答案", "answer"); m.put("答案（必填）", "answer");
        m.put("answer", "answer"); m.put("Answer", "answer");
        m.put("分类", "category"); m.put("category", "category"); m.put("Category", "category");
        m.put("类别", "category");
        m.put("关键词", "keywords"); m.put("关键词（逗号分隔）", "keywords");
        m.put("keywords", "keywords"); m.put("Keywords", "keywords"); m.put("keyword", "keywords");
        m.put("优先级", "priority"); m.put("priority", "priority"); m.put("Priority", "priority");
        m.put("状态", "status"); m.put("status", "status"); m.put("Status", "status");
        m.put("生效时间", "effectiveTime"); m.put("effectiveTime", "effectiveTime");
        m.put("失效时间", "expireTime"); m.put("expireTime", "expireTime");
        COLUMN_ALIASES = Collections.unmodifiableMap(m);
    }

    private ExcelQaPairImporter() {
    }

    /**
     * 解析上传的 Excel 文件。
     *
     * @param file                 上传的 Excel 文件
     * @param existingQuestions    该 kbId 下已有的归一化问题集合（用于重复检测）
     * @param overwrite            是否覆盖已存在的问答对
     * @return 导入结果，包含逐行明细
     */
    public static QaImportResult parse(MultipartFile file,
                                       Set<String> existingQuestions,
                                       boolean overwrite) {
        QaImportResult result = new QaImportResult();

        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空，请重新选择");
        }
        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        String ext = getExtension(originalFilename);
        if (!ext.matches("(?i)\\.(xlsx|xls)$")) {
            throw BusinessException.badRequest("仅支持 .xlsx 和 .xls 格式的文件");
        }

        Workbook workbook;
        try (InputStream is = file.getInputStream()) {
            workbook = WorkbookFactory.create(is);
        } catch (EncryptedDocumentException e) {
            log.warn("[ExcelImport] 文件已加密: {}", originalFilename);
            throw BusinessException.badRequest("Excel 文件已加密或受密码保护，无法解析");
        } catch (IOException e) {
            log.error("[ExcelImport] 文件读取失败: {}", originalFilename, e);
            throw BusinessException.badRequest("文件读取失败，请确认文件未损坏");
        } catch (Exception e) {
            log.error("[ExcelImport] 文件解析失败: {}", originalFilename, e);
            throw BusinessException.badRequest("文件解析失败，请确认文件格式正确：" + e.getMessage());
        }

        try (workbook) {
            int sheetCount = workbook.getNumberOfSheets();
            if (sheetCount == 0) {
                throw BusinessException.badRequest("Excel 文件中没有任何工作表");
            }

            Sheet sheet = null;
            for (int i = 0; i < sheetCount; i++) {
                if (!workbook.isSheetHidden(i)) {
                    sheet = workbook.getSheetAt(i);
                    break;
                }
            }
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.badRequest("Excel 文件为空，未找到表头行");
            }

            Map<Integer, String> colMap = buildColumnMap(headerRow);
            Integer qColIdx = getColIndex(colMap, "question");
            Integer aColIdx = getColIndex(colMap, "answer");

            if (qColIdx == null || aColIdx == null) {
                List<String> missing = new ArrayList<>();
                if (qColIdx == null) missing.add("问题/question");
                if (aColIdx == null) missing.add("答案/answer");
                throw BusinessException.badRequest(
                        "缺少必要的列：" + String.join("、", missing)
                                + "，请使用导入模板或确保表头包含这些列");
            }

            int lastRow = sheet.getLastRowNum();
            int dataRowCount = 0;
            Set<String> seenInFile = new HashSet<>();

            for (int r = 1; r <= lastRow; r++) {
                if (dataRowCount >= MAX_DATA_ROWS) {
                    log.warn("[ExcelImport] 超过 {} 行上限，截断", MAX_DATA_ROWS);
                    result.getDetails().add(ImportRowResult.failed(
                            r + 1, "", "Excel 数据行超过 " + MAX_DATA_ROWS
                                    + " 条上限，请分批导入"));
                    break;
                }

                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                dataRowCount++;

                String rawQuestion = getCellString(row, qColIdx);
                String rawAnswer   = getCellString(row, aColIdx);
                String category    = getCellString(row, getColIndex(colMap, "category"));
                String keywords    = getCellString(row, getColIndex(colMap, "keywords"));
                String priorityStr = getCellString(row, getColIndex(colMap, "priority"));
                String rawStatus   = getCellString(row, getColIndex(colMap, "status"));

                // 必填校验
                String trimmedQ = rawQuestion == null ? "" : rawQuestion.trim();
                if (trimmedQ.isEmpty()) {
                    result.getDetails().add(ImportRowResult.failed(
                            r + 1, "", "问题不能为空"));
                    continue;
                }

                String normalizedQ = normalizeQuestion(trimmedQ);

                // 文件内重复
                if (!seenInFile.add(normalizedQ)) {
                    result.getDetails().add(ImportRowResult.skipped(
                            r + 1, truncate(trimmedQ),
                            "该问题在本文件中存在重复"));
                    continue;
                }

                // 与已有数据比对
                boolean exists = existingQuestions != null && existingQuestions.contains(normalizedQ);
                if (exists && overwrite) {
                    result.getDetails().add(ImportRowResult.success(
                            r + 1, trimmedQ, rawAnswer == null ? "" : rawAnswer,
                            category, keywords, parsePriority(priorityStr),
                            normalizeStatus(rawStatus), normalizedQ));
                    // reason 标记为覆盖模式
                    result.getDetails().get(result.getDetails().size() - 1)
                            .setReason("覆盖更新（原记录将被更新）");
                } else if (exists) {
                    result.getDetails().add(ImportRowResult.skipped(
                            r + 1, truncate(trimmedQ),
                            "知识库中已存在相同问题，已跳过"));
                } else {
                    // 校验优先级
                    if (priorityStr != null && !priorityStr.isBlank()) {
                        try {
                            int p = Integer.parseInt(priorityStr.trim());
                            if (p < 1 || p > 10) {
                                result.getDetails().add(ImportRowResult.failed(
                                        r + 1, truncate(trimmedQ),
                                        "优先级必须是 1-10 的整数，当前值：" + priorityStr.trim()));
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            result.getDetails().add(ImportRowResult.failed(
                                    r + 1, truncate(trimmedQ),
                                    "优先级格式无效，需为整数：" + priorityStr));
                            continue;
                        }
                    }

                    // 校验状态值
                    if (rawStatus != null && !rawStatus.isBlank()) {
                        String s = normalizeStatus(rawStatus);
                        if (s == null) {
                            result.getDetails().add(ImportRowResult.failed(
                                    r + 1, truncate(trimmedQ),
                                    "状态值无效，仅支持 draft / confirmed，当前值：" + rawStatus.trim()));
                            continue;
                        }
                        rawStatus = s;
                    }

                    result.getDetails().add(ImportRowResult.success(
                            r + 1, trimmedQ, rawAnswer == null ? "" : rawAnswer,
                            category, keywords, parsePriority(priorityStr),
                            rawStatus != null ? rawStatus : "draft", normalizedQ));
                }
            }

            result.setTotalRows(result.getDetails().size());
            log.info("[ExcelImport] 解析完成: {} 行，成功={}，跳过={}，失败={}",
                    result.getTotalRows(),
                    result.getDetails().stream().filter(r -> "success".equals(r.getStatus())).count(),
                    result.getDetails().stream().filter(r -> "skipped".equals(r.getStatus())).count(),
                    result.getDetails().stream().filter(r -> "failed".equals(r.getStatus())).count());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ExcelImport] 解析过程异常", e);
            throw BusinessException.badRequest("Excel 解析异常：" + e.getMessage());
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    /** 构建列序号 → 标准字段名的映射 */
    private static Map<Integer, String> buildColumnMap(Row headerRow) {
        Map<Integer, String> map = new LinkedHashMap<>();
        Set<String> usedFields = new HashSet<>();

        for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) continue;
            String raw = getCellString(cell);
            if (raw == null || raw.isBlank()) continue;

            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            String field = COLUMN_ALIASES.get(normalized);

            if (field == null) {
                String trimmed = normalized.replaceAll("^\\(必填\\)\\s*|^\\(可选\\)\\s*|^\\*\\s*", "").trim();
                field = COLUMN_ALIASES.get(trimmed);
            }

            if (field != null && !usedFields.contains(field)) {
                map.put(c, field);
                usedFields.add(field);
            }
        }
        return map;
    }

    private static Integer getColIndex(Map<Integer, String> colMap, String fieldName) {
        return colMap.entrySet().stream()
                .filter(e -> fieldName.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** 读取单元格文本值 */
    private static String getCellString(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        return getCellString(cell);
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    DataFormatter formatter = new DataFormatter();
                    yield formatter.formatCellValue(cell);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            case BLANK -> "";
            default -> null;
        };
    }

    /** 判断行是否完全为空 */
    private static boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = getCellString(cell);
                if (v != null && !v.isBlank()) return false;
            }
        }
        return true;
    }

    /**
     * 问题归一化：全角 → 半角，trim，压缩连续空白。
     */
    public static String normalizeQuestion(String question) {
        if (question == null) return "";
        String s = fullWidthToHalfWidth(question);
        s = s.trim().replaceAll("\\s+", " ");
        return s;
    }

    /** 全角字符转半角 */
    private static String fullWidthToHalfWidth(String src) {
        if (src == null) return null;
        StringBuilder sb = new StringBuilder(src.length());
        for (char c : src.toCharArray()) {
            if (c == 0x3000) {
                sb.append(' ');
            } else if (c > 0xFF00 && c < 0xFF5F) {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 截断文本用于展示 */
    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_QUESTION_TRUNCATE
                ? text.substring(0, MAX_QUESTION_TRUNCATE) + "…" : text;
    }

    /** 解析优先级字符串 */
    private static Integer parsePriority(String s) {
        if (s == null || s.isBlank()) return 5;
        try {
            int p = Integer.parseInt(s.trim());
            return Math.max(1, Math.min(10, p));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 状态值标准化 */
    private static String normalizeStatus(String s) {
        if (s == null || s.isBlank()) return "draft";
        String lower = s.trim().toLowerCase(Locale.ROOT);
        if ("draft".equals(lower) || "草稿".equals(s.trim())) return "draft";
        if ("confirmed".equals(lower) || "已确认".equals(s.trim())
                || "confirmed".equalsIgnoreCase(s.trim())) return "confirmed";
        return null;
    }

    /** 获取文件扩展名（含点） */
    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
