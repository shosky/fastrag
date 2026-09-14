package com.fastrag.module.knowledge.controller;

import com.fastrag.ai.imagegen.ImageGenResult;
import com.fastrag.ai.imagegen.ImageGenService;
import com.fastrag.ai.tts.TtsResult;
import com.fastrag.ai.tts.TtsService;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.model.KbTtsRequest;
import com.fastrag.module.knowledge.service.KnowledgeManageService;
import com.fastrag.module.knowledge.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}")
@RequiredArgsConstructor
public class KnowledgeManageController {
    private final KnowledgeManageService svc;
    private final MediaStorageService mediaStorageService;
    private final TtsService ttsService;
    private final ImageGenService imageGenService;

    // ===== M10 知识管理 =====
    @GetMapping("/knowledge") public ApiResponse<?> list(@PathVariable String kbId, @RequestParam(required=false) String keyword, @RequestParam(required=false) String category) { return ApiResponse.success(svc.list(kbId,keyword,category)); }
    @GetMapping("/knowledge/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping("/knowledge") public ApiResponse<?> create(@PathVariable String kbId, @RequestBody KbKnowledge knowledge) { knowledge.setKbId(kbId); return ApiResponse.success(svc.create(knowledge)); }
    @PutMapping("/knowledge/{id}") public ApiResponse<?> update(@PathVariable String id, @RequestBody KbKnowledge knowledge) { return ApiResponse.success(svc.update(id,knowledge)); }
    @DeleteMapping("/knowledge/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }

    // ===== 知识回收站（条目级软删） =====
    @GetMapping("/knowledge/deleted") public ApiResponse<?> deleted(@PathVariable String kbId) { return ApiResponse.success(svc.listDeleted(kbId)); }
    @PostMapping("/knowledge/{id}/restore") public ApiResponse<?> restore(@PathVariable String id) { svc.restore(id); return ApiResponse.success(); }
    @DeleteMapping("/knowledge/{id}/permanent") public ApiResponse<?> permanentDelete(@PathVariable String id) { svc.permanentDelete(id); return ApiResponse.success(); }
    @DeleteMapping("/knowledge/recycle-bin") public ApiResponse<?> emptyRecycleBin(@PathVariable String kbId) { svc.emptyRecycleBin(kbId); return ApiResponse.success(); }

    // ===== AI 能力：封面图生成 =====
    @PostMapping("/knowledge/{id}/cover-image/generate")
    public ApiResponse<?> generateCoverImage(@PathVariable String kbId,
                                             @PathVariable("id") String knowledgeId,
                                             @RequestBody Map<String, String> body) {
        String prompt = body == null ? "" : body.getOrDefault("prompt", "");
        String imageSize = body == null ? null : body.getOrDefault("imageSize", null);
        Long seed = null;
        try {
            if (body != null && body.get("seed") != null && !body.get("seed").isBlank()) {
                seed = Long.parseLong(body.get("seed"));
            }
        } catch (NumberFormatException ignore) { /* ignore invalid seed */ }
        if (prompt.isBlank()) {
            // 用知识真实内容驱动 prompt，而不是只看标题
            var k = svc.get(knowledgeId);
            prompt = buildCoverPrompt(k);
        }
        ImageGenResult result = imageGenService.generate(prompt, imageSize, seed);
        KbMediaStorage media = mediaStorageService.saveBytes(
                kbId, "image", result.getImageBytes(),
                result.getMimeType() != null && result.getMimeType().endsWith("png") ? "png" : "jpg",
                "ai_image_gen", "AI 生成封面图：" + prompt);
        svc.updateCoverImage(knowledgeId, media.getObjectKey());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mediaId", media.getId());
        resp.put("objectKey", media.getObjectKey());
        resp.put("imageUrl", "/api/kb/" + kbId + "/media/images/" + media.getId() + "/download");
        resp.put("prompt", prompt);
        return ApiResponse.success(resp);
    }

    /**
     * 用知识内容生成 prompt：先看 summary（最精炼），再 fallback 到 content 截断前 240 字符，
     * 最后才是 title。拼上 category 风格提示，去掉 markdown 噪声，让生成图与内容真正相关。
     */
    private String buildCoverPrompt(KbKnowledge k) {
        if (k == null) return "knowledge illustration, modern flat design";
        String summary = clean(k.getSummary());
        String content = clean(k.getContent());
        String title   = clean(k.getTitle());
        String category = clean(k.getCategory());

        String body;
        if (!summary.isEmpty()) {
            body = summary;
        } else if (!content.isEmpty()) {
            body = content.length() > 240 ? content.substring(0, 240) : content;
        } else if (!title.isEmpty()) {
            body = title;
        } else {
            body = "knowledge illustration";
        }

        String styleHint = switch (category) {
            case "业务流程" -> "flat business process illustration, clean icons, professional blue tone";
            case "产品资料" -> "product showcase illustration, modern, soft gradient";
            case "技术文档" -> "technical schematic illustration, minimalist, isometric";
            case "规章制度" -> "official policy illustration, formal, balanced composition";
            case "问答抽取" -> "FAQ illustration, friendly, conversational";
            default -> "modern flat illustration, professional";
        };
        return styleHint + ". Subject: " + body + ", no text, no letters, no Chinese characters, illustration only";
    }

    /** 去掉 markdown/换行/多余空白，给图像生成模型更干净的输入 */
    private String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[#*`>\\-_\\[\\]()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ===== AI 能力：知识朗读（TTS 下载） =====
    @PostMapping("/knowledge/{id}/tts")
    public ApiResponse<?> generateTts(@PathVariable String kbId,
                                     @PathVariable("id") String knowledgeId,
                                     @RequestBody(required = false) KbTtsRequest req) {
        if (req == null) req = new KbTtsRequest();
        String text = req.getText();
        if (text == null || text.isBlank()) {
            text = svc.getContent(knowledgeId);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("知识条目内容为空，且未提供朗读文本");
        }
        // 截断过长文本，避免超出 TTS 模型限制
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        TtsResult result = ttsService.synthesize(text, req.getVoice());
        KbMediaStorage media = mediaStorageService.saveBytes(
                kbId, "audio", result.getAudioBytes(), "mp3",
                "ai_tts", "知识朗读（" + (text.length() > 30 ? text.substring(0, 30) + "..." : text) + "）");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mediaId", media.getId());
        resp.put("objectKey", media.getObjectKey());
        resp.put("audioUrl", "/api/kb/" + kbId + "/media/audios/" + media.getId() + "/download");
        resp.put("contentType", result.getContentType());
        resp.put("textLength", text.length());
        resp.put("voice", req.getVoice() == null ? "default" : req.getVoice());
        return ApiResponse.success(resp);
    }

    // 知识测试
    @GetMapping("/knowledge-tests") public ApiResponse<?> tests(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.listTests(kbId,knowledgeId)); }
    @PostMapping("/knowledge-tests") public ApiResponse<?> createTest(@PathVariable String kbId, @RequestBody KbKnowledgeTest test) { test.setKbId(kbId); return ApiResponse.success(svc.createTest(test)); }
    @PutMapping("/knowledge-tests/{id}") public ApiResponse<?> updateTest(@PathVariable String id, @RequestBody KbKnowledgeTest test) { return ApiResponse.success(svc.updateTest(id,test)); }
    @DeleteMapping("/knowledge-tests/{id}") public ApiResponse<?> deleteTest(@PathVariable String id) { svc.deleteTest(id); return ApiResponse.success(); }
    // 知识对话
    @GetMapping("/knowledge-dialogs") public ApiResponse<?> dialogs(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.listDialogs(kbId,knowledgeId)); }
    @PostMapping("/knowledge-dialogs") public ApiResponse<?> createDialog(@PathVariable String kbId, @RequestBody KbKnowledgeDialog dialog) { dialog.setKbId(kbId); return ApiResponse.success(svc.createDialog(dialog)); }
    @DeleteMapping("/knowledge-dialogs/{id}") public ApiResponse<?> deleteDialog(@PathVariable String id) { svc.deleteDialog(id); return ApiResponse.success(); }
    @PostMapping("/knowledge-dialogs/{id}/judge") public ApiResponse<?> judge(@PathVariable String id, @RequestBody Map<String,String> body) { return ApiResponse.success(svc.judge(id,body.get("query"),body.get("model"))); }
}
