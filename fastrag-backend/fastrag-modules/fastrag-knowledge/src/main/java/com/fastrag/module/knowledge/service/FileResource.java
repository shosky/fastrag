package com.fastrag.module.knowledge.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@AllArgsConstructor
public class FileResource {
    private String fileName;
    private Resource resource;
}
