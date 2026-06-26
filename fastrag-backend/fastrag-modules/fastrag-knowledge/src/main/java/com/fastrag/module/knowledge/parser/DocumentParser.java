package com.fastrag.module.knowledge.parser;

import java.io.InputStream;

public interface DocumentParser {
    ParseResult parse(InputStream fileStream, String extension, String strategyId);
}
