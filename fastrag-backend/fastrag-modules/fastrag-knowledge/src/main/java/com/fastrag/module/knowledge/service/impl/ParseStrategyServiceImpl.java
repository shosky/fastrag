package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.ParseStrategyDto;
import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import com.fastrag.module.knowledge.parser.ParseMethodRegistry;
import com.fastrag.module.knowledge.service.ParseStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParseStrategyServiceImpl implements ParseStrategyService {
    private final KbParseStrategyMapper mapper;
    private final KbFileMapper fileMapper;

    @Override
    public List<ParseStrategyDto> list(String kbId) {
        // 只返回知识库级命名策略（file_id 为空）；文件级专属策略不出现在策略管理列表
        List<KbParseStrategy> all = mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .isNull(KbParseStrategy::getFileId));
        Map<String, Long> refCounts = countFileReferences(kbId,
                all.stream().map(KbParseStrategy::getId).collect(Collectors.toList()));
        return all.stream().map(s -> {
            ParseStrategyDto dto = toDto(s);
            dto.setFileCount(refCounts.getOrDefault(s.getId(), 0L).intValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ParseStrategyDto get(String kbId, String id) {
        ParseStrategyDto dto = toDto(getOne(kbId, id));
        dto.setFileCount(countFileReferences(kbId, List.of(id)).getOrDefault(id, 0L).intValue());
        return dto;
    }

    /**
     * 统计各策略被未删除文件的引用数（策略删除确认框据此提示影响面）。
     * 绑定被引用的策略删除后，文件回退扩展名自动匹配（resolveRequestedStrategy 的兜底语义）。
     */
    private Map<String, Long> countFileReferences(String kbId, List<String> strategyIds) {
        if (strategyIds == null || strategyIds.isEmpty()) return Map.of();
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                        .eq(KbFile::getKbId, kbId)
                        .isNull(KbFile::getDeletedAt)
                        .in(KbFile::getParseStrategyId, strategyIds))
                .stream()
                .collect(Collectors.groupingBy(KbFile::getParseStrategyId, Collectors.counting()));
    }

    @Override
    public ParseStrategyDto create(String kbId, ParseStrategyRequest req) {
        validate(req, req.getParseMethod());
        validateRequiredFields(req, true);
        validateChunkParams(req.getAdvanced());
        var e = new KbParseStrategy();
        e.setKbId(kbId);
        e.setName(req.getName());
        e.setDescription(req.getDescription());
        e.setExtensions(req.getExtensions() != null ? JSONUtil.toJsonStr(req.getExtensions()) : null);
        e.setParseMethod(req.getParseMethod());
        e.setIsDefault(0);
        e.setAdvanced(req.getAdvanced() != null ? JSONUtil.toJsonStr(req.getAdvanced()) : null);
        e.setLlmModel(req.getLlmModel());
        mapper.insert(e);
        return toDto(e);
    }

    @Override
    public ParseStrategyDto update(String kbId, String id, ParseStrategyRequest req) {
        var e = getOne(kbId, id);
        // 生效方法：请求优先，否则沿用现有策略（部分更新语义）
        String effectiveMethod = req.getParseMethod() != null ? req.getParseMethod() : e.getParseMethod();
        validate(req, effectiveMethod);
        validateRequiredFields(req, false);
        // 分片参数校验：更新为部分更新语义，请求带 advanced 时校验请求值，否则校验沿用后的现有值
        if (req.getAdvanced() != null) {
            validateChunkParams(req.getAdvanced());
        } else if (StrUtil.isNotBlank(e.getAdvanced())) {
            validateChunkParams(JSONUtil.parseObj(e.getAdvanced()));
        }
        if (req.getName() != null) e.setName(req.getName());
        if (req.getDescription() != null) e.setDescription(req.getDescription());
        if (req.getExtensions() != null) e.setExtensions(JSONUtil.toJsonStr(req.getExtensions()));
        if (req.getParseMethod() != null) e.setParseMethod(req.getParseMethod());
        if (req.getLlmModel() != null) e.setLlmModel(req.getLlmModel());
        if (req.getAdvanced() != null) e.setAdvanced(JSONUtil.toJsonStr(req.getAdvanced()));
        mapper.updateById(e);
        return toDto(e);
    }

    @Override
    public void delete(String kbId, String id) {
        var e = getOne(kbId, id);
        if (e.getIsDefault() == 1) throw BusinessException.badRequest("默认策略不允许删除");
        mapper.deleteById(id);
    }

    @Override
    public void setDefault(String kbId, String id) {
        mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>()
                        .eq(KbParseStrategy::getKbId, kbId)
                        .eq(KbParseStrategy::getIsDefault, 1)
                        .isNull(KbParseStrategy::getFileId))
                .forEach(s -> { s.setIsDefault(0); mapper.updateById(s); });
        var e = getOne(kbId, id);
        e.setIsDefault(1);
        mapper.updateById(e);
    }

    @Override
    public ParseStrategyDto resolveByExtension(String kbId, String ext) {
        var all = mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .isNull(KbParseStrategy::getFileId));
        for (var s : all) {
            if (StrUtil.isNotBlank(s.getExtensions()) && JSONUtil.toList(s.getExtensions(), String.class).contains(ext))
                return toDto(s);
        }
        return all.stream().filter(s -> s.getIsDefault() == 1).findFirst().map(this::toDto).orElse(null);
    }

    /**
     * 检测给定扩展名列表与知识库内已有策略（排除自身）的扩展名重叠，返回冲突策略名称。
     * 前端「实时冲突提示」据此给出覆盖歧义警告。
     */
    @Override
    public List<String> detectConflicts(String kbId, List<String> exts, String excludeId) {
        if (exts == null || exts.isEmpty()) return List.of();
        Set<String> extSet = new HashSet<>(exts);
        List<String> conflicts = new ArrayList<>();
        var all = mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .isNull(KbParseStrategy::getFileId));
        for (var s : all) {
            if (excludeId != null && excludeId.equals(s.getId())) continue;
            if (StrUtil.isBlank(s.getExtensions())) continue;
            List<String> own = JSONUtil.toList(s.getExtensions(), String.class);
            if (own.stream().anyMatch(extSet::contains)) conflicts.add(s.getName());
        }
        return conflicts;
    }

    /**
     * 保存文件级专属自定义策略（upsert：同一文件仅一条，重复保存即更新）。
     *
     * <p>该策略绑定 file_id，不出现在策略管理列表、不参与扩展名自动匹配，
     * 随文件删除级联清理。管线（解析/分片/预览）只按策略 id 消费，无需感知文件级差异。
     *
     * <p>名称/扩展名校验放宽：name 为文件展示用（可超 50 字符不截断、可为空），
     * extensions 由前端按文件扩展名传入（用于与 parseMethod 的兼容性校验）。
     */
    @Override
    public ParseStrategyDto saveFileStrategy(String kbId, String fileId, ParseStrategyRequest req) {
        validate(req, req.getParseMethod());
        validateChunkParams(req.getAdvanced());

        KbParseStrategy existing = mapper.selectOne(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .eq(KbParseStrategy::getFileId, fileId));

        String name = StrUtil.isNotBlank(req.getName()) ? req.getName() : "自定义";
        String description = StrUtil.maxLength(req.getDescription(), 200);
        String extensions = req.getExtensions() != null ? JSONUtil.toJsonStr(req.getExtensions()) : null;
        String advanced = req.getAdvanced() != null ? JSONUtil.toJsonStr(req.getAdvanced()) : null;

        if (existing == null) {
            var e = new KbParseStrategy();
            e.setKbId(kbId);
            e.setFileId(fileId);
            e.setName(name);
            e.setDescription(description);
            e.setExtensions(extensions);
            e.setParseMethod(req.getParseMethod());
            e.setIsDefault(0);
            e.setAdvanced(advanced);
            e.setLlmModel(req.getLlmModel());
            mapper.insert(e);
            return toDto(e);
        }
        existing.setName(name);
        existing.setDescription(description);
        existing.setExtensions(extensions);
        existing.setParseMethod(req.getParseMethod());
        existing.setAdvanced(advanced);
        existing.setLlmModel(req.getLlmModel());
        mapper.updateById(existing);
        return toDto(existing);
    }

    /**
     * 删除文件级专属策略（文件删除时级联清理）。
     */
    @Override
    public void deleteFileStrategy(String kbId, String fileId) {
        mapper.delete(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .eq(KbParseStrategy::getFileId, fileId));
    }

    /**
     * 策略合法性校验：
     * <ul>
     *   <li>parseMethod 必须为注册表内方法（创建必填；更新按部分更新语义可缺省）</li>
     *   <li>extensions 非空且每个扩展名被系统支持</li>
     *   <li>扩展名与（生效的）解析方法兼容：default 兼容全部受支持扩展名，专用方法只兼容自身扩展名集</li>
     * </ul>
     *
     * @param effectiveMethod 生效的解析方法（创建时为请求值；更新时请求值优先，缺省沿用现有策略）
     */
    private void validate(ParseStrategyRequest req, String effectiveMethod) {
        if (effectiveMethod == null || effectiveMethod.isBlank()) {
            throw BusinessException.badRequest("请选择文档类型（解析方法）");
        }
        if (ParseMethodRegistry.of(effectiveMethod) == null) {
            throw BusinessException.badRequest("不支持的解析方法: " + effectiveMethod);
        }
        List<String> extensions = req.getExtensions();
        if (extensions != null) {
            if (extensions.isEmpty()) {
                throw BusinessException.badRequest("请至少选择一个文件扩展名");
            }
            for (String ext : extensions) {
                if (!ParseMethodRegistry.isSupported(ext)) {
                    throw BusinessException.badRequest("不支持的扩展名: " + ext + "（可选扩展名见文档类型映射）");
                }
                if (!ParseMethodRegistry.isCompatible(effectiveMethod, ext)) {
                    throw BusinessException.badRequest("扩展名 " + ext + " 与文档类型「"
                            + ParseMethodRegistry.of(effectiveMethod).getLabel() + "」不兼容");
                }
            }
        }
    }

    /**
     * 名称/描述/扩展名的必填与长度校验（与前端表单规则对齐）：
     * <ul>
     *   <li>name：创建必填且 ≤ 50 字符；更新时若提供则不能为空且 ≤ 50 字符（null 表示沿用现有值）</li>
     *   <li>description：可选；提供时 ≤ 200 字符</li>
     *   <li>extensions：创建必填非空；更新时若提供则不能为空（null 表示沿用现有值，走部分更新）</li>
     * </ul>
     */
    private void validateRequiredFields(ParseStrategyRequest req, boolean isCreate) {
        if (isCreate || req.getName() != null) {
            if (StrUtil.isBlank(req.getName())) {
                throw BusinessException.badRequest("请输入策略名称");
            }
            if (req.getName().length() > 50) {
                throw BusinessException.badRequest("策略名称不能超过 50 个字符");
            }
        }
        if (req.getDescription() != null && req.getDescription().length() > 200) {
            throw BusinessException.badRequest("策略描述不能超过 200 个字符");
        }
        if (isCreate) {
            if (req.getExtensions() == null || req.getExtensions().isEmpty()) {
                throw BusinessException.badRequest("请至少选择一个文件扩展名");
            }
        } else if (req.getExtensions() != null && req.getExtensions().isEmpty()) {
            throw BusinessException.badRequest("请至少选择一个文件扩展名");
        }
    }

    /**
     * 分片参数合法性校验：overlap 必须小于 chunkLength。
     * overlap ≥ chunkLength 会导致相邻分片几乎全量重复（向量化后产生大量重复内容、稀释召回），
     * 属于系统应拦截的非法配置。兼容分组格式（advanced.chunk.*）与旧版扁平格式（advanced.*）。
     */
    @SuppressWarnings("unchecked")
    private void validateChunkParams(Map<String, Object> advanced) {
        if (advanced == null) return;
        Map<String, Object> chunk = advanced.get("chunk") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : advanced;
        Object len = chunk.get("chunkLength");
        Object ov = chunk.get("overlap");
        if (len instanceof Number n && ov instanceof Number o
                && n.intValue() > 0 && o.intValue() >= n.intValue()) {
            throw BusinessException.badRequest("重叠字符数（" + o.intValue()
                    + "）必须小于分片长度（" + n.intValue() + "），建议取分片长度的 10%~20%");
        }
    }

    private KbParseStrategy getOne(String kbId, String id) {
        // 管理接口只允许操作知识库级策略（file_id 为空），文件级专属策略走 saveFileStrategy/deleteFileStrategy
        var e = mapper.selectOne(new LambdaQueryWrapper<KbParseStrategy>()
                .eq(KbParseStrategy::getKbId, kbId)
                .eq(KbParseStrategy::getId, id)
                .isNull(KbParseStrategy::getFileId));
        if (e == null) throw BusinessException.notFound("解析策略不存在");
        return e;
    }

    private ParseStrategyDto toDto(KbParseStrategy e) {
        var d = new ParseStrategyDto();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setDescription(e.getDescription());
        d.setExtensions(StrUtil.isNotBlank(e.getExtensions()) ? JSONUtil.toList(e.getExtensions(), String.class) : null);
        d.setParseMethod(e.getParseMethod());
        d.setDefault(e.getIsDefault() == 1);
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        d.setAdvanced(StrUtil.isNotBlank(e.getAdvanced()) ? JSONUtil.toBean(e.getAdvanced(), Map.class) : null);
        d.setLlmModel(e.getLlmModel());
        return d;
    }
}
