package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.AttrDef;
import com.fastrag.module.knowledge.model.FileBatchTagRequest;
import com.fastrag.module.knowledge.model.FileMetadataUpdateRequest;
import com.fastrag.module.knowledge.model.FileMetadataVO;
import com.fastrag.module.knowledge.service.FileMetadataService;
import com.fastrag.module.knowledge.service.TagRelationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件元数据管理服务实现（分册四 · schema 驱动版本）。
 *
 * <p>设计（方案 A）：元数据字段全部由 KB 自定义属性 schema (AttrDef) 定义，文件取值按字段 name 归并。
 * 勾选 searchable 且 name 命中已知检索维度（region/publishDate/docLevel）的字段，保存时投影到
 * kb_file 强类型列（供分册二原生 WHERE / ORDER BY），其余字段写入 custom_attrs JSON。无硬编码固定字段区。</p>
 *
 * <p>状态机：人工写入→ metadata_status=revised（metadata_source=manual）；自动抽取（extractMetadata）
 * 仅覆盖 none/partial，不覆盖 revised，除非显式 force。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataServiceImpl implements FileMetadataService {

    private final KbFileMapper fileMapper;
    private final KbChunkMapper chunkMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KbTagMapper tagMapper;
    private final TagRelationHelper tagHelper;

    /** 发文层级可选值（kb_file.doc_level 唯一合法值） */
    private static final Set<String> DOC_LEVEL_STORE = Set.of("national", "provincial", "municipal", "county", "unknown");

    /** 检索感知保留字段名：命中且 searchable=true 时投影到强类型列（与分册二检索契约对齐） */
    private static final Set<String> SEARCHABLE_BOUND_KEYS = Set.of("region", "publishDate", "docLevel");

    /** 行政区划简称/全称词典（覆盖主要省级行政区，可扩展） */
    private static final Map<String, String> REGION_ALIAS = new LinkedHashMap<>() {{
        put("北京", "北京市"); put("天津", "天津市"); put("河北", "河北省"); put("山西", "山西省");
        put("内蒙古", "内蒙古自治区"); put("辽宁", "辽宁省"); put("吉林", "吉林省"); put("黑龙江", "黑龙江省");
        put("上海", "上海市"); put("江苏", "江苏省"); put("浙江", "浙江省"); put("安徽", "安徽省");
        put("福建", "福建省"); put("江西", "江西省"); put("山东", "山东省"); put("河南", "河南省");
        put("湖北", "湖北省"); put("湖南", "湖南省"); put("广东", "广东省"); put("广西", "广西壮族自治区");
        put("海南", "海南省"); put("重庆", "重庆市"); put("四川", "四川省"); put("贵州", "贵州省");
        put("云南", "云南省"); put("西藏", "西藏自治区"); put("陕西", "陕西省"); put("甘肃", "甘肃省");
        put("青海", "青海省"); put("宁夏", "宁夏回族自治区"); put("新疆", "新疆维吾尔自治区");
    }};

    /** 发文日期正则：2024年12月31日 / 2024-12-31 / 20241231 / 2024.12.31 */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(20\\d{2})\\s*年(\\d{1,2})\\s*月(\\d{1,2})?\\s*日?|(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})|(20\\d{6})");

    /** 文号正则：发改价格〔2024〕123号 / 发改价格[2024]123号 / (2024)123号 / 2024年123号 */
    private static final Pattern DOC_NUMBER_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{0,12})[〔\\[【（(]\\s*(\\d{4})\\s*[〕\\]】)）]\\s*(\\d+)\\s*号|(\\d{4})\\s*年\\s*(\\d+)\\s*号");

    private static final Pattern DOC_LEVEL_PATTERN = Pattern.compile(
            "(国务院|中共中央|国家发展改革委|国家发改委|工业和信息化部|发改委|部令|国令)" +
                    "|(省人民政府|省政府|省委|省人大|省发改委|省发展改革委|省物价局|省厅)" +
                    "|(市人民政府|市政府|市委|市发改委|市物价局)" +
                    "|(县人民政府|县政府|县委|区人民政府|区政府|区发改委)");

    // ========== 查询 ==========

    @Override
    public FileMetadataVO getMetadata(String kbId, String fileId) {
        KbFile f = requireFile(kbId, fileId);
        KnowledgeBase kb = kbMapper.selectById(kbId);
        List<AttrDef> schema = parseAttrSchema(kb != null ? kb.getCustomAttrSchema() : null);

        FileMetadataVO vo = new FileMetadataVO();
        vo.setFileId(f.getId());
        vo.setFileName(f.getName());
        vo.setAttrSchema(schema);
        vo.setMetadataStatus(f.getMetadataStatus() == null ? "none" : f.getMetadataStatus());
        vo.setMetadataSource(f.getMetadataSource());

        // 标签
        vo.setTags(tagHelper.listTags(TagRelationHelper.TARGET_FILE, fileId).stream()
                .map(t -> {
                    FileMetadataVO.FileTagVO v = new FileMetadataVO.FileTagVO();
                    v.setId(t.getId()); v.setName(t.getName()); v.setColor(t.getColor()); v.setTagTypeId(t.getTagTypeId());
                    return v;
                }).collect(Collectors.toList()));

        // 取值归并：检索感知字段读强类型列，其余读 custom_attrs（仅含 schema 定义的字段）
        Map<String, Object> customAttrs = parseJsonMap(f.getCustomAttrs());
        Map<String, Object> values = new LinkedHashMap<>();
        for (AttrDef def : schema) {
            String key = def.getName();
            if (isSearchableBound(def)) {
                Object v = typedColumnValue(def, f);
                if (v != null) values.put(key, v);
            } else if (customAttrs.containsKey(key)) {
                values.put(key, customAttrs.get(key));
            }
        }
        vo.setValues(values);
        return vo;
    }

    // ========== 写入 ==========

    @Override
    @Transactional
    public FileMetadataVO updateMetadata(String kbId, String fileId, FileMetadataUpdateRequest req) {
        KbFile f = requireFile(kbId, fileId);
        if (f.getDeletedAt() != null) {
            throw BusinessException.badRequest("文件已在回收站中，请先恢复后再编辑元数据");
        }
        KnowledgeBase kb = kbMapper.selectById(kbId);
        List<AttrDef> schema = parseAttrSchema(kb != null ? kb.getCustomAttrSchema() : null);
        Map<String, Object> incoming = req.getValues() == null ? new LinkedHashMap<>() : req.getValues();

        // 按 schema 逐字段处理：检索感知 → 投影强类型列；普通字段 → custom_attrs（空=清除）
        Map<String, Object> customAttrs = new LinkedHashMap<>(parseJsonMap(f.getCustomAttrs()));
        for (AttrDef def : schema) {
            String key = def.getName();
            Object raw = incoming.get(key);
            if (isSearchableBound(def)) {
                projectToTyped(def, raw, f);
            } else if (isBlankValue(raw)) {
                customAttrs.remove(key);
            } else {
                customAttrs.put(key, coerceValue(def, raw));
            }
        }
        f.setCustomAttrs(JSONUtil.toJsonStr(customAttrs));

        // 状态机：人工写入 → revised/manual（A7：自动抽取不再覆盖人工值）
        f.setMetadataStatus("revised");
        f.setMetadataSource("manual");
        fileMapper.updateById(f);

        // 事务内回填 kb_chunk 冗余列（A2）
        backfillChunkRedundancy(fileId, f);
        log.info("[FileMetadata] Updated (schema-driven) metadata for file {} in kb {}", fileId, kbId);
        return getMetadata(kbId, fileId);
    }

    @Override
    @Transactional
    public void setFileTags(String kbId, String fileId, List<String> tagIds) {
        requireFile(kbId, fileId);
        if (tagIds != null) {
            for (String tid : tagIds) {
                KbTag tag = findTag(tid);
                if (!kbId.equals(tag.getKbId())) {
                    throw BusinessException.badRequest("标签 " + tag.getName() + " 不属于当前知识库");
                }
            }
        }
        tagHelper.replaceTags(TagRelationHelper.TARGET_FILE, fileId, tagIds);
    }

    @Override
    @Transactional
    public Map<String, Object> batchSetTags(String kbId, FileBatchTagRequest req) {
        if (req.getFileIds() == null || req.getFileIds().isEmpty()) {
            throw BusinessException.badRequest("fileIds 不能为空");
        }
        List<String> addIds = req.getAddTagIds() == null ? new ArrayList<>() : req.getAddTagIds().stream().distinct().collect(Collectors.toList());
        List<String> removeIds = req.getRemoveTagIds() == null ? new ArrayList<>() : req.getRemoveTagIds().stream().distinct().collect(Collectors.toList());
        for (String tid : addIds) { KbTag tag = findTag(tid); if (!kbId.equals(tag.getKbId())) throw BusinessException.badRequest("标签 " + tag.getName() + " 不属于当前知识库"); }
        for (String tid : removeIds) { KbTag tag = findTag(tid); if (!kbId.equals(tag.getKbId())) throw BusinessException.badRequest("标签 " + tag.getName() + " 不属于当前知识库"); }

        int added = 0, removed = 0, failed = 0;
        Set<String> failedFiles = new TreeSet<>();
        for (String fileId : req.getFileIds()) {
            try {
                requireFile(kbId, fileId);
                for (String tid : addIds) { tagHelper.addTag(TagRelationHelper.TARGET_FILE, fileId, tid); added++; }
                for (String tid : removeIds) { tagHelper.removeTag(TagRelationHelper.TARGET_FILE, fileId, tid); removed++; }
            } catch (Exception e) {
                failed++; failedFiles.add(fileId);
                log.warn("[FileMetadata] Batch tag failed for file {}: {}", fileId, e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("fileCount", req.getFileIds().size());
        result.put("addedCount", added);
        result.put("removedCount", removed);
        result.put("failedCount", failed);
        result.put("failedFileIds", failedFiles);
        return result;
    }

    // ========== 存量补抽 ==========

    @Override
    @Transactional
    public Map<String, Object> extractMetadata(String kbId, List<String> fileIds, boolean force) {
        List<KbFile> targets = new ArrayList<>();
        if (fileIds == null || fileIds.isEmpty()) {
            targets = fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                    .eq(KbFile::getKbId, kbId).isNull(KbFile::getDeletedAt));
        } else {
            for (String fid : fileIds) targets.add(requireFile(kbId, fid));
        }
        KnowledgeBase kb = kbMapper.selectById(kbId);
        Set<String> schemaNames = parseAttrSchema(kb != null ? kb.getCustomAttrSchema() : null)
                .stream().map(AttrDef::getName).collect(Collectors.toSet());

        int extracted = 0, partial = 0, skippedRevised = 0, failed = 0;
        List<String> failedFiles = new ArrayList<>();
        for (KbFile f : targets) {
            if (f.getDeletedAt() != null) continue;
            String status = f.getMetadataStatus() == null ? "none" : f.getMetadataStatus();
            if ("revised".equals(status) && !force) { skippedRevised++; continue; }
            try {
                ExtractionResult r = extractFromFileName(f.getName());
                if (r.isAnyResolved() || force) {
                    Map<String, Object> customAttrs = new LinkedHashMap<>(parseJsonMap(f.getCustomAttrs()));
                    // 检索感知维度投影到强类型列（schema 含该保留名即投）
                    if (schemaNames.contains("region")) f.setRegion(StrUtil.isBlank(r.region()) ? null : toJsonArray(r.region()));
                    if (schemaNames.contains("publishDate")) f.setPublishDate(r.publishDate);
                    if (schemaNames.contains("docLevel")) f.setDocLevel(r.docLevel);
                    // 普通保留字段进入 custom_attrs（仅当 schema 声明）
                    if (schemaNames.contains("issuer") && StrUtil.isNotBlank(r.issuer)) customAttrs.put("issuer", r.issuer);
                    if (schemaNames.contains("docNumber") && StrUtil.isNotBlank(r.docNumber)) customAttrs.put("docNumber", r.docNumber);
                    f.setCustomAttrs(JSONUtil.toJsonStr(customAttrs));
                    f.setMetadataSource("auto");
                    f.setMetadataStatus(r.isFull() ? "full" : "partial");
                    fileMapper.updateById(f);
                    backfillChunkRedundancy(f.getId(), f);
                    if (r.isFull()) extracted++; else partial++;
                }
            } catch (Exception e) {
                failed++; failedFiles.add(f.getId());
                log.warn("[FileMetadata] Extract failed for file {}: {}", f.getId(), e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalFiles", targets.size());
        result.put("extracted", extracted);
        result.put("partial", partial);
        result.put("skippedRevised", skippedRevised);
        result.put("failed", failed);
        result.put("failedFileIds", failedFiles);
        return result;
    }

    // ========== 内部辅助方法 ==========

    private KbFile requireFile(String kbId, String fileId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId).eq(KbFile::getKbId, kbId));
        if (f == null) throw BusinessException.notFound("文件不存在或不属于当前知识库");
        return f;
    }

    private KbTag findTag(String tagId) {
        KbTag tag = tagMapper.selectById(tagId);
        if (tag == null) throw BusinessException.badRequest("标签不存在: " + tagId);
        return tag;
    }

    /** 判断是否检索感知保留字段（searchable=true 且 name 命中 region/publishDate/docLevel） */
    private boolean isSearchableBound(AttrDef def) {
        return def.getName() != null && Boolean.TRUE.equals(def.getSearchable())
                && SEARCHABLE_BOUND_KEYS.contains(def.getName());
    }

    /** 检索感知字段投影到强类型列（null/空=清空） */
    private void projectToTyped(AttrDef def, Object raw, KbFile f) {
        String key = def.getName();
        String text = raw == null ? null : String.valueOf(raw).trim();
        switch (key) {
            case "region": f.setRegion(normalizeRegion(text)); break;
            case "publishDate": f.setPublishDate(StrUtil.isBlank(text) ? null : parseDateStrict(text)); break;
            case "docLevel": f.setDocLevel(normalizeDocLevel(text)); break;
            default: break;
        }
    }

    /** 检索感知字段的强类型列读取（返回给 VO 的值） */
    private Object typedColumnValue(AttrDef def, KbFile f) {
        String key = def.getName();
        switch (key) {
            case "region": return f.getRegion();
            case "publishDate": return f.getPublishDate() == null ? null : f.getPublishDate().toString();
            case "docLevel": return f.getDocLevel();
            default: return null;
        }
    }

    private boolean isBlankValue(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    /** 按 schema 类型对普通字段取值做基础类型化（number→Double, boolean→Boolean，未知原样） */
    private Object coerceValue(AttrDef def, Object raw) {
        if (raw == null) return null;
        if ("number".equals(def.getType())) {
            try { return Double.valueOf(String.valueOf(raw)); } catch (Exception ignored) { return raw; }
        }
        if ("boolean".equals(def.getType())) {
            return Boolean.valueOf(String.valueOf(raw));
        }
        return raw;
    }

    private LocalDate parseDateStrict(String text) {
        try { return LocalDate.parse(text.trim()); }
        catch (Exception e) { throw BusinessException.badRequest("日期格式应为 yyyy-MM-dd: " + text); }
    }

    /** 解析 KB 自定义属性 schema JSON 数组串 → AttrDef 列表（容错：非法 JSON 返回空列表） */
    private List<AttrDef> parseAttrSchema(String json) {
        if (StrUtil.isBlank(json)) return new ArrayList<>();
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            List<AttrDef> defs = new ArrayList<>();
            for (Object o : arr) {
                AttrDef def = JSONUtil.toBean(JSONUtil.parseObj(o), AttrDef.class);
                if (StrUtil.isNotBlank(def.getName())) defs.add(def);
            }
            return defs;
        } catch (Exception e) {
            log.warn("Failed to parse custom_attr_schema: {}", json);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (StrUtil.isBlank(json)) return new LinkedHashMap<>();
        try { return new LinkedHashMap<>(JSONUtil.toBean(json, Map.class)); }
        catch (Exception e) { log.warn("Failed to parse custom_attrs: {}", json); return new LinkedHashMap<>(); }
    }

    /** 归一化地域：字符串或 JSON 数组串 → 规范化 JSON 数组串（去重、trim、过滤空） */
    private String normalizeRegion(String region) {
        if (StrUtil.isBlank(region)) return null;
        String trimmed = region.trim();
        Set<String> values = new TreeSet<>();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray arr = JSONUtil.parseArray(trimmed);
                for (Object o : arr) { String s = o == null ? null : String.valueOf(o).trim(); if (StrUtil.isNotBlank(s)) values.add(s); }
            } else {
                for (String s : trimmed.split("[,，、\\s]+")) { s = s.trim(); if (StrUtil.isNotBlank(s)) values.add(s); }
            }
        } catch (Exception e) { values.add(trimmed); }
        return values.isEmpty() ? null : toJsonArray(String.join(",", values));
    }

    private String toJsonArray(String commaSeparated) {
        JSONArray arr = new JSONArray();
        for (String s : commaSeparated.split(",")) { s = s.trim(); if (StrUtil.isNotBlank(s)) arr.add(s); }
        return arr.toString();
    }

    private String normalizeDocLevel(String docLevel) {
        if (StrUtil.isBlank(docLevel)) return null;
        String v = docLevel.trim().toLowerCase();
        switch (v) {
            case "国家级": return "national";
            case "省部级": case "省级": case "province": return "provincial";
            case "市级": return "municipal";
            case "县级": case "区级": return "county";
            default: return DOC_LEVEL_STORE.contains(v) ? v : null;
        }
    }

    /** 从文件名规则抽取元数据（轻量实现；后续可升级为分册二 LLM 路径） */
    private ExtractionResult extractFromFileName(String fileName) {
        ExtractionResult r = new ExtractionResult();
        String text = fileName == null ? "" : fileName;
        // 1. 地域
        for (Map.Entry<String, String> e : REGION_ALIAS.entrySet()) {
            if (text.contains(e.getKey())) r.addRegion(e.getValue());
        }
        // 2. 发文日期
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            if (dateMatcher.group(1) != null) r.publishDate = toDate(dateMatcher.group(1), dateMatcher.group(2), dateMatcher.group(3));
            else if (dateMatcher.group(4) != null) r.publishDate = toDate(dateMatcher.group(4), dateMatcher.group(5), dateMatcher.group(6));
            else if (dateMatcher.group(7) != null) { String g = dateMatcher.group(7); r.publishDate = toDate(g.substring(0,4), g.substring(4,6), g.substring(6,8)); }
        }
        // 3. 文号
        Matcher numMatcher = DOC_NUMBER_PATTERN.matcher(text);
        if (numMatcher.find()) {
            if (numMatcher.group(2) != null) r.docNumber = numMatcher.group(1) + "〔" + numMatcher.group(2) + "〕" + numMatcher.group(3) + "号";
            else if (numMatcher.group(4) != null) r.docNumber = numMatcher.group(4) + "年" + numMatcher.group(5) + "号";
        }
        // 4. 发文层级
        Matcher levelMatcher = DOC_LEVEL_PATTERN.matcher(text);
        if (levelMatcher.find()) {
            if (levelMatcher.group(1) != null) r.docLevel = "national";
            else if (levelMatcher.group(2) != null) r.docLevel = "provincial";
            else if (levelMatcher.group(3) != null) r.docLevel = "municipal";
            else if (levelMatcher.group(4) != null) r.docLevel = "county";
        }
        // 5. 发文机关（无文号时的前缀启发式；有文号取前缀）
        if (StrUtil.isBlank(r.docNumber)) {
            Matcher m = Pattern.compile("([\\u4e00-\\u9fa5]{2,12}?)(?:关于|转发|印发|发布|颁布|决定|通知|公告|通告)").matcher(text);
            if (m.find()) r.issuer = m.group(1);
        } else {
            int idx = text.indexOf("〔");
            if (idx > 0) {
                String prefix = text.substring(0, idx).replaceAll("[0-9\\s年月日（）()\\[\\]【】]", "").trim();
                r.issuer = prefix.length() > 20 ? prefix.substring(0, 20) : prefix;
            }
        }
        return r;
    }

    private LocalDate toDate(String y, String m, String d) {
        try {
            int yy = Integer.parseInt(y);
            int mm = m == null ? 1 : Integer.parseInt(m);
            int dd = d == null ? 1 : Integer.parseInt(d);
            if (yy < 1900 || yy > 2100 || mm < 1 || mm > 12 || dd < 1 || dd > 31) return null;
            return LocalDate.of(yy, mm, dd);
        } catch (Exception e) { return null; }
    }

    /** 回填 kb_chunk 冗余列（检索过滤/排序直接消费） */
    private void backfillChunkRedundancy(String fileId, KbFile f) {
        chunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                .eq(KbChunk::getFileId, fileId)
                .set(KbChunk::getRegion, f.getRegion())
                .set(KbChunk::getPublishDate, f.getPublishDate())
                .set(KbChunk::getDocLevel, f.getDocLevel()));
    }

    /** 抽取结果中间结构 */
    private static class ExtractionResult {
        private final Set<String> regions = new TreeSet<>();
        private LocalDate publishDate;
        private String docLevel;
        private String issuer;
        private String docNumber;

        void addRegion(String r) { regions.add(r); }
        String region() { return regions.isEmpty() ? null : String.join(",", regions); }
        boolean isAnyResolved() {
            return !regions.isEmpty() || publishDate != null || docLevel != null
                    || StrUtil.isNotBlank(issuer) || StrUtil.isNotBlank(docNumber);
        }
        boolean isFull() {
            return !regions.isEmpty() && publishDate != null && docLevel != null
                    && StrUtil.isNotBlank(issuer) && StrUtil.isNotBlank(docNumber);
        }
    }
}