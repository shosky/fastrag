package com.fastrag.infra.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {
    private final MilvusServiceClient milvusClient;

    public void createCollection(String name, int dimension) {
        List<FieldType> fields = List.of(
            FieldType.newBuilder().withName("id").withDataType(DataType.VarChar).withMaxLength(64).withPrimaryKey(true).withAutoID(false).build(),
            FieldType.newBuilder().withName("vector").withDataType(DataType.FloatVector).withDimension(dimension).build(),
            FieldType.newBuilder().withName("kb_id").withDataType(DataType.VarChar).withMaxLength(32).build(),
            FieldType.newBuilder().withName("file_id").withDataType(DataType.VarChar).withMaxLength(32).build(),
            FieldType.newBuilder().withName("chunk_index").withDataType(DataType.Int64).build());
        milvusClient.createCollection(CreateCollectionParam.newBuilder().withCollectionName(name).withShardsNum(1)
            .addFieldType(fields.get(0)).addFieldType(fields.get(1)).addFieldType(fields.get(2))
            .addFieldType(fields.get(3)).addFieldType(fields.get(4)).build());
        milvusClient.createIndex(CreateIndexParam.newBuilder().withCollectionName(name).withFieldName("vector")
            .withIndexType(IndexType.HNSW).withMetricType(MetricType.L2)
            .withExtraParam("{\"M\":16,\"efConstruction\":256}").build());
    }

    public void insert(String collection, List<String> ids, List<List<Float>> vectors, String kbId, String fileId, List<Long> chunkIndices) {
        List<InsertParam.Field> fields = List.of(
            new InsertParam.Field("id", ids), new InsertParam.Field("vector", vectors),
            new InsertParam.Field("kb_id", Collections.nCopies(ids.size(), kbId)),
            new InsertParam.Field("file_id", Collections.nCopies(ids.size(), fileId)),
            new InsertParam.Field("chunk_index", chunkIndices));
        milvusClient.insert(InsertParam.newBuilder().withCollectionName(collection).withFields(fields).build());
    }

    public List<Map<String, Object>> search(String collection, List<Float> queryVector, int topK) {
        milvusClient.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
        R<SearchResults> result = milvusClient.search(SearchParam.newBuilder()
            .withCollectionName(collection).withVectors(List.of(queryVector)).withVectorFieldName("vector")
            .withTopK(topK).withMetricType(MetricType.L2).withParams("{\"ef\":128}")
            .withOutFields(List.of("id", "file_id", "chunk_index")).build());
        List<Map<String, Object>> results = new ArrayList<>();
        if (result.getData() != null) {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            for (SearchResultsWrapper.IDScore score : wrapper.getIDScore(0)) {
                results.add(Map.of("id", score.get("id"), "fileId", score.get("file_id"),
                    "chunkIndex", score.get("chunk_index"), "distance", score.getScore()));
            }
        }
        return results;
    }

    public void deleteByFileId(String collection, String fileId) {
        milvusClient.delete(DeleteParam.newBuilder().withCollectionName(collection)
            .withExpr("file_id == \"%s\"".formatted(fileId)).build());
    }
}
