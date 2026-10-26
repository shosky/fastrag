package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.service.FileService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final KbFileMapper fileMapper;
    private final MinioService minioService;
    private final MessagePublisher messagePublisher;
    private final KbParseStrategyMapper strategyMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public List<FileDto> list(String kbId) {
        // 过滤已删除的文件
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getCreatedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<FileDto> listDeleted(String kbId) {
        // 查询已删除的文件需要绕过逻辑删除
        // 使用自定义 SQL 或直接查询
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNotNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getDeletedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public FileDto upload(String kbId, MultipartFile file) {
        String objectKey = kbId + "/" + IdUtil.fastSimpleUUID();

        // 上传文件到 MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("文件上传到存储失败", e);
        }

        // 保存元数据
        String extension = FileUtil.extName(file.getOriginalFilename());
        KbFile f = new KbFile();
        f.setKbId(kbId);
        f.setName(file.getOriginalFilename());
        f.setExtension(extension);
        f.setSize(file.getSize());
        f.setCategory(detectCategory(extension));
        f.setObjectKey(objectKey);
        f.setStatus("pending");
        f.setProgress(0);
        f.setChunkCount(0);
        fileMapper.insert(f);

        log.info("File uploaded: {}", f.getId());
        return toDto(f);
    }

    @Override
    public FileDto uploadText(String kbId, String fileName, String content) {
        String objectKey = kbId + "/" + IdUtil.fastSimpleUUID();
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        minioService.upload(objectKey, new java.io.ByteArrayInputStream(bytes), "text/plain");

        String extension = FileUtil.extName(fileName);
        KbFile f = new KbFile();
        f.setKbId(kbId);
        f.setName(fileName);
        f.setExtension(extension);
        f.setSize((long) bytes.length);
        f.setCategory(detectCategory(extension));
        f.setObjectKey(objectKey);
        f.setStatus("pending");
        f.setProgress(0);
        f.setChunkCount(0);
        fileMapper.insert(f);
        return toDto(f);
    }

    @Override
    public void process(String kbId, String fileId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw new RuntimeException("File not found");

        // 获取解析策略
        String strategyId = resolveStrategy(kbId, f.getExtension());

        // 发送消息到 RabbitMQ 触发处理
        Map<String, Object> msg = new HashMap<>();
        msg.put("fileId", f.getId());
        msg.put("kbId", kbId);
        msg.put("objectKey", f.getObjectKey());
        msg.put("strategyId", strategyId);
        msg.put("operator", SecurityUtil.getCurrentUser() != null
                ? SecurityUtil.getCurrentUser().getUsername() : "system");
        messagePublisher.publishIngestion(msg);

        log.info("File processing triggered: {}", f.getId());
    }

    @Override
    public FileDto update(String kbId, String fileId, Map<String, Object> patch) {
        var f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).eq(KbFile::getId, fileId).isNull(KbFile::getDeletedAt));
        if (f == null) throw new RuntimeException("File not found");
        if (patch.containsKey("name")) f.setName((String) patch.get("name"));
        if (patch.containsKey("folderId")) f.setFolderId((String) patch.get("folderId"));
        fileMapper.updateById(f);
        return toDto(f);
    }

    @Override
    public void delete(String kbId, String fileId) {
        // 软删除：使用原生 SQL 确保 deleted_at 被更新
        jdbcTemplate.update("UPDATE kb_file SET deleted_at = NOW() WHERE id = ? AND kb_id = ?", fileId, kbId);
    }

    @Override
    public void restore(String kbId, String fileId) {
        // 恢复：清除 deletedAt
        fileMapper.update(null, new LambdaUpdateWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId)
                .set(KbFile::getDeletedAt, null));
    }

    @Override
    public void permanentDelete(String kbId, String fileId) {
        fileMapper.deleteById(fileId);
    }

    @Override
    public void emptyRecycleBin(String kbId) {
        fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).isNotNull(KbFile::getDeletedAt))
                .forEach(f -> fileMapper.deleteById(f.getId()));
    }

    @Override
    public FileDto copy(String kbId, String fileId) {
        var s = fileMapper.selectById(fileId);
        if (s == null) throw new RuntimeException("File not found");
        var c = new KbFile();
        c.setKbId(kbId);
        c.setName(s.getName() + " (copy)");
        c.setCategory(s.getCategory());
        c.setExtension(s.getExtension());
        c.setSize(s.getSize());
        c.setObjectKey(s.getObjectKey());
        c.setStatus("pending");
        c.setProgress(0);
        c.setChunkCount(0);
        fileMapper.insert(c);
        return toDto(c);
    }

    /**
     * 按「应答文档清单」Excel（导出格式：sheet 含 文档名称 列）批量导入文档。
     * 清单只有元数据不含文件内容，因此按名称从系统内其它知识库复制源文件
     * （共享 MinIO objectKey），并触发解析；找不到源的行返回原因。
     */
    @Override
    public Map<String, Object> importManifest(String kbId, MultipartFile file) {
        List<Map<String, String>> rows = parseManifest(file);
        if (rows.isEmpty()) throw new RuntimeException("清单中没有可用数据行（需含「文档名称」列）");

        int imported = 0, existed = 0, missing = 0, parseFailed = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            Map<String, Object> d = new HashMap<>();
            d.put("name", name);

            Long dup = fileMapper.selectCount(new LambdaQueryWrapper<KbFile>()
                    .eq(KbFile::getKbId, kbId).eq(KbFile::getName, name).isNull(KbFile::getDeletedAt));
            if (dup != null && dup > 0) {
                d.put("result", "skipped");
                d.put("reason", "目标知识库已存在同名文档");
                existed++;
                details.add(d);
                continue;
            }

            KbFile src = fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                            .eq(KbFile::getName, name).isNull(KbFile::getDeletedAt)
                            .orderByDesc(KbFile::getCreatedAt))
                    .stream().filter(f -> !kbId.equals(f.getKbId())).findFirst().orElse(null);
            if (src == null) {
                d.put("result", "skipped");
                d.put("reason", "系统内未找到同名源文件，请用「导入」直接上传");
                missing++;
                details.add(d);
                continue;
            }

            KbFile c = new KbFile();
            c.setKbId(kbId);
            c.setName(src.getName());
            c.setCategory(src.getCategory());
            c.setExtension(src.getExtension());
            c.setSize(src.getSize());
            c.setObjectKey(src.getObjectKey());
            c.setStatus("pending");
            c.setProgress(0);
            c.setChunkCount(0);
            fileMapper.insert(c);
            imported++;
            d.put("result", "imported");
            d.put("fileId", c.getId());
            details.add(d);
            try {
                process(kbId, c.getId());
            } catch (Exception e) {
                parseFailed++;
                log.warn("清单导入后触发解析失败: {}", name, e);
            }
        }

        Map<String, Object> r = new HashMap<>();
        r.put("total", rows.size());
        r.put("imported", imported);
        r.put("existed", existed);
        r.put("missing", missing);
        r.put("parseTriggerFailed", parseFailed);
        r.put("details", details);
        return r;
    }

    /** 解析导出的清单 xlsx：仅用 JDK（zip + XML），不引入 POI。返回含 name（文档名称）的行。 */
    private List<Map<String, String>> parseManifest(MultipartFile file) {
        Map<String, String> parts = new HashMap<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (!e.isDirectory() && n.startsWith("xl/") && n.endsWith(".xml")) {
                    parts.put(n, new String(zin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("无法读取 xlsx 文件：" + ex.getMessage(), ex);
        }
        if (parts.isEmpty()) throw new RuntimeException("不是有效的 xlsx 文件");

        try {
            var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            var db = dbf.newDocumentBuilder();

            // 共享字符串表
            List<String> shared = new ArrayList<>();
            String sstXml = parts.get("xl/sharedStrings.xml");
            if (sstXml != null) {
                var sst = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(sstXml)));
                var sis = sst.getElementsByTagName("si");
                for (int i = 0; i < sis.getLength(); i++) {
                    StringBuilder sb = new StringBuilder();
                    collectText(sis.item(i), sb);
                    shared.add(sb.toString());
                }
            }

            // 第一个 sheet：workbook.xml 首个 sheet 的 r:id → rels → 路径
            String sheetPath = "xl/worksheets/sheet1.xml";
            String wbXml = parts.get("xl/workbook.xml");
            String relsXml = parts.get("xl/_rels/workbook.xml.rels");
            if (wbXml != null && relsXml != null) {
                var wb = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(wbXml)));
                var sheets = wb.getElementsByTagName("sheet");
                if (sheets.getLength() > 0) {
                    String rid = sheets.item(0).getAttributes().getNamedItem("r:id") != null
                            ? sheets.item(0).getAttributes().getNamedItem("r:id").getNodeValue() : null;
                    if (rid != null) {
                        var rel = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(relsXml)));
                        var rs = rel.getElementsByTagName("Relationship");
                        for (int i = 0; i < rs.getLength(); i++) {
                            var a = rs.item(i).getAttributes();
                            if (rid.equals(a.getNamedItem("Id").getNodeValue())) {
                                String target = a.getNamedItem("Target").getNodeValue();
                                sheetPath = target.startsWith("/") ? target.substring(1)
                                        : (target.startsWith("xl/") ? target : "xl/" + target);
                                break;
                            }
                        }
                    }
                }
            }
            String sheetXml = parts.get(sheetPath);
            if (sheetXml == null) sheetXml = parts.get("xl/worksheets/sheet1.xml");
            if (sheetXml == null) throw new RuntimeException("xlsx 中没有工作表");

            var sheet = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(sheetXml)));
            var rowNodes = sheet.getElementsByTagName("row");

            // 逐行取值：col 索引 → 文本
            List<Map<Integer, String>> grid = new ArrayList<>();
            for (int i = 0; i < rowNodes.getLength(); i++) {
                Map<Integer, String> rowVals = new HashMap<>();
                var cells = rowNodes.item(i).getChildNodes();
                int fallbackCol = 0;
                for (int j = 0; j < cells.getLength(); j++) {
                    var cell = cells.item(j);
                    if (!"c".equals(cell.getNodeName())) continue;
                    var attrs = cell.getAttributes();
                    int col = fallbackCol;
                    var r = attrs != null ? attrs.getNamedItem("r") : null;
                    if (r != null) col = colIndex(r.getNodeValue());
                    String t = attrs != null && attrs.getNamedItem("t") != null
                            ? attrs.getNamedItem("t").getNodeValue() : "";
                    String val = "";
                    if ("inlineStr".equals(t)) {
                        StringBuilder sb = new StringBuilder();
                        collectText(cell, sb);
                        val = sb.toString();
                    } else {
                        var vs = cell.getChildNodes();
                        for (int k = 0; k < vs.getLength(); k++) {
                            if ("v".equals(vs.item(k).getNodeName())) { val = vs.item(k).getTextContent(); break; }
                        }
                        if ("s".equals(t) && !val.isBlank()) {
                            int idx = Integer.parseInt(val.trim());
                            val = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                        }
                    }
                    rowVals.put(col, val == null ? "" : val.trim());
                    fallbackCol = col + 1;
                }
                grid.add(rowVals);
            }

            // 定位表头行（含「文档名称」），后续行转成 name/extension/status
            List<Map<String, String>> out = new ArrayList<>();
            int headerIdx = -1;
            Map<Integer, String> headerCols = new HashMap<>();
            for (int i = 0; i < grid.size(); i++) {
                for (var en : grid.get(i).entrySet()) {
                    if ("文档名称".equals(en.getValue())) {
                        headerCols.put(en.getKey(), "name");
                        headerIdx = i;
                    } else if ("扩展名".equals(en.getValue())) {
                        headerCols.put(en.getKey(), "extension");
                    } else if ("解析状态".equals(en.getValue())) {
                        headerCols.put(en.getKey(), "status");
                    }
                }
                if (headerIdx >= 0) break;
            }
            if (headerIdx < 0) throw new RuntimeException("清单缺少「文档名称」表头列");

            for (int i = headerIdx + 1; i < grid.size(); i++) {
                Map<Integer, String> rowVals = grid.get(i);
                String name = headerCols.entrySet().stream()
                        .filter(en -> "name".equals(en.getValue())).map(en -> rowVals.get(en.getKey()))
                        .findFirst().orElse(null);
                if (name == null || name.isBlank()) continue;
                Map<String, String> item = new HashMap<>();
                item.put("name", name.trim());
                headerCols.forEach((col, key) -> {
                    if (!"name".equals(key)) item.put(key, rowVals.getOrDefault(col, ""));
                });
                out.add(item);
            }
            return out;
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new RuntimeException("解析清单失败：" + ex.getMessage(), ex);
        }
    }

    private void collectText(org.w3c.dom.Node node, StringBuilder sb) {
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var c = children.item(i);
            if ("t".equals(c.getNodeName())) sb.append(c.getTextContent());
            else collectText(c, sb);
        }
    }

    /** "A1" → 0，"AB3" → 27（仅取列字母部分） */
    private int colIndex(String ref) {
        int v = 0;
        for (char ch : ref.toCharArray()) {
            if (ch >= 'A' && ch <= 'Z') v = v * 26 + (ch - 'A' + 1);
            else if (ch >= 'a' && ch <= 'z') v = v * 26 + (ch - 'a' + 1);
            else break;
        }
        return Math.max(v - 1, 0);
    }


    @Override
    public Map<String, Object> getProcessingStatus(String kbId, String fileId) {
        var f = fileMapper.selectById(fileId);
        if (f == null) throw new RuntimeException("File not found");
        var r = new HashMap<String, Object>();
        r.put("fileId", f.getId());
        r.put("status", f.getStatus());
        r.put("progress", f.getProgress() != null ? f.getProgress() : 0);
        r.put("stage", f.getStage() != null ? f.getStage() : "");
        return r;
    }

    private String detectCategory(String ext) {
        if (ext == null) return "document";
        ext = ext.toLowerCase();
        if (Set.of("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "md", "csv").contains(ext)) return "document";
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(ext)) return "image";
        if (Set.of("mp3", "wav", "m4a", "aac", "ogg").contains(ext)) return "audio";
        if (Set.of("mp4", "avi", "mov", "mkv", "flv").contains(ext)) return "video";
        return "document";
    }

    private String resolveStrategy(String kbId, String extension) {
        try {
            List<KbParseStrategy> strategies = strategyMapper.selectList(
                    new LambdaQueryWrapper<KbParseStrategy>()
                            .eq(KbParseStrategy::getKbId, kbId)
                            .eq(KbParseStrategy::getIsDefault, 1));
            if (!strategies.isEmpty()) return strategies.get(0).getId();

            strategies = strategyMapper.selectList(
                    new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId, kbId));
            if (!strategies.isEmpty()) return strategies.get(0).getId();
        } catch (Exception e) {
            log.warn("Failed to resolve strategy", e);
        }
        return null;
    }

    private FileDto toDto(KbFile f) {
        var d = new FileDto();
        d.setId(f.getId());
        d.setName(f.getName());
        d.setCategory(f.getCategory());
        d.setExtension(f.getExtension());
        d.setSize(f.getSize());
        d.setUrl("/api/kb/" + f.getKbId() + "/files/" + f.getId() + "/download");
        d.setStatus(f.getStatus());
        d.setProgress(f.getProgress());
        d.setStage(f.getStage());
        d.setChunkCount(f.getChunkCount());
        d.setFolderId(f.getFolderId());
        d.setDeletedAt(f.getDeletedAt());
        d.setCreatedAt(f.getCreatedAt());
        d.setUpdatedAt(f.getUpdatedAt());
        return d;
    }
}
