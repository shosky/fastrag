package com.fastrag.module.knowledge.parser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将结构化文档节点列表 (List<DocNode>) 序列化为 Markdown 字符串。
 *
 * 作为分片的输入格式和 LLM 的上下文格式，保留完整文档结构信息：
 * - 标题层级 (# ...)
 * - 表格 (| ... | ... |)
 * - 图片 (![caption](key))
 * - 代码块 (```language ... ```)
 * - 数学公式 ($$...$$)
 */
@Component
public class MarkdownSerializer {

    /**
     * 将节点列表序列化为 Markdown 全文
     */
    public String serialize(List<DocNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (DocNode node : nodes) {
            String line = serializeNode(node);
            if (line != null && !line.isEmpty()) {
                sb.append(line).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 将单个节点序列化为 Markdown 片段
     */
    public String serializeNode(DocNode node) {
        if (node == null) return "";
        return switch (node.getType()) {
            case HEADING -> "#".repeat(Math.min(node.getLevel(), 6)) + " " + node.getTitle();
            case PARAGRAPH -> node.getContent() != null ? node.getContent() : "";
            case TABLE -> serializeTable(node);
            case IMAGE -> "![" + (node.getImageCaption() != null ? node.getImageCaption() : "图片") + "](" + node.getImageKey() + ")";
            case CODE_BLOCK -> "```" + (node.getCodeLanguage() != null ? node.getCodeLanguage() : "") + "\n"
                    + (node.getContent() != null ? node.getContent() : "") + "\n```";
            case DISPLAY_MATH -> "$$" + (node.getLatex() != null ? node.getLatex() : "") + "$$";
            case INLINE_MATH -> "$" + (node.getLatex() != null ? node.getLatex() : "") + "$";
            case LIST -> serializeList(node);
            case LIST_ITEM -> "- " + (node.getContent() != null ? node.getContent() : "");
            case HORIZONTAL_RULE -> "---";
            case PAGE_BREAK -> ""; // Markdown 中不需要显式标记
        };
    }

    /**
     * 序列化表格节点为 Markdown 表格语法
     */
    private String serializeTable(DocNode node) {
        List<String> headers = node.getHeaders();
        List<List<String>> rows = node.getRows();
        if (headers == null || headers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("| ").append(headers.stream().map(h -> h != null ? h : "").collect(Collectors.joining(" | "))).append(" |\n");
        // 分隔行
        sb.append("|").append(" --- |".repeat(headers.size())).append("\n");
        // 数据行
        if (rows != null) {
            for (List<String> row : rows) {
                if (row == null || row.isEmpty()) continue;
                // 确保每行列数与表头一致，不足补空
                while (row.size() < headers.size()) {
                    row.add("");
                }
                sb.append("| ").append(row.stream().map(c -> c != null ? c : "").collect(Collectors.joining(" | "))).append(" |\n");
            }
        }
        return sb.toString();
    }

    /**
     * 序列化列表节点
     * 列表项紧随 LIST 节点之后，顺序遍历即可
     */
    private String serializeList(DocNode node) {
        // 纯文本列表直接输出为多行 "- " 格式
        // 如果是嵌套列表通过 content 传递 Markdown 格式文本
        return node.getContent() != null ? node.getContent() : "";
    }

    /**
     * 剥离 Markdown 语法字符，计算纯文本长度。
     * 用于分片时判断 chunk 是否超限，避免 Markdown 语法字符导致误判。
     */
    public int plainTextLength(String markdown) {
        if (markdown == null || markdown.isEmpty()) return 0;
        String stripped = stripMarkdownSyntax(markdown);
        return stripped.length();
    }

    /**
     * 剥离 Markdown 语法字符：表格分隔线 (|, -), 代码块标记 (```),
     * 数学公式标记 ($), 标题标记 (#), 图片/链接标记等。
     */
    public String stripMarkdownSyntax(String markdown) {
        if (markdown == null) return "";
        String text = markdown;
        // 移除代码块标记 ```language ... ```
        text = text.replaceAll("```[a-zA-Z]*\n?", "");
        // 移除行内代码标记 `...`
        text = text.replaceAll("`[^`]+`", "");
        // 移除行间公式 $$...$$
        text = text.replaceAll("\\$\\$[^$]+\\$\\$", "");
        // 移除行内公式 $...$
        text = text.replaceAll("\\$[^$]+\\$", "");
        // 移除标题标记（每行行首）
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");
        // 移除表格分隔线行 (|---|)
        text = text.replaceAll("(?m)^\\|[\\s-]+\\|.*$", "");
        // 移除图片标记 ![alt](url)
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
        // 移除链接 [text](url)
        text = text.replaceAll("\\[[^\\]]*\\]\\([^)]*\\)", "");
        // 移除列表标记（每行行首）
        text = text.replaceAll("(?m)^[-*+]\\s+", "");
        // 移除水平线（行首 --- ）
        text = text.replaceAll("(?m)^---+$", "");
        return text.trim();
    }
}
