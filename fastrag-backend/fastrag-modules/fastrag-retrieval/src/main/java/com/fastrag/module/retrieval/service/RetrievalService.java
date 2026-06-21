package com.fastrag.module.retrieval.service;
import com.fastrag.module.retrieval.model.*;
import java.util.List;
public interface RetrievalService { List<SearchResultItem> search(RetrievalRequest req); long getChunkCount(String kbId); }
