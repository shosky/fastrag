package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaGenerationTask;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaGenerationTaskMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.model.QaGenerationTaskView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 问答对提炼编排服务测试（tdd/qa-generation.md S1/S2/S3/S5 切片）。
 *
 * <p>mock 边界：LlmService（缝 1）、MessagePublisher（缝 2）、mapper 层；
 * 复用件 normalizeQuestion / BenchmarkResponseParser 直用真实现。MyBatis-Plus TableInfo
 * 手工初始化仅为断言 delete wrapper 的 SQL 段（replace 语义只删 ai 来源）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QaGenerationServiceImplTest {

    @Mock private KbQaGenerationTaskMapper taskMapper;
    @Mock private KbQaPairMapper qaPairMapper;
    @Mock private KbChunkMapper chunkMapper;
    @Mock private KbFileMapper fileMapper;
    @Mock private LlmService llmService;
    @Mock private ObjectProvider<MessagePublisher> publisherProvider;
    @Mock private MessagePublisher publisher;
    @Mock private JdbcTemplate jdbcTemplate;

    private QaGenerationServiceImpl svc;

    @BeforeAll
    static void initMpMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), KbQaPair.class);
    }

    @BeforeEach
    void setUp() {
        svc = new QaGenerationServiceImpl(taskMapper, qaPairMapper, chunkMapper, fileMapper,
                llmService, publisherProvider, jdbcTemplate);
        ReflectionTestUtils.setField(svc, "staleMinutes", 30);
        ReflectionTestUtils.setField(svc, "llmTimeoutSeconds", 180);
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(fileMapper.selectById("f1")).thenReturn(file());
    }

    // ---- fixtures ----

    private KbFile file() {
        KbFile f = new KbFile();
        f.setId("f1");
        f.setKbId("kb1");
        f.setName("产品手册.pdf");
        f.setStatus("completed");
        return f;
    }

    private KbChunk parent(String id) {
        KbChunk c = new KbChunk();
        c.setId(id);
        c.setKbId("kb1");
        c.setFileId("f1");
        c.setFileName("产品手册.pdf");
        c.setChunkType("parent");
        c.setChunkIndex(0);
        c.setTitle("参数章节");
        c.setContent("片段内容-独特标记-" + id);
        return c;
    }

    private KbQaGenerationTask task(String status) {
        KbQaGenerationTask t = new KbQaGenerationTask();
        t.setFileId("f1");
        t.setKbId("kb1");
        t.setStatus(status);
        t.setTotalChunks(0);
        t.setSucceededChunks(0);
        t.setFailedChunks(0);
        t.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        t.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return t;
    }

    private KbQaPair pair(String source, String question) {
        KbQaPair p = new KbQaPair();
        p.setId("old-" + question.hashCode());
        p.setKbId("kb1");
        p.setFileId("f1");
        p.setQuestion(question);
        p.setAnswer("既有答案内容");
        p.setSource(source);
        p.setStatus("draft");
        return p;
    }

    /** 3 条合法问答 JSON（问题互异、长度过闸） */
    private String json3(String tag) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 1; i <= 3; i++) {
            if (i > 1) sb.append(",");
            sb.append("{\"question\":\"").append(tag).append("号问题第").append(i)
                    .append("条的标准问法是什么\",\"answer\":\"").append(tag).append(i)
                    .append("号问题的答案内容说明\"}");
        }
        return sb.append("]").toString();
    }

    private void mockLlm(String... responses) {
        Deque<String> queue = new ArrayDeque<>(List.of(responses));
        when(llmService.chatWithTimeout(anyString(), anyString(), nullable(String.class), nullable(String.class),
                any(), anyInt(), anyInt())).thenAnswer(inv -> queue.isEmpty() ? "" : queue.poll());
    }

    private void stubChunks(KbChunk... chunks) {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunks));
    }

    @SuppressWarnings("unchecked")
    private KbQaGenerationTask lastTaskUpdate() {
        ArgumentCaptor<KbQaGenerationTask> cap = ArgumentCaptor.forClass(KbQaGenerationTask.class);
        verify(taskMapper, atLeastOnce()).updateById(cap.capture());
        return cap.getValue();
    }

    // ==================== Tracer + S1 触发受理 ====================

    @Test
    void 受理_缺省参数时_落running并投递且零问答增删() {
        stubChunks(parent("p1"), parent("p2"));

        QaGenerationTaskView view = svc.trigger("kb1", "f1", null);

        assertEquals("running", view.getStatus());
        assertEquals(2, view.getTotalChunks());
        ArgumentCaptor<KbQaGenerationTask> cap = ArgumentCaptor.forClass(KbQaGenerationTask.class);
        verify(taskMapper).insert(cap.capture());
        assertEquals("running", cap.getValue().getStatus());
        QaGenerationConfig saved = QaGenerationConfig.fromJson(cap.getValue().getConfig());
        assertEquals(3, saved.getCount());
        assertEquals("replace", saved.getMode());
        assertNull(saved.getModel());
        verify(publisher, times(1)).publishQaGeneration(any());
        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        verify(qaPairMapper, never()).delete(any());
    }

    @Test
    void 受理_count越界0或11时_参数拒绝() {
        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", new QaGenerationConfig() {{ setCount(0); }}));
        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", new QaGenerationConfig() {{ setCount(11); }}));
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void 受理_自定义指令超500字符时_参数拒绝() {
        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setCustomInstruction("长".repeat(501));
        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", cfg));
    }

    @Test
    void 受理_文件跨库时_拒绝() {
        KbFile other = file();
        other.setKbId("kb2");
        when(fileMapper.selectById("f1")).thenReturn(other);
        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", null));
    }

    @Test
    void 受理_无父分片且未完成解析时_未就绪拒绝() {
        stubChunks();
        KbFile processing = file();
        processing.setStatus("processing");
        when(fileMapper.selectById("f1")).thenReturn(processing);
        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", null));
    }

    @Test
    void 受理_空文档已完成时_可受理零分片() {
        stubChunks();
        QaGenerationTaskView view = svc.trigger("kb1", "f1", null);
        assertEquals(0, view.getTotalChunks());
        assertEquals("running", view.getStatus());
    }

    @Test
    void 受理_已有新鲜running任务时_互斥拒绝且原行不动() {
        KbQaGenerationTask running = task("running");
        running.setUpdatedAt(LocalDateTime.now());
        when(taskMapper.selectById("f1")).thenReturn(running);
        stubChunks(parent("p1"));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", null));
        assertTrue(ex.getMessage().contains("进行中"));
        verify(taskMapper, never()).insert(any());
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    void 受理_MQ不可用时_任务落failed并报业务错误() {
        stubChunks(parent("p1"));
        when(publisherProvider.getIfAvailable()).thenReturn(null);

        assertThrows(BusinessException.class, () -> svc.trigger("kb1", "f1", null));

        KbQaGenerationTask t = lastTaskUpdate();
        assertEquals("failed", t.getStatus());
        assertTrue(t.getError().contains("队列不可用"));
    }

    @Test
    void 查询_从未触发时_返回空() {
        when(taskMapper.selectById("f1")).thenReturn(null);
        assertNull(svc.status("kb1", "f1"));
    }

    @Test
    void 查询_有历史任务时_返回全貌() {
        KbQaGenerationTask done = task("partial");
        done.setTotalChunks(3);
        done.setSucceededChunks(2);
        done.setFailedChunks(1);
        done.setError("部分产出被跳过或失败：1");
        when(taskMapper.selectById("f1")).thenReturn(done);

        QaGenerationTaskView view = svc.status("kb1", "f1");
        assertEquals("partial", view.getStatus());
        assertEquals(2, view.getSucceededChunks());
        assertEquals(1, view.getFailedChunks());
    }

    @Test
    void 受理_二次受理时_覆盖旧任务行() {
        KbQaGenerationTask done = task("success");
        when(taskMapper.selectById("f1")).thenReturn(done);
        stubChunks(parent("p1"));

        svc.trigger("kb1", "f1", null);

        ArgumentCaptor<KbQaGenerationTask> cap = ArgumentCaptor.forClass(KbQaGenerationTask.class);
        verify(taskMapper).updateById(cap.capture());
        assertEquals("running", cap.getValue().getStatus());
        assertEquals(0, cap.getValue().getSucceededChunks());
        verify(taskMapper, never()).insert(any());
    }

    // ==================== S2 生成编排与事务 ====================

    @Test
    void 执行_3分片每片3条时_success且产出9条溯源正确() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"), parent("p2"), parent("p3"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm(json3("A"), json3("B"), json3("C"));

        svc.execute("kb1", "f1", null);

        ArgumentCaptor<KbQaPair> cap = ArgumentCaptor.forClass(KbQaPair.class);
        verify(qaPairMapper, times(9)).insert(cap.capture());
        List<KbQaPair> inserted = cap.getAllValues();
        assertTrue(inserted.stream().allMatch(p -> "ai".equals(p.getSource()) && "draft".equals(p.getStatus())));
        assertEquals(3, inserted.stream().filter(p -> "p1".equals(p.getSourceChunkId())).count());
        assertEquals(3, inserted.stream().filter(p -> "p3".equals(p.getSourceChunkId())).count());
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals("success", finalTask.getStatus());
        assertEquals(3, finalTask.getSucceededChunks());
        assertEquals(0, finalTask.getFailedChunks());
    }

    @Test
    void 执行_prompt注入时_三约束自定义指令与分片可见() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm(json3("A"));

        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setCustomInstruction("只围绕参数出题");
        svc.execute("kb1", "f1", cfg);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmService).chatWithTimeout(anyString(), prompt.capture(), any(), any(), any(), anyInt(), anyInt());
        String p = prompt.getValue();
        assertTrue(p.contains("自包含"));
        assertTrue(p.contains("严禁引入片段之外的知识"));
        assertTrue(p.contains("空数组"));
        assertTrue(p.contains("只围绕参数出题"));
        assertTrue(p.contains("片段内容-独特标记-p1"));
    }

    @Test
    void 执行_分片无可答题材料时_零产出正常完结() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm("[]");

        svc.execute("kb1", "f1", null);

        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals("success", finalTask.getStatus());
        assertEquals(1, finalTask.getSucceededChunks());
    }

    @Test
    void 执行_坏JSON重试1次后_分片失败任务partial() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"), parent("p2"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        // 截断损坏 JSON（含 [] 但内容非法）→ 解析抛异常 → 重试 1 次仍败 → 分片失败
        mockLlm(json3("A"), "[abc]", "[abc]");

        svc.execute("kb1", "f1", null);

        verify(llmService, times(3)).chatWithTimeout(anyString(), anyString(), any(), any(), any(), anyInt(), anyInt());
        verify(qaPairMapper, times(3)).insert(any(KbQaPair.class));
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals("partial", finalTask.getStatus());
        assertEquals(1, finalTask.getSucceededChunks());
        assertEquals(1, finalTask.getFailedChunks());
    }

    @Test
    void 执行_replace完成时_删旧ai对先于产出且manual不动() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(
                pair("ai", "将被替换的旧问题内容是什么"),
                pair("manual", "人工维护的标准问题内容")));
        mockLlm(json3("A"));

        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setMode("replace");
        svc.execute("kb1", "f1", cfg);

        // 删旧（任务开始）先于 LLM 调用与产出落库（逐片实时落库，产出先于第二片的 LLM 调用可见）
        org.mockito.InOrder inOrder = inOrder(qaPairMapper, llmService);
        inOrder.verify(qaPairMapper).delete(any());
        inOrder.verify(llmService, atLeastOnce()).chatWithTimeout(anyString(), anyString(), any(), any(), any(), anyInt(), anyInt());
        inOrder.verify(qaPairMapper, atLeastOnce()).insert(any(KbQaPair.class));

        var delWrapper = verifyDeleteCapture();
        assertTrue(delWrapper.getSqlSegment().contains("source"));
        assertTrue(delWrapper.getParamNameValuePairs().containsValue("ai"));
        verify(qaPairMapper, times(3)).insert(any(KbQaPair.class));
    }

    /** 捕获 replace 模式的删除条件（唯一一次 delete 调用） */
    @SuppressWarnings("unchecked")
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbQaPair> verifyDeleteCapture() {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<KbQaPair>> delCap =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(qaPairMapper).delete(delCap.capture());
        return (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbQaPair>) delCap.getValue();
    }

    @Test
    void 执行_append完成时_不删旧对() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(pair("ai", "将被保留的旧问题内容")));
        mockLlm(json3("A"));

        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setMode("append");
        svc.execute("kb1", "f1", cfg);

        verify(qaPairMapper, never()).delete(any());
        verify(qaPairMapper, times(3)).insert(any(KbQaPair.class));
    }

    @Test
    void 执行_存量无父分片文件时_回退文本分片提取() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        KbChunk legacy = parent("legacy-text-0");
        legacy.setChunkType("text");
        // 第一次查询（父分片）为空，第二次查询（回退文本分片）返回存量分片
        when(chunkMapper.selectList(any())).thenReturn(List.of(), List.of(legacy));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm(json3("A"));

        svc.execute("kb1", "f1", null);

        ArgumentCaptor<KbQaPair> cap = ArgumentCaptor.forClass(KbQaPair.class);
        verify(qaPairMapper, times(3)).insert(cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(p -> "legacy-text-0".equals(p.getSourceChunkId())));
        assertEquals("产品手册.pdf", cap.getAllValues().get(0).getFileName());
        assertEquals("success", lastTaskUpdate().getStatus());
    }

    @Test
    void 执行_全部分片失败时_failed且零产出() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"), parent("p2"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm("[abc]", "[abc]",
                "[abc]", "[abc]");

        svc.execute("kb1", "f1", null);

        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals("failed", finalTask.getStatus());
        assertEquals(0, finalTask.getSucceededChunks());
        assertEquals(2, finalTask.getFailedChunks());
    }

    // ==================== S3 质量闸门与判重基线 ====================

    @Test
    void 闸门_全角产出遇库内半角同文时_判重跳过() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(pair("manual", "refund policy")));
        // 全角字母/全角空格 → 归一化后半角化，与库内半角同文撞题
        mockLlm("[{\"question\":\"ｒｅｆｕｎｄ　ｐｏｌｉｃｙ\",\"answer\":\"退款政策说明内容\"}]");

        svc.execute("kb1", "f1", null);

        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals(1, finalTask.getFailedChunks());
    }

    @Test
    void 闸门_replace模式同文件旧ai同题时_不入基线照常落库() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(pair("ai", "将被替换的旧问题内容是什么")));
        mockLlm("[{\"question\":\"将被替换的旧问题内容是什么\",\"answer\":\"新的答案内容说明\"}]");

        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setMode("replace");
        svc.execute("kb1", "f1", cfg);

        verify(qaPairMapper, times(1)).insert(any(KbQaPair.class));
    }

    @Test
    void 闸门_append模式同文件旧ai同题时_入基线跳过() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(pair("ai", "将被保留的旧问题内容是什么")));
        mockLlm("[{\"question\":\"将被保留的旧问题内容是什么\",\"answer\":\"重复的答案内容说明\"}]");

        QaGenerationConfig cfg = new QaGenerationConfig();
        cfg.setMode("append");
        svc.execute("kb1", "f1", cfg);

        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
    }

    @Test
    void 闸门_问题过短或答案过短时_护栏丢弃() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm("[{\"question\":\"参数\",\"answer\":\"x\"},{\"question\":\"正常的问题内容是什么\",\"answer\":\"答\"}]");

        svc.execute("kb1", "f1", null);

        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        KbQaGenerationTask finalTask = lastTaskUpdate();
        assertEquals(2, finalTask.getFailedChunks());
    }

    @Test
    void 闸门_manual同题时_两模式均跳过() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(pair("manual", "如何申请退款退款流程说明")));
        mockLlm("[{\"question\":\"如何申请退款退款流程说明\",\"answer\":\"退款流程的答案内容说明\"}]");
        stubChunks(parent("p1"));

        QaGenerationConfig replace = new QaGenerationConfig();
        replace.setMode("replace");
        svc.execute("kb1", "f1", replace);
        verify(qaPairMapper, never()).insert(any(KbQaPair.class));

        QaGenerationConfig append = new QaGenerationConfig();
        append.setMode("append");
        svc.execute("kb1", "f1", append);
        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
    }

    // ==================== S5 行态门与心跳 ====================

    @Test
    void 行态门_重投消息遇终态行时_静默丢弃零副作用() {
        when(taskMapper.selectById("f1")).thenReturn(task("success"));
        stubChunks(parent("p1"));

        svc.execute("kb1", "f1", null);

        verifyNoInteractions(llmService);
        verify(qaPairMapper, never()).insert(any(KbQaPair.class));
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    void 行态门_任务行缺失时_静默丢弃() {
        when(taskMapper.selectById("f1")).thenReturn(null);

        svc.execute("kb1", "f1", null);

        verifyNoInteractions(llmService);
        verifyNoInteractions(qaPairMapper);
    }

    @Test
    void 心跳_分片完结时_计数即时回写() {
        when(taskMapper.selectById("f1")).thenReturn(task("running"));
        stubChunks(parent("p1"), parent("p2"));
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        mockLlm(json3("A"), json3("B"));

        svc.execute("kb1", "f1", null);

        // 2 分片各一次心跳 + 1 次终态 = 至少 3 次回写
        verify(taskMapper, atLeast(3)).updateById(any(KbQaGenerationTask.class));
    }
}
