package com.fastrag.module.knowledge.config;

import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.platform.service.ConfigManageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * excel_row 配置合并测试（REQ-H01/H04；tdd §5.2 S7 归一侧）。
 *
 * 断言：chunk.excelRow 嵌套组正确映射进 ChunkConfig.ExcelRow；strategy=excel_row 而
 * excelRow 组缺失时归一化为非空默认（rowsPerSlice=100）；单项非法值逐项容错沿用默认。
 */
@ExtendWith(MockitoExtension.class)
class ExcelRowConfigResolverTest {

    @Mock
    private KbParseStrategyMapper strategyMapper;

    @Mock
    private ConfigManageService configService;

    private static KbParseStrategy strategy(String advanced) {
        KbParseStrategy s = new KbParseStrategy();
        s.setId("s1");
        s.setAdvanced(advanced);
        return s;
    }

    @Test
    void excelRow嵌套组映射进ExcelRow() {
        StrategyConfigResolver resolver = new StrategyConfigResolver(strategyMapper, configService);
        lenient().when(configService.getConfig("general_chunk_size")).thenReturn(null);
        lenient().when(configService.getConfig("general_chunk_overlap")).thenReturn(null);

        ParseStrategyConfig config = resolver.resolve(strategy(
                "{\"chunk\":{\"strategy\":\"excel_row\",\"excelRow\":{\"headerStartRow\":1,\"headerEndRow\":2,\"sliceStartRow\":3,\"rowsPerSlice\":50}}}"));

        assertEquals("excel_row", config.getChunk().getStrategy());
        assertNotNull(config.getChunk().getExcelRow());
        assertEquals(1, config.getChunk().getExcelRow().getHeaderStartRow());
        assertEquals(2, config.getChunk().getExcelRow().getHeaderEndRow());
        assertEquals(3, config.getChunk().getExcelRow().getSliceStartRow());
        assertEquals(50, config.getChunk().getExcelRow().getRowsPerSlice());
    }

    @Test
    void excelRow组缺失时归一化为非空默认() {
        StrategyConfigResolver resolver = new StrategyConfigResolver(strategyMapper, configService);
        lenient().when(configService.getConfig("general_chunk_size")).thenReturn(null);
        lenient().when(configService.getConfig("general_chunk_overlap")).thenReturn(null);

        ParseStrategyConfig config = resolver.resolve(strategy("{\"chunk\":{\"strategy\":\"excel_row\"}}"));

        assertNotNull(config.getChunk().getExcelRow(), "strategy=excel_row 时 excelRow 组保证非空");
        assertEquals(100, config.getChunk().getExcelRow().getRowsPerSlice(), "rowsPerSlice 缺省 100（REQ-H04）");
    }

    @Test
    void 非excel_row策略不生成ExcelRow组() {
        StrategyConfigResolver resolver = new StrategyConfigResolver(strategyMapper, configService);
        lenient().when(configService.getConfig("general_chunk_size")).thenReturn(null);
        lenient().when(configService.getConfig("general_chunk_overlap")).thenReturn(null);

        ParseStrategyConfig config = resolver.resolve(strategy("{\"chunk\":{\"strategy\":\"rule_fixed\"}}"));

        assertNull(config.getChunk().getExcelRow(), "非 excel_row 不生成 excelRow 组（残留无副作用）");
    }

    @Test
    void excelRow单项非法值容错沿用默认() {
        StrategyConfigResolver resolver = new StrategyConfigResolver(strategyMapper, configService);
        lenient().when(configService.getConfig("general_chunk_size")).thenReturn(null);
        lenient().when(configService.getConfig("general_chunk_overlap")).thenReturn(null);

        // headerStartRow 为非法字符串 → 逐项容错沿用默认 0；其余合法项正常合并
        ParseStrategyConfig config = resolver.resolve(strategy(
                "{\"chunk\":{\"strategy\":\"excel_row\",\"excelRow\":{\"headerStartRow\":\"abc\",\"headerEndRow\":2,\"sliceStartRow\":3}}}"));

        assertNotNull(config.getChunk().getExcelRow());
        assertEquals(0, config.getChunk().getExcelRow().getHeaderStartRow(), "非法项容错沿用默认");
        assertEquals(2, config.getChunk().getExcelRow().getHeaderEndRow());
    }
}
