package com.fastrag.infra.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.param.*;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务
 * 连接 Milvus 实例，提供 Collection 创建、向量插入、相似度搜索、按文件删除等功能。
 */
@Slf4j
@Service
public class MilvusService {

    @Value("${milvus.host:127.0.0.1}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    private MilvusServiceClient milvusClient;

    @PostConstruct
    public void init() {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(10, TimeUnit.SECONDS)   // 连接超时 10 秒
                    .withRpcDeadline(15, TimeUnit.SECONDS)      // RPC 调用超时 15 秒
                    .build();
            milvusClient = new MilvusServiceClient(connectParam);

            // 验证连接是否可用
            try {
                R<Boolean> hasR = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                        .withCollectionName("_milvus_ping_")
                        .build());
                if (hasR.getStatus() == R.Status.Success.getCode()) {
                    log.info("Milvus client connected to {}:{}", host, port);
                } else {
                    log.warn("Milvus connect to {}:{} but ping failed: {}", host, port, hasR.getMessage());
                    milvusClient.close();
                    milvusClient = null;
                }
            } catch (Exception e) {
                log.warn("Milvus server {}:{} not reachable, vector storage disabled: {}", host, port, e.getMessage());
                try { milvusClient.close(); } catch (Exception ignored) {}
                milvusClient = null;
            }
        } catch (Exception e) {
            log.error("Milvus client initialization failed: {}:{}", host, port, e);
            milvusClient = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (milvusClient != null) {
            try {
                milvusClient.close();
                log.info("Milvus client closed");
            } catch (Exception e) {
                log.warn("Failed to close Milvus client", e);
            }
        }
    }

    /**
     * 创建 Collection（如不存在）
     */
    public void createCollection(String name, int dimension) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, skip createCollection: {}", name);
            return;
        }
        try {
            R<Boolean> hasR = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(name).build());
            if (hasR.getData() != null && hasR.getData()) {
                log.debug("Milvus collection already exists: {}", name);
                return;
            }

            List<FieldType> fields = new ArrayList<>();

            // 主键字段
            fields.add(FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(128)
                    .withPrimaryKey(true)
                    .build());

            // 向量字段
            fields.add(FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build());

            // kbId 字段
            fields.add(FieldType.newBuilder()
                    .withName("kb_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build());

            // fileId 字段
            fields.add(FieldType.newBuilder()
                    .withName("file_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build());

            // chunkIndex 字段
            fields.add(FieldType.newBuilder()
                    .withName("chunk_index")
                    .withDataType(DataType.Int64)
                    .build());

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(name)
                    .withDescription("Knowledge base chunks")
                    .withFieldTypes(fields)
                    .build();

            R<RpcStatus> createR = milvusClient.createCollection(createParam);
            if (createR.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus collection created: {}, dimension={}", name, dimension);

                // 创建向量索引 (IVF_FLAT)
                R<RpcStatus> indexR = milvusClient.createIndex(CreateIndexParam.newBuilder()
                        .withCollectionName(name)
                        .withFieldName("vector")
                        .withIndexType(IndexType.IVF_FLAT)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam("{\"nlist\":128}")
                        .build());
                if (indexR.getStatus() == R.Status.Success.getCode()) {
                    log.info("Milvus vector index created on collection: {}", name);
                } else {
                    log.warn("Milvus vector index creation failed for {}: {}", name, indexR.getMessage());
                }

                // 加载集合
                milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(name).build());
                log.info("Milvus collection loaded: {}", name);
            } else {
                log.warn("Milvus collection creation failed for {}: {}", name, createR.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus createCollection failed for {}: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 插入向量数据
     */
    public void insert(String collection, List<String> ids, List<List<Float>> vectors,
                       String kbId, String fileId, List<Long> chunkIndices) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, skip insert into: {}", collection);
            return;
        }
        try {
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", ids));
            fields.add(new InsertParam.Field("vector", vectors));

            List<String> kbIds = ids.stream().map(id -> kbId).collect(Collectors.toList());
            fields.add(new InsertParam.Field("kb_id", kbIds));

            List<String> fileIds = ids.stream().map(id -> fileId).collect(Collectors.toList());
            fields.add(new InsertParam.Field("file_id", fileIds));

            List<Long> indices = chunkIndices != null ? chunkIndices :
                    ids.stream().map(id -> 0L).collect(Collectors.toList());
            fields.add(new InsertParam.Field("chunk_index", indices));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collection)
                    .withFields(fields)
                    .build();

            R<MutationResult> insertR = milvusClient.insert(insertParam);
            if (insertR.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus inserted {} vectors into {}", ids.size(), collection);
            } else {
                log.error("Milvus insert failed for {}: {}", collection, insertR.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus insert failed for {}: {}", collection, e.getMessage(), e);
        }
    }

    /**
     * 向量相似度搜索
     */
    public List<Map<String, Object>> search(String collection, List<Float> queryVector, int topK) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, returning empty results for: {}", collection);
            return Collections.emptyList();
        }
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collection)
                    .withVectorFieldName("vector")
                    .withTopK(topK)
                    .withMetricType(MetricType.COSINE)
                    .withVectors(Collections.singletonList(queryVector))
                    .withOutFields(Collections.singletonList("chunk_index"))
                    .build();

            R<io.milvus.grpc.SearchResults> searchR = milvusClient.search(searchParam);
            if (searchR.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus search failed for {}: {}", collection, searchR.getMessage());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchR.getData().getResults());
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
                Map<String, Object> item = new HashMap<>();
                SearchResultsWrapper.IDScore idScore = wrapper.getIDScore(0).get(i);
                item.put("id", idScore.getStrID());
                item.put("score", idScore.getScore());
                item.put("fieldValues", idScore.getFieldValues());
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.error("Milvus search failed for {}: {}", collection, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 按文件 ID 删除向量数据
     */
    public void deleteByFileId(String collection, String fileId) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, skip deleteByFileId: {} in {}", fileId, collection);
            return;
        }
        try {
            String expr = "file_id == \"" + fileId + "\"";
            R<MutationResult> deleteR = milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(collection)
                    .withExpr(expr)
                    .build());
            if (deleteR.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus deleted vectors for fileId={} in collection={}", fileId, collection);
            } else {
                log.warn("Milvus delete failed for fileId={} in {}: {}", fileId, collection, deleteR.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus deleteByFileId failed for fileId={} in {}: {}", fileId, collection, e.getMessage(), e);
        }
    }
}
