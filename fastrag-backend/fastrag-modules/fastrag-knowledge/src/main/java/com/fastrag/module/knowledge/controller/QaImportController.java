package com.fastrag.module.knowledge.controller;

import com.fastrag.common.response.ApiResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 问答对导入相关控制器。
 *
 * <p>提供 Excel 模板下载能力，供用户导入问答对前下载标准模板。</p>
 */
@RestController
@RequestMapping("/api/kb/{kbId}/qa-pairs")
@RequiredArgsConstructor
@Slf4j
public class QaImportController {

    /**
     * 下载问答对导入模板。
     *
     * <p>生成一个包含标准表头的空 Excel 文件，前端以附件形式下载。</p>
     */
    @GetMapping("/import-template")
    public void downloadTemplate(@PathVariable String kbId, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = "qa-pairs-import-template.xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodedFileName);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(128);
             ServletOutputStream out = response.getOutputStream()) {

            Sheet sheet = wb.createSheet("问答对导入模板");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            // 普通单元格样式
            CellStyle cellStyle = wb.createCellStyle();
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            // 构建表头行
            String[] headers = {
                    "问题（必填）", "答案（必填）", "分类", "关键词（逗号分隔）",
                    "优先级", "状态", "生效时间", "失效时间"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 示例数据行（灰色背景提示）
            String[][] examples = {
                    {"如何申请退款？", "请在订单详情页点击\"申请退款\"按钮，填写退款原因后提交。",
                            "常见问题", "退款,退货", "5", "draft", "", ""},
                    {"忘记密码怎么办？", "点击登录页的\"忘记密码\"链接，输入注册邮箱或手机号重置。",
                            "账户相关", "密码,登录", "8", "confirmed", "", ""},
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(examples[r][c]);
                    cell.setCellStyle(cellStyle);
                }
            }

            // 自动列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, Math.min(40 * 256, 80 * 256));
            }

            wb.write(out);
            out.flush();
            log.info("[QaImport] 模板下载成功: kbId={}, fileName={}", kbId, fileName);
        } catch (Exception e) {
            log.error("[QaImport] 模板生成失败: kbId={}", kbId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
