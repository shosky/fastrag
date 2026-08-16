package com.fastrag.module.knowledge.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.Resource;

/**
 * 文件下载资源封装。
 *
 * <p>封装文件下载接口的返回内容，包含原始文件名和Spring {@link Resource} 资源对象。
 * 用于知识库文件下载时向前端提供带原始文件名的文件流。</p>
 */
@Getter
@AllArgsConstructor
public class FileResource {
    private String fileName;
    private Resource resource;
}
