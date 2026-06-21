package com.fastrag.module.retrieval.service.impl;
import com.fastrag.infra.elasticsearch.ESIndexService; import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.retrieval.model.*; import com.fastrag.module.retrieval.service.RetrievalService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Slf4j @Service @RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {
    private final ESIndexService esService; private final MilvusService milvusService;
    @Override public List<SearchResultItem> search(RetrievalRequest req) {
        String kbId=req.getKnowledgeId(); String query=req.getQuery();
        List<SearchResultItem> results=new ArrayList<>();
        try {
            var esResults=esService.search("kb_"+kbId,query,10);
            for(int i=0;i<esResults.size();i++) {
                var r=esResults.get(i);
                var item=new SearchResultItem(); item.setIndex(i); item.setContent((String)r.get("content"));
                item.setFileId((String)r.get("fileId")); item.setChunkIndex(r.get("chunkIndex")instanceof Integer?(int)r.get("chunkIndex"):0);
                item.setSimilarity(r.get("score")instanceof Double?(double)r.get("score"):0);
                item.setDistance(1.0-item.getSimilarity()); item.setSource("fulltext");
                if(r.containsKey("highlights"))item.setHighlights((List<String>)r.get("highlights"));
                results.add(item);
            }
        } catch(Exception e){log.error("Search error",e);}
        return results;
    }
    @Override public long getChunkCount(String kbId) { return 0; }
}
