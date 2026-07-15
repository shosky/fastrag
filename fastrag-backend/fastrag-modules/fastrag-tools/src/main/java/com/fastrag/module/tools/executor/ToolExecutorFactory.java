package com.fastrag.module.tools.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutorFactory {
    private final List<ToolExecutor> executors;

    public ToolExecutor getExecutor(String type) {
        return executors.stream()
            .filter(e -> e.getType().equals(type))
            .findFirst()
            .orElse(null);
    }
}
