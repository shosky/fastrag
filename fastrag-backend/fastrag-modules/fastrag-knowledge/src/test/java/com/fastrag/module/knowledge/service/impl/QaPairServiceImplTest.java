package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.QaBatchConfirmResult;
import com.fastrag.module.publish.service.LogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * batchConfirm 单元测试（qa-pairs 2026-11-02 REQ-B04/B05）。
 *
 * <p>追溯：[Test] → QaPairService.batchConfirm（design/qa-pairs.md §2 契约种子）
 * → QaPairModule → spec REQ-B04（混选幂等跳过/单事务整批原子/≤10000 上限）与 REQ-B05（一条汇总审计）。
 * mock 边界 = 服务公共接口缝（mapper/transactionTemplate/logService），沿 QaGenerationServiceImplTest 先例。
 */
@ExtendWith(MockitoExtension.class)
class QaPairServiceImplTest {

    @Mock
    private KbQaPairMapper mapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private LogService logService;

    private QaPairServiceImpl svc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), KbQaPair.class);
        svc = new QaPairServiceImpl(mapper, transactionTemplate, logService);
    }

    /** 让 executeWithoutResult 直接执行回调（真事务语义以回调编排+异常传播断言，沿链内 mock 先例）；void 方法须用 doAnswer 词序 */
    private void runCallbackInline() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private KbQaPair pair(String id, String status) {
        KbQaPair p = new KbQaPair();
        p.setId(id);
        p.setKbId("kb1");
        p.setQuestion("问题" + id);
        p.setAnswer("答案");
        p.setSource("ai");
        p.setStatus(status);
        return p;
    }

    @Test
    void 批量确认_混选时_仅draft行确认且已confirmed行幂等跳过() {
        runCallbackInline();
        KbQaPair d1 = pair("d1", "draft");
        KbQaPair d2 = pair("d2", "confirmed");
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(d1, d2)));

        QaBatchConfirmResult result = svc.batchConfirm("kb1", List.of("d1", "d2"));

        assertEquals(1, result.getConfirmedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals("confirmed", d1.getStatus());
        verify(mapper, times(1)).updateById(any(KbQaPair.class));
    }

    @Test
    void 批量确认_不存在或跨库id_计入跳过不报错() {
        runCallbackInline();
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(pair("d1", "draft"))));

        QaBatchConfirmResult result = svc.batchConfirm("kb1", List.of("d1", "ghost"));

        assertEquals(1, result.getConfirmedCount());
        assertEquals(1, result.getSkippedCount());
    }

    @Test
    void 批量确认_超过10000条_业务错误拒绝且零写库() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 10001; i++) ids.add("id" + i);

        assertThrows(BusinessException.class, () -> svc.batchConfirm("kb1", ids));

        verify(mapper, never()).selectList(any());
        verify(transactionTemplate, never()).executeWithoutResult(any());
        verifyNoInteractions(logService);
    }

    @Test
    void 批量确认_空id列表_业务错误拒绝() {
        assertThrows(BusinessException.class, () -> svc.batchConfirm("kb1", List.of()));
        assertThrows(BusinessException.class, () -> svc.batchConfirm("kb1", List.of("  ", "")));

        verifyNoInteractions(mapper);
        verifyNoInteractions(logService);
    }

    @Test
    void 批量确认_事务内更新失败_异常传播且不写审计() {
        runCallbackInline();
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(pair("d1", "draft"))));
        when(mapper.updateById(any(KbQaPair.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> svc.batchConfirm("kb1", List.of("d1")));

        verify(logService, never()).addLog(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 批量确认成功_写一条汇总审计日志() {
        runCallbackInline();
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(pair("d1", "draft"))));

        svc.batchConfirm("kb1", List.of("d1"));

        verify(logService, times(1)).addLog(any(), any(), any(), any(), any(), any(), any());
    }
}
