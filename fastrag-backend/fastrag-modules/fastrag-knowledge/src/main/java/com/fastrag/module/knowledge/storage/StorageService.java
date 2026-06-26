package com.fastrag.module.knowledge.storage;

import com.fastrag.module.knowledge.chunking.ChunkData;
import java.util.List;

public interface StorageService {
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks);
}
