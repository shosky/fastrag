package com.fastrag.module.knowledge.chunking;

/**
 * LLM 分片边界审查实现。
 *
 * <p>流程：分批（≤{@value #BATCH_SIZE} 片/批，最多 {@value #MAX_CALLS} 次调用）送 LLM
 * 审查边界合理性 → LLM 输出 merge/split/keep 调整方案 JSON（模式与 AiChunk 分组输出同构）→
 * 全覆盖校验（每个编号恰好出现一次）→ 按位置降序应用调整 → 内容守恒校验
 * （调整前后受影响范围的文本必须逐字相等，零丢失零篡改）→ 任一环节失败整批回退保持原样。</p>
 *
 * <p>模型解析回退链：策略 llmModel → 知识库 llmModel → 知识库 graphLlmModel → 缺失则跳过审查。
 * 审查通过的分片回填 llmReviewed=1/reviewModel/reviewTime/reviewNote，
 * 由 StorageServiceImpl 持久化并写入 kb_chunk_llm_review 明细。</p>
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkReviewServiceImpl implements ChunkReviewService {

    private final StrategyConfigResolver configResolver;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final KbParseStrategyMapper parseStrategyMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final ModelRecordMapper modelRecordMapper;

    /** 每批送审分片数 */
    private static final int BATCH_SIZE = 10;
    /** 单篇最大 LLM 调用次数（成本红线，Q11） */
    private static final int MAX_CALLS = 3;
    /** 单次调用超时（秒），对齐 GraphBuild 观测的 LLM 时延量级 */
    private static final int TIMEOUT_SECONDS = 90;
    /** merge 后允许的最大分片长度（字符） */
    private static final int MAX_MERGE_LENGTH = 2000;
    /** 送审内容截断长度（省 token；beforeLine 引用超出截断范围会导致该批回退） */
    private static final int CONTENT_TRUNCATE = 800;
    /** 审查 prompt 版本（调整 prompt 时递增，明细表按版本追溯） */
    private static final String PROMPT_VERSION = "v2";
    /** 自纠重试时回灌给模型的违规修正提示（%1=违规原因，%2/%3=本批编号区间） */
    private static final String CORRECTION_SUFFIX = """

            注意：你上一次的输出存在如下违规，必须修正：
            %1$s
            请重新输出覆盖第 %2$d~%3$d 号分片的完整 JSON 调整方案，每个编号恰好出现一次，严格遵守 table/code 片只能 keep 的规则。""";

    /**
     * 全局总开关（application.yml: fastrag.chunk.llm-review.enabled，默认 false）。
     * true = 强制对所有 structure_aware 摄入生效（测试/全局推广用）；
     * false = 按策略级 llmReview.enabled 开关。
     */
    @Value("${fastrag.chunk.llm-review.enabled:false}")
    private boolean globalEnabled;

    /** 一个边界调整方案：merge / split / keep */
    private record Adjustment(String action, List<Integer> chunks, Integer splitChunk,
                              String beforeLine, String reason) {
    }

    /** 解析结果：error 非 null 表示校验失败（含具体原因），adjustments 为通过校验的调整方案 */
    private record ParseOutcome(String error, List<Adjustment> adjustments) {
        static ParseOutcome fail(String error) { return new ParseOutcome(error, null); }
        static ParseOutcome ok(List<Adjustment> adjustments) { return new ParseOutcome(null, adjustments); }
    }

    /** 审查过程中的工作分组：原文行序列 + 源分片原编号（拆分后可重复） */
    private static final class Group {
        final List<String> lines = new ArrayList<>();          // 原文行（split("\n",-1) 保留空行）
        final List<Integer> lineSources = new ArrayList<>();   // 每行所属源分片原编号
        final Set<Integer> sources = new LinkedHashSet<>();    // 该组涉及的源分片编号集合
        final StringBuilder note = new StringBuilder();        // 应用的调整说明
    }

    @Override
    public List<ChunkData> reviewAndAdjust(String kbId, String fileId, String strategyId, List<ChunkData> chunks) {
        if (chunks == null || chunks.size() < 2) {
            return chunks;
        }
        ParseStrategyConfig.ChunkConfig cfg = configResolver.resolve(strategyId).getChunk();
        if (!"structure_aware".equals(cfg.getStrategy())) {
            return chunks;
        }
        // 生效判定：yml 全局总开关（强制）OR 策略级开关
        boolean strategyEnabled = cfg.getLlmReview() != null && cfg.getLlmReview().isEnabled();
        if (!globalEnabled && !strategyEnabled) {
            // 可观测性：开关未开时必须留痕，否则"为什么没审查"无法从日志判断
            log.info("[ChunkReview] disabled (global={}, strategy llmReview.enabled={}), skip review for strategy {}",
                    globalEnabled, strategyEnabled, strategyId);
            return chunks;
        }
        String model = resolveModel(strategyId, kbId);
        if (model == null || model.isBlank()) {
            log.warn("[ChunkReview] no LLM model configured (strategy.llmModel / kb.llmModel / kb.graphLlmModel), skip review");
            return chunks;
        }
        ModelRecord rec = resolveRecord(model);
        if (rec == null) {
            log.warn("[ChunkReview] model '{}' not online, skip review", model);
            return chunks;
        }

        // 初始分组：每个原始分片一组
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Group g = new Group();
            g.lines.addAll(Arrays.asList(chunks.get(i).getContent().split("\n", -1)));
            for (int k = 0; k < g.lines.size(); k++) g.lineSources.add(i);
            g.sources.add(i);
            groups.add(g);
        }

        int n = chunks.size();
        int batches = Math.min(MAX_CALLS, (n + BATCH_SIZE - 1) / BATCH_SIZE);
        int calls = 0;
        int reviewedUpTo = 0; // [0, reviewedUpTo) 内的源分片已被成功审查
        int totalMerge = 0;
        int totalSplit = 0;
        int totalKeep = 0;

        int b = 0;
        while (b < batches) {
            int from = b * BATCH_SIZE;
            int to = Math.min(from + BATCH_SIZE, n);
            String prompt = buildPrompt(from, to, chunks);

            ParseOutcome outcome = null;
            String response = null;
            // 每批最多两次调用：首次 + 违规自纠重试（把失败原因回灌给模型；重试消耗同一调用预算，MAX_CALLS 封顶）
            for (int attempt = 1; attempt <= 2; attempt++) {
                if (attempt == 2 && calls >= MAX_CALLS) {
                    log.warn("[ChunkReview] batch {}-{} invalid ({}), call budget exhausted, keep original; response={}",
                            from, to - 1, outcome.error(), truncate(response, 600));
                    break;
                }
                String promptToUse = attempt == 1
                        ? prompt
                        : prompt + CORRECTION_SUFFIX.formatted(outcome.error(), from, to - 1);
                try {
                    response = llmService.chatWithTimeout(model, promptToUse, rec.getApiUrl(), rec.getApiKeyRef(),
                            false, TIMEOUT_SECONDS, 2048);
                } catch (Exception e) {
                    log.warn("[ChunkReview] LLM call failed (batch {}-{}, attempt {}), stop and keep remaining original: {}",
                            from, to - 1, attempt, e.getMessage());
                    response = null;
                    break;
                }
                calls++;
                outcome = parseAdjustments(response, from, to, chunks);
                if (outcome.error() == null) break;
                if (attempt == 1) {
                    log.warn("[ChunkReview] batch {}-{} invalid ({}), retry once with correction hint",
                            from, to - 1, outcome.error());
                } else {
                    log.warn("[ChunkReview] batch {}-{} invalid after correction retry ({}), keep original; response={}",
                            from, to - 1, outcome.error(), truncate(response, 600));
                }
            }
            if (outcome == null || outcome.error() != null) break;   // 本批失败：剩余批次保守终止

            List<Adjustment> ads = outcome.adjustments();

            // 深拷贝快照，守恒校验失败整批回退
            List<Group> snapshot = deepCopy(groups);
            String before = flattenRange(groups, from, to);
            if (!applyAdjustments(groups, ads, from, to)) {
                log.warn("[ChunkReview] batch {}-{} adjustment application failed, keep original", from, to - 1);
                groups = snapshot;
                break;
            }
            String after = flattenRange(groups, from, to);
            if (!before.equals(after)) {
                log.warn("[ChunkReview] batch {}-{} conservation check FAILED (content changed), revert batch", from, to - 1);
                groups = snapshot;
                break;
            }
            reviewedUpTo = to;
            int mergeCount = (int) ads.stream().filter(a -> "merge".equals(a.action())).count();
            int splitCount = (int) ads.stream().filter(a -> "split".equals(a.action())).count();
            int keepCount = (int) ads.stream().filter(a -> "keep".equals(a.action())).count();
            totalMerge += mergeCount;
            totalSplit += splitCount;
            totalKeep += keepCount;
            log.info("[ChunkReview] batch {}-{} applied: merge={}, split={}, keep={} (entries={})",
                    from, to - 1, mergeCount, splitCount, keepCount, ads.size());
            for (Adjustment ad : ads) {
                if ("keep".equals(ad.action())) continue;
                String target = ad.chunks() != null
                        ? "chunks " + ad.chunks()
                        : "chunk " + ad.splitChunk() + " @\"" + truncate(ad.beforeLine(), 30) + "\"";
                log.info("[ChunkReview]   -> {} {}: {}", ad.action(), target, ad.reason());
            }
            b++;
        }

        // 重建分片：连续编号 + 审查字段回填
        List<ChunkData> result = new ArrayList<>();
        int idx = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Group g : groups) {
            int firstSource = g.sources.iterator().next();
            ChunkData c = chunks.get(firstSource);
            // 组内源分片类型一致则沿用，否则退回 text（table/code 片禁止参与 merge/split，不会出现混合）
            String firstType = null;
            boolean sameType = true;
            for (Integer s : g.sources) {
                String t = chunks.get(s).getChunkType() == null ? "text" : chunks.get(s).getChunkType();
                if (firstType == null) firstType = t;
                else if (!firstType.equals(t)) sameType = false;
            }
            boolean reviewed = allReviewed(g, reviewedUpTo);
            ChunkData rebuilt = ChunkData.builder()
                    .id("chunk_" + idx)
                    .index(idx)
                    .content(String.join("\n", g.lines))
                    .title(c.getTitle())
                    .headingPath(c.getHeadingPath())
                    .pageNumber(c.getPageNumber())
                    .pageRange(c.getPageRange())
                    .chunkType(sameType && firstType != null ? firstType : "text")
                    .llmReviewed(reviewed ? 1 : 0)
                    .reviewModel(reviewed ? model : null)
                    .reviewTime(reviewed ? now : null)
                    .reviewNote(g.note.isEmpty() ? null : g.note.toString())
                    .reviewPromptVersion(reviewed ? PROMPT_VERSION : null)
                    .build();
            if (c.getImageKeys() != null) {
                rebuilt.setImageKeys(new ArrayList<>(c.getImageKeys()));
            }
            result.add(rebuilt);
            idx++;
        }

        log.info("[ChunkReview] done: {} chunks -> {} chunks (calls={}, reviewedUpTo={}, merge={}, split={}, keep={}, model={}, prompt={})",
                n, result.size(), calls, reviewedUpTo, totalMerge, totalSplit, totalKeep, model, PROMPT_VERSION);
        return result;
    }

    // ==================== 模型解析 ====================

    /** 模型回退链：策略 llmModel → 知识库 llmModel → 知识库 graphLlmModel → null（跳过审查） */
    private String resolveModel(String strategyId, String kbId) {
        try {
            KbParseStrategy ps = parseStrategyMapper.selectById(strategyId);
            if (ps != null && ps.getLlmModel() != null && !ps.getLlmModel().isBlank()) {
                return ps.getLlmModel();
            }
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null) {
                return null;
            }
            if (kb.getLlmModel() != null && !kb.getLlmModel().isBlank()) {
                return kb.getLlmModel();
            }
            return kb.getGraphLlmModel();
        } catch (Exception e) {
            log.warn("[ChunkReview] resolve model failed: {}", e.getMessage());
            return null;
        }
    }

    private ModelRecord resolveRecord(String model) {
        try {
            return modelRecordMapper.selectOne(new LambdaQueryWrapper<ModelRecord>()
                    .eq(ModelRecord::getCode, model)
                    .eq(ModelRecord::getStatus, "online")
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.debug("[ChunkReview] resolve model record '{}' failed: {}", model, e.getMessage());
            return null;
        }
    }

    // ==================== Prompt ====================

    private String buildPrompt(int from, int to, List<ChunkData> chunks) {
        StringBuilder list = new StringBuilder();
        List<Integer> protectedIndexes = new ArrayList<>();
        for (int i = from; i < to; i++) {
            ChunkData c = chunks.get(i);
            String type = c.getChunkType() == null ? "text" : c.getChunkType();
            list.append(i).append(". [").append(type).append("]");
            if ("table".equals(type) || "code".equals(type)) {
                list.append("（仅可 keep，禁止合并/拆分）");
                protectedIndexes.add(i);
            }
            list.append(' ').append(truncate(c.getContent(), CONTENT_TRUNCATE).replace('\n', ' ')).append('\n');
        }
        String protectedNote = protectedIndexes.isEmpty()
                ? "本批无 table/code 分片。"
                : "本批中编号 " + protectedIndexes + " 为 table/code 分片，只能作为 keep 出现，出现在 merge/split 中即违规。";
        return """
                你是知识库分片质量审核员。以下是同一篇文档按顺序切出的第 %d~%d 号分片（内容摘要）。
                请审查分片边界是否合理，只允许调整边界，禁止改动、增删任何文字。
                只输出 JSON 对象，禁止输出任何其他文字：
                {"adjustments":[
                  {"action":"merge","chunks":[12,13],"reason":"原因"},
                  {"action":"split","chunk":14,"beforeLine":"新片首行的开头原文","reason":"原因"},
                  {"action":"keep","chunks":[15]}
                ]}
                规则：
                1. merge 的分片编号必须连续（如 3,4,5），且合并后总长不得超过 %d 字符；
                2. split 的 beforeLine 必须逐字引用该分片中某一行开头的一段原文，新片从该行开始；
                3. type 为 table/code 的分片只能 keep，不得合并或拆分；%s
                4. 每个编号必须恰好出现一次（merge/split/keep 三选一），编号不重复、不遗漏；
                5. 常见不合理：同一小节被切成多个碎片、一片混入明显不相关主题、切点切断句子论述、标题与正文分离。
                分片列表：
                %s""".formatted(from, to - 1, MAX_MERGE_LENGTH, protectedNote, list);
    }

    // ==================== 调整方案解析与校验 ====================

    /**
     * 解析并校验 LLM 调整方案。要求 [from,to) 每个编号恰好出现一次；
     * merge 编号连续、成员不含 table/code、合并后不超长；split 目标为 text 分片且 beforeLine 可定位。
     * 任一违规返回 null（整批回退）。
     */
    private ParseOutcome parseAdjustments(String response, int from, int to, List<ChunkData> chunks) {
        try {
            String json = stripFences(response);
            int s = json.indexOf('{');
            int e = json.lastIndexOf('}');
            if (s < 0 || e <= s) return ParseOutcome.fail("no JSON object found in response");
            JsonNode root = objectMapper.readTree(json.substring(s, e + 1));
            JsonNode arr = root.get("adjustments");
            if (arr == null || !arr.isArray() || arr.isEmpty()) return ParseOutcome.fail("adjustments array missing/empty");
            Map<Integer, String> coverage = new HashMap<>();
            List<Adjustment> out = new ArrayList<>();
            for (JsonNode op : arr) {
                String action = op.path("action").asText("");
                String reason = op.path("reason").asText("");
                switch (action) {
                    case "merge" -> {
                        JsonNode ids = op.path("chunks");
                        if (!ids.isArray() || ids.size() < 2) return ParseOutcome.fail("merge needs chunks array with >=2 members");
                        List<Integer> members = new ArrayList<>();
                        int prev = -1;
                        int total = 0;
                        for (JsonNode id : ids) {
                            int v = id.asInt(-1);
                            if (v < from || v >= to) return ParseOutcome.fail("merge index " + v + " out of range [" + from + "," + to + ")");
                            if (prev >= 0 && v != prev + 1) return ParseOutcome.fail("merge members not contiguous at " + v);
                            String type = chunks.get(v).getChunkType() == null ? "text" : chunks.get(v).getChunkType();
                            if ("table".equals(type) || "code".equals(type)) return ParseOutcome.fail("merge contains non-mergeable " + type + " chunk " + v);
                            total += chunks.get(v).getContent() == null ? 0 : chunks.get(v).getContent().length();
                            members.add(v);
                            prev = v;
                        }
                        if (total > MAX_MERGE_LENGTH) return ParseOutcome.fail("merged length " + total + " > " + MAX_MERGE_LENGTH);
                        for (int v : members) mark(coverage, v, "merge", from, to);
                        out.add(new Adjustment("merge", members, null, null, reason));
                    }
                    case "split" -> {
                        int v = op.path("chunk").asInt(-1);
                        if (v < from || v >= to) return ParseOutcome.fail("split index " + v + " out of range [" + from + "," + to + ")");
                        String type = chunks.get(v).getChunkType() == null ? "text" : chunks.get(v).getChunkType();
                        if (!"text".equals(type)) return ParseOutcome.fail("split target chunk " + v + " is " + type + " (only text splittable)");
                        String beforeLine = op.path("beforeLine").asText("").trim();
                        if (beforeLine.isEmpty()) return ParseOutcome.fail("split beforeLine empty for chunk " + v);
                        if (resolvable(chunks.get(v).getContent(), beforeLine) < 0)
                            return ParseOutcome.fail("split beforeLine not found verbatim in chunk " + v + ": \"" + truncate(beforeLine, 40) + "\"");
                        mark(coverage, v, "split", from, to);
                        out.add(new Adjustment("split", null, v, beforeLine, reason));
                    }
                    case "keep" -> {
                        JsonNode ids = op.path("chunks");
                        if (!ids.isArray() || ids.isEmpty()) return ParseOutcome.fail("keep needs non-empty chunks array");
                        List<Integer> members = new ArrayList<>();
                        for (JsonNode id : ids) {
                            int v = id.asInt(-1);
                            if (v < from || v >= to) return ParseOutcome.fail("keep index " + v + " out of range [" + from + "," + to + ")");
                            members.add(v);
                            mark(coverage, v, "keep", from, to);
                        }
                        out.add(new Adjustment("keep", members, null, null, reason));
                    }
                    default -> {
                        return ParseOutcome.fail("unknown action \"" + action + "\"");
                    }
                }
            }
            // 全覆盖校验：[from,to) 每个编号恰好一次
            List<Integer> missing = new ArrayList<>();
            for (int v = from; v < to; v++) {
                if (!coverage.containsKey(v)) missing.add(v);
            }
            if (!missing.isEmpty()) return ParseOutcome.fail("indexes not covered: " + missing);
            return ParseOutcome.ok(out);
        } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
            return ParseOutcome.fail("JSON parse failed: " + jpe.getOriginalMessage());
        } catch (IllegalStateException ise) {
            return ParseOutcome.fail(ise.getMessage()); // mark()：编号重复覆盖
        } catch (Exception ex) {
            return ParseOutcome.fail("unexpected: " + ex.getMessage());
        }
    }

    private void mark(Map<Integer, String> coverage, int index, String action, int from, int to) {
        if (index < from || index >= to || coverage.containsKey(index)) {
            throw new IllegalStateException("duplicate/out-of-range index: " + index);
        }
        coverage.put(index, action);
    }

    /** beforeLine 定位：优先行首逐字匹配，退化为行内包含；命中返回行下标（>0，保证拆分两侧非空） */
    private int resolvable(String content, String beforeLine) {
        if (content == null) return -1;
        String[] lines = content.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().startsWith(beforeLine)) return i;
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].contains(beforeLine)) return i;
        }
        return -1;
    }

    private String stripFences(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            int fence = t.lastIndexOf("```");
            if (fence >= 0) t = t.substring(0, fence);
        }
        return t;
    }

    // ==================== 调整应用（含内容守恒校验） ====================

    /**
     * 应用调整方案：按受影响最小位置降序执行（保证位置引用不失效）。
     * 返回 false 表示应用失败（找不到定位行等），调用方应整批回退。
     */
    private boolean applyAdjustments(List<Group> groups, List<Adjustment> ads, int from, int to) {
        List<Adjustment> ordered = new ArrayList<>(ads);
        ordered.sort(Comparator.comparingInt(a -> -minAffected(a)));
        for (Adjustment ad : ordered) {
            Map<Integer, Integer> posOf = buildPosOf(groups);
            switch (ad.action()) {
                case "merge" -> {
                    int p = posOf.get(ad.chunks().get(0));
                    String reason = ad.reason();
                    for (int k = 1; k < ad.chunks().size(); k++) {
                        int q = posOf.get(ad.chunks().get(k));
                        Group host = groups.get(p);
                        Group absorbed = groups.get(q);
                        appendNote(host, String.format("merge(第%d,%d号片): %s", ad.chunks().get(0), ad.chunks().get(k), reason));
                        appendNote(absorbed, String.format("merge(第%d,%d号片): %s", ad.chunks().get(0), ad.chunks().get(k), reason));
                        host.lines.addAll(absorbed.lines);
                        host.lineSources.addAll(absorbed.lineSources);
                        host.sources.addAll(absorbed.sources);
                        groups.remove(q);
                    }
                }
                case "split" -> {
                    int p = posOf.get(ad.splitChunk());
                    Group g = groups.get(p);
                    int cut = resolvable(String.join("\n", g.lines), ad.beforeLine());
                    if (cut <= 0 || cut >= g.lines.size()) return false;
                    Group head = new Group();
                    Group tail = new Group();
                    for (int k = 0; k < g.lines.size(); k++) {
                        (k < cut ? head : tail).lines.add(g.lines.get(k));
                        (k < cut ? head : tail).lineSources.add(g.lineSources.get(k));
                    }
                    head.sources.addAll(g.lineSources.subList(0, cut));
                    tail.sources.addAll(g.lineSources.subList(cut, g.lineSources.size()));
                    String reason = String.format("split(第%d号片,自\"%s\"起): %s", ad.splitChunk(),
                            truncate(ad.beforeLine(), 20), ad.reason());
                    head.note.append(g.note).append(reason);
                    tail.note.append(g.note).append(reason);
                    groups.set(p, head);
                    groups.add(p + 1, tail);
                }
                case "keep" -> {
                    // 无操作，仅覆盖校验
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private int minAffected(Adjustment ad) {
        if (ad.chunks() != null && !ad.chunks().isEmpty()) return ad.chunks().get(0);
        return ad.splitChunk() != null ? ad.splitChunk() : Integer.MAX_VALUE;
    }

    private Map<Integer, Integer> buildPosOf(List<Group> groups) {
        Map<Integer, Integer> posOf = new HashMap<>();
        for (int p = 0; p < groups.size(); p++) {
            for (Integer idx : groups.get(p).sources) {
                posOf.putIfAbsent(idx, p);
            }
        }
        return posOf;
    }

    private void appendNote(Group g, String note) {
        if (g.note.length() > 0) g.note.append("；");
        g.note.append(note);
    }

    // ==================== 工具 ====================

    /** 受影响范围（from..to 源编号对应的所有组）的文本拼接，用于内容守恒校验 */
    private String flattenRange(List<Group> groups, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (Group g : groups) {
            boolean affected = g.sources.stream().anyMatch(i -> i >= from && i < to);
            if (affected) {
                sb.append(String.join("\n", g.lines)).append('\n');
            }
        }
        return sb.toString();
    }

    private List<Group> deepCopy(List<Group> groups) {
        List<Group> copy = new ArrayList<>(groups.size());
        for (Group g : groups) {
            Group c = new Group();
            c.lines.addAll(g.lines);
            c.lineSources.addAll(g.lineSources);
            c.sources.addAll(g.sources);
            c.note.append(g.note);
            copy.add(c);
        }
        return copy;
    }

    private boolean allReviewed(Group g, int reviewedUpTo) {
        return !g.sources.isEmpty() && g.sources.stream().allMatch(i -> i < reviewedUpTo);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
