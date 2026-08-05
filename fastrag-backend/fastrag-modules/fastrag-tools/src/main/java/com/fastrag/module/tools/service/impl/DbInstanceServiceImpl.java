package com.fastrag.module.tools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.tools.entity.DbInstance;
import com.fastrag.module.tools.entity.DbTable;
import com.fastrag.module.tools.mapper.DbInstanceMapper;
import com.fastrag.module.tools.mapper.DbTableMapper;
import com.fastrag.module.tools.service.DbInstanceService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import cn.hutool.json.JSONUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbInstanceServiceImpl implements DbInstanceService {

    private final DbInstanceMapper mapper;
    private final DbTableMapper tableMapper;

    /** 最大查询返回行数 */
    private static final int MAX_ROWS = 500;

    /** 数据库实例可见性：系统级（存量） / 属主 / 同组织 / API Token */
    private boolean visible(DbInstance db, LoginUser user) {
        return DataScope.visible(user, db.getCreatedBy(), db.getOrgId(), null);
    }

    private void requireManage(DbInstance db) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (db == null) throw BusinessException.notFound("数据库实例不存在");
        if (!DataScope.manageable(user, db.getCreatedBy())) throw BusinessException.forbidden("无权管理该数据库实例");
    }

    /** 凭据字段（密码/连接串）仅属主可见 */
    private void maskCredentials(DbInstance db, LoginUser user) {
        if (db != null && !DataScope.manageable(user, db.getCreatedBy())) {
            db.setPassword("******");
            db.setJdbcUrl(null);
        }
    }

    @Override
    public List<DbInstance> list(String keyword, String dbType) {
        var w = new LambdaQueryWrapper<DbInstance>();
        if (keyword != null && !keyword.isEmpty()) w.like(DbInstance::getName, keyword);
        if (dbType != null && !dbType.isEmpty()) w.eq(DbInstance::getDbType, dbType);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            w.and(q -> q.eq(DbInstance::getCreatedBy, user.getUserId())
                    .or(o -> o.eq(DbInstance::getOrgId, user.getOrgId())
                            .or().eq(DbInstance::getCreatedBy, "system")));
        }
        w.orderByDesc(DbInstance::getCreatedAt);
        List<DbInstance> list = mapper.selectList(w);
        list.forEach(db -> maskCredentials(db, user));
        return list;
    }

    @Override
    public DbInstance get(String id) {
        DbInstance db = mapper.selectById(id);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (db == null || !visible(db, user)) throw BusinessException.forbidden("无权访问该数据库实例");
        maskCredentials(db, user);
        return db;
    }

    @Override
    public DbInstance create(DbInstance db) {
        LoginUser user = SecurityUtil.getCurrentUser();
        db.setCreatedBy(user.getUserId());
        db.setOrgId(user.getOrgId());
        if (db.getStatus() == null) db.setStatus("disconnected");
        if (db.getReadOnly() == null) db.setReadOnly(1);
        // 自动拼接 JDBC URL
        if (db.getJdbcUrl() == null || db.getJdbcUrl().isBlank()) {
            db.setJdbcUrl(buildJdbcUrl(db));
        }
        mapper.insert(db);
        return db;
    }

    @Override
    public DbInstance update(String id, DbInstance db) {
        DbInstance existing = mapper.selectById(id);
        requireManage(existing);
        db.setId(id);
        if (db.getJdbcUrl() != null && !db.getJdbcUrl().isBlank()) {
            // 如果手动改了连接参数，重新拼接
            if (existing != null && (differentHost(db, existing) || differentPort(db, existing) || differentDb(db, existing))) {
                db.setJdbcUrl(buildJdbcUrl(db));
            }
        }
        mapper.updateById(db);
        return mapper.selectById(id);
    }

    private boolean differentHost(DbInstance a, DbInstance b) {
        return (a.getHost() != null && !a.getHost().equals(b.getHost()));
    }

    private boolean differentPort(DbInstance a, DbInstance b) {
        return (a.getPort() != null && !a.getPort().equals(b.getPort()));
    }

    private boolean differentDb(DbInstance a, DbInstance b) {
        return (a.getDbName() != null && !a.getDbName().equals(b.getDbName()));
    }

    @Override
    @Transactional
    public void delete(String id) {
        DbInstance db = mapper.selectById(id);
        requireManage(db);
        // 级联删除关联的 db_table 记录
        tableMapper.delete(new LambdaQueryWrapper<DbTable>().eq(DbTable::getDbId, id));
        mapper.deleteById(id);
    }

    @Override
    public List<DbTable> listTables(String dbId) {
        DbInstance db = mapper.selectById(dbId);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (db == null || !visible(db, user)) throw BusinessException.forbidden("无权访问该数据库实例");
        return tableMapper.selectList(
                new LambdaQueryWrapper<DbTable>().eq(DbTable::getDbId, dbId).orderByAsc(DbTable::getTableName));
    }

    @Override
    public DbTable createTable(DbTable t) {
        if (t.getEnabled() == null) t.setEnabled(1);
        tableMapper.insert(t);
        return t;
    }

    @Override
    public Map<String, Object> testConnection(String dbId) {
        Map<String, Object> result = new LinkedHashMap<>();
        DbInstance db = mapper.selectById(dbId);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (db == null || !visible(db, user)) throw BusinessException.forbidden("无权访问该数据库实例");
        if (db == null) {
            result.put("connected", false);
            result.put("error", "数据库实例不存在");
            return result;
        }
        long start = System.currentTimeMillis();
        try (HikariDataSource ds = createDataSource(db)) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                rs.next();
                long latency = System.currentTimeMillis() - start;
                result.put("connected", true);
                result.put("latencyMs", latency);
                // 更新实例状态
                DbInstance update = new DbInstance();
                update.setId(dbId);
                update.setStatus("connected");
                mapper.updateById(update);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("数据库连接测试失败, dbId={}, error={}", dbId, e.getMessage());
            result.put("connected", false);
            result.put("latencyMs", latency);
            result.put("error", e.getMessage());
            // 更新实例状态
            DbInstance update = new DbInstance();
            update.setId(dbId);
            update.setStatus("disconnected");
            mapper.updateById(update);
        }
        return result;
    }

    @Override
    public Map<String, Object> query(String dbId, String sql) {
        DbInstance db = mapper.selectById(dbId);
        requireManage(db); // 执行 SQL 仅属主可用
        Map<String, Object> result = new LinkedHashMap<>();

        // SQL 安全校验
        String trimmed = sql.trim();
        if (!isReadOnlySql(trimmed)) {
            result.put("error", "仅允许执行 SELECT / SHOW / DESCRIBE / EXPLAIN 语句");
            result.put("columns", List.of());
            result.put("rows", List.of());
            result.put("rowCount", 0);
            return result;
        }

        if (db == null) {
            result.put("error", "数据库实例不存在");
            result.put("columns", List.of());
            result.put("rows", List.of());
            result.put("rowCount", 0);
            return result;
        }
        long start = System.currentTimeMillis();
        try (HikariDataSource ds = createDataSource(db)) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
            jdbcTemplate.setMaxRows(MAX_ROWS);
            jdbcTemplate.setFetchSize(100);

            jdbcTemplate.query(trimmed, rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                // 列名
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                result.put("columns", columns);

                // 行数据
                List<Map<String, Object>> rows = new ArrayList<>();
                int rowCount = 0;
                while (rs.next() && rowCount < MAX_ROWS) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        row.put(columns.get(i - 1), val);
                    }
                    rows.add(row);
                    rowCount++;
                }
                result.put("rows", rows);
                result.put("rowCount", rowCount);
            });

            long elapsed = System.currentTimeMillis() - start;
            result.put("elapsedMs", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("SQL执行失败, dbId={}, sql={}, error={}", dbId, trimmed, e.getMessage());
            result.put("error", e.getMessage());
            result.put("columns", List.of());
            result.put("rows", List.of());
            result.put("rowCount", 0);
            result.put("elapsedMs", elapsed);
        }
        return result;
    }

    /**
     * 同步指定数据库实例的表元数据（从实际数据库获取）
     */
    public Map<String, Object> syncTables(String dbId) {
        Map<String, Object> result = new LinkedHashMap<>();
        DbInstance db = mapper.selectById(dbId);
        requireManage(db); // 同步表结构仅属主可用
        if (db == null) {
            result.put("error", "数据库实例不存在");
            result.put("syncedCount", 0);
            return result;
        }

        int syncedCount = 0;
        try (HikariDataSource ds = createDataSource(db)) {
            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schemaPattern = "mysql".equalsIgnoreCase(db.getDbType()) ? catalog : null;

                // 获取所有用户表
                try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        String tableComment = rs.getString("REMARKS");

                        // 获取列信息
                        List<Map<String, Object>> columnsInfo = new ArrayList<>();
                        try (ResultSet colRs = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
                            while (colRs.next()) {
                                Map<String, Object> col = new LinkedHashMap<>();
                                col.put("name", colRs.getString("COLUMN_NAME"));
                                col.put("type", colRs.getString("TYPE_NAME"));
                                col.put("size", colRs.getInt("COLUMN_SIZE"));
                                col.put("nullable", colRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                                col.put("comment", colRs.getString("REMARKS"));
                                columnsInfo.add(col);
                            }
                        }

                        // 获取行数
                        long rowCount = 0;
                        try (Statement stmt = conn.createStatement();
                             ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
                            if (countRs.next()) rowCount = countRs.getLong(1);
                        } catch (Exception e) {
                            log.warn("获取表行数失败, table={}, error={}", tableName, e.getMessage());
                        }

                        // 序列化列信息为 JSON 字符串
                        String columnsJson = JSONUtil.toJsonStr(columnsInfo);

                        // upsert: 查找是否已存在该表记录
                        DbTable existing = tableMapper.selectOne(
                                new LambdaQueryWrapper<DbTable>()
                                        .eq(DbTable::getDbId, dbId)
                                        .eq(DbTable::getTableName, tableName));

                        if (existing != null) {
                            existing.setColumns(columnsJson);
                            existing.setTableComment(tableComment);
                            existing.setRowCount(rowCount);
                            existing.setSyncedAt(java.time.LocalDateTime.now());
                            tableMapper.updateById(existing);
                        } else {
                            DbTable table = new DbTable();
                            table.setDbId(dbId);
                            table.setTableName(tableName);
                            table.setTableComment(tableComment);
                            table.setColumns(columnsJson);
                            table.setRowCount(rowCount);
                            table.setEnabled(1);
                            table.setSyncedAt(java.time.LocalDateTime.now());
                            tableMapper.insert(table);
                        }
                        syncedCount++;
                    }
                }

                result.put("syncedCount", syncedCount);
                // 更新实例状态
                DbInstance update = new DbInstance();
                update.setId(dbId);
                update.setStatus("connected");
                mapper.updateById(update);
            }
        } catch (Exception e) {
            log.error("同步表元数据失败, dbId={}, error={}", dbId, e.getMessage());
            result.put("error", e.getMessage());
            result.put("syncedCount", syncedCount);
            // 更新实例状态
            DbInstance update = new DbInstance();
            update.setId(dbId);
            update.setStatus("disconnected");
            mapper.updateById(update);
        }
        return result;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 动态创建 HikariCP 数据源
     */
    private HikariDataSource createDataSource(DbInstance db) {
        String url = db.getJdbcUrl();
        if (url == null || url.isBlank()) {
            url = buildJdbcUrl(db);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(db.getUsername());
        config.setPassword(db.getPassword());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setReadOnly(db.getReadOnly() != null && db.getReadOnly() == 1);
        config.setPoolName("db-" + db.getId() + "-" + System.currentTimeMillis());

        return new HikariDataSource(config);
    }

    /**
     * 根据数据库类型和连接信息拼接 JDBC URL
     */
    private String buildJdbcUrl(DbInstance db) {
        String type = db.getDbType() != null ? db.getDbType().toLowerCase() : "mysql";
        String host = db.getHost() != null ? db.getHost() : "127.0.0.1";
        int port = db.getPort() != null ? db.getPort() : getDefaultPort(type);
        String dbName = db.getDbName() != null ? db.getDbName() : "";

        return switch (type) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
            case "postgresql", "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
            case "h2" -> "jdbc:h2:mem:" + (dbName.isBlank() ? "test" : dbName) + ";MODE=MySQL";
            case "oracle" -> "jdbc:oracle:thin:@" + host + ":" + port + ":" + dbName;
            case "sqlserver", "mssql" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + dbName;
            default -> "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false";
        };
    }

    private int getDefaultPort(String dbType) {
        return switch (dbType) {
            case "mysql" -> 3306;
            case "postgresql", "postgres" -> 5432;
            case "h2" -> -1;
            case "oracle" -> 1521;
            case "sqlserver", "mssql" -> 1433;
            default -> 3306;
        };
    }

    /**
     * 校验 SQL 是否为只读语句
     */
    private boolean isReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String upper = sql.toUpperCase().trim();
        return upper.startsWith("SELECT")
                || upper.startsWith("SHOW")
                || upper.startsWith("DESCRIBE")
                || upper.startsWith("DESC")
                || upper.startsWith("EXPLAIN");
    }
}
