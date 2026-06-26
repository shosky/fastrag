package com.fastrag.module.knowledge.parser;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParseResult {
    private String text;
    private int pages;
    private String metadata;
}
