package com.fastrag.module.operation.service;
import com.fastrag.module.operation.entity.DataMiningTask; import java.util.*;
public interface DataMiningService {
    List<DataMiningTask> list(String kbId,String keyword);
    DataMiningTask get(String id);
    DataMiningTask create(DataMiningTask task);
    void delete(String id);
    DataMiningTask run(String id);
}
