package com.fastrag.module.knowledge.model;

import com.fastrag.module.knowledge.entity.KbChunk;
import lombok.Data;

/**
 * 分片 DTO，用于接口返回
 */
@Data
public class ChunkDto {
    private String id;
    private String kbId;
    private String fileId;
    private String fileName;
    private int chunkIndex;
    private String content;
    private String embeddingId;
    private Integer vectorStored;
    private Double startTime;
    private Double endTime;
    private Integer pageNumber;
    private String pageRange;
    private String imageKeys;
    private String chunkType;
    private Integer graphIndexed;
    private String extractionResult;

    /**
     * 从 KbChunk 实体转换为 DTO
     */
    public static ChunkDto toDto(KbChunk e) {
        if (e == null) return null;
        ChunkDto d = new ChunkDto();
        d.setId(e.getId());
        d.setKbId(e.getKbId());
        d.setFileId(e.getFileId());
        d.setFileName(e.getFileName());
        d.setChunkIndex(e.getChunkIndex() != null ? e.getChunkIndex() : 0);
        d.setContent(e.getContent());
        d.setEmbeddingId(e.getEmbeddingId());
        d.setVectorStored(e.getVectorStored());
        d.setStartTime(e.getStartTime());
        d.setEndTime(e.getEndTime());
        d.setPageNumber(e.getPageNumber());
        d.setPageRange(e.getPageRange());
        d.setImageKeys(e.getImageKeys());
        d.setChunkType(e.getChunkType());
        d.setGraphIndexed(e.getGraphIndexed());
        d.setExtractionResult(e.getExtractionResult());
        return d;
    }
}
