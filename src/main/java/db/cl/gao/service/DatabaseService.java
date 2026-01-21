package db.cl.gao.service;

import db.cl.gao.common.Constant;
import db.cl.gao.common.annotation.LogOperation;
import db.cl.gao.common.annotation.TrackSql;
import db.cl.gao.common.excep.DbException;
import db.cl.gao.common.mapper.OperationLogMapper;
import db.cl.gao.common.model.OperationLog;
import db.cl.gao.common.param.DatabaseContextHolder;
import db.cl.gao.config.DatabaseConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final DatabaseConfigManager configManager;
    private final DataSource defaultDataSource;
    private final OperationLogMapper operationLogMapper;
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        // 设置默认数据源
        configManager.setDefaultDataSource(defaultDataSource);
        jdbcTemplate = new JdbcTemplate(defaultDataSource);
        log.info("DatabaseService初始化完成，默认数据源: {}", defaultDataSource);
    }

    /**
     * 获取当前JdbcTemplate（根据上下文决定使用哪个数据库）
     */
    private JdbcTemplate getJdbcTemplate() {
        String dbKey = DatabaseContextHolder.getDatabase();
        if (dbKey == null || dbKey.isEmpty() || dbKey.equals("default")) {
            return jdbcTemplate;
        }

        DataSource dataSource = configManager.getDataSource(dbKey);
        return new JdbcTemplate(dataSource);
    }

    /**
     * 获取所有数据库列表（从配置中获取）
     */
    public List<String> getDatabases() {
        List<String> databases = new ArrayList<>();

        try {
            // 从配置管理器中获取所有数据库名称
            List<String> dbNames = configManager.getDatabaseNames();

            if (dbNames.isEmpty()) {
                // 如果没有配置，返回当前数据库
                String currentDb = getCurrentDatabaseName(jdbcTemplate);
                if (currentDb != null) {
                    databases.add(currentDb);
                }
            } else {
                databases.addAll(dbNames);
            }

        } catch (Exception e) {
            log.error("获取数据库列表失败", e);
            throw new DbException("获取数据库列表失败: " + e.getMessage());
        }

        return databases;
    }

    /**
     * 切换到指定数据库
     */
    public boolean switchDatabase(String database) {
        try {
            // 验证数据库是否存在
            if (!configManager.containsDatabase(database)) {
                log.warn("数据库配置不存在: {}", database);
                return false;
            }

            // 尝试连接数据库
            JdbcTemplate template = new JdbcTemplate(configManager.getDataSource(database));
            String result = template.queryForObject("SELECT 1", String.class);

            if ("1".equals(result)) {
                log.info("成功切换到数据库: {}", database);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("切换数据库失败: {}", database, e);
            return false;
        }
    }

    /**
     * 获取当前数据库名
     */
    private String getCurrentDatabaseName(JdbcTemplate template) {
        try {
            return template.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.error("获取当前数据库名失败", e);
            return null;
        }
    }

    /**
     * 获取所有表信息
     */
    public List<Map<String, Object>> getTables() {
        JdbcTemplate template = getJdbcTemplate();

        // 首先获取当前数据库名
        String currentDb = getCurrentDatabaseName(template);
        if (currentDb == null) {
            throw new DbException("无法获取当前数据库名");
        }

        String sql = "SELECT " +
                "TABLE_NAME, " +
                "TABLE_COMMENT, " +
                "TABLE_ROWS, " +
                "CREATE_TIME, " +
                "UPDATE_TIME, " +
                "DATA_LENGTH, " +
                "INDEX_LENGTH, " +
                "DATA_LENGTH + INDEX_LENGTH as TOTAL_SIZE " +
                "FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? " +
                "ORDER BY TABLE_NAME";

        log.debug("执行SQL: {}", sql);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> maps = template.queryForList(sql, currentDb);
        long end = System.currentTimeMillis() - start;
        log.debug("SQL执行完成，耗时: {}ms", end);
        operatorLog(currentDb, sql,"information_schema.TABLES",end);
        return maps;
    }

    private void operatorLog(String currentDb, String sql,String tableNme,long end) {
        OperationLog operationLog = new OperationLog();
        operationLog.setDatabaseName(currentDb);
        operationLog.setTableName(tableNme);
        operationLog.setSqlText(sql);
        operationLog.setOperationType(LogOperation.OperationType.QUERY.name());
        operationLog.setSuccess(true);
        operationLog.setCreatedBy("system");
        operationLog.setCreatedAt(new Date());
        operationLog.setExecuteTime(new Date(end));
        operationLogMapper.insert(operationLog);
    }

    /**
     * 获取表结构
     */
    public List<Map<String, Object>> getTableStructure(String tableName) {
        JdbcTemplate template = getJdbcTemplate();
        String currentDb = getCurrentDatabaseName(template);

        if (currentDb == null) {
            throw new DbException("无法获取当前数据库名");
        }

        String sql = "SELECT " +
                "COLUMN_NAME, " +
                "COLUMN_TYPE, " +
                "IS_NULLABLE, " +
                "COLUMN_KEY, " +
                "COLUMN_DEFAULT, " +
                "COLUMN_COMMENT, " +
                "EXTRA " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        log.debug("获取表结构: {}, SQL: {}", tableName, sql);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> maps = template.queryForList(sql, currentDb, tableName);
        long end = System.currentTimeMillis() - start;
        log.debug(" COLUMNS SQL执行完成，耗时: {}ms", end);
        operatorLog(currentDb, sql,"information_schema.COLUMNS",end);
        return maps;
    }

    /**
     * 获取表数据（支持分页和排序）
     */
    @LogOperation(type = LogOperation.OperationType.QUERY, value = "查询表数据")
    public Map<String, Object> getTableData(String tableName, int page, int size, String sortField, String sortOrder) {
        JdbcTemplate template = getJdbcTemplate();
        Map<String, Object> result = new HashMap<>();

        try {
            // 构建排序SQL
            String orderBy = "";
            if (StringUtils.hasText(sortField)) {
                // 简单的SQL注入防护
                if (!sortField.matches(Constant.TABLE_NAME_PATTERN.pattern())) {
                    throw new IllegalArgumentException("非法的排序字段: " + sortField);
                }
                orderBy = " ORDER BY " + sortField;
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    orderBy += " DESC";
                } else {
                    orderBy += " ASC";
                }
            }

            // 计算分页
            int offset = (page - 1) * size;
            String dataSql = String.format("SELECT * FROM %s%s LIMIT %d OFFSET %d",
                    tableName, orderBy, size, offset);

            // 获取数据
            List<Map<String, Object>> data = template.queryForList(dataSql);

            // 获取总数
            String countSql = String.format("SELECT COUNT(*) as total FROM %s", tableName);
            Integer total = template.queryForObject(countSql, Integer.class);

            if (total == null) {
                total = 0;
            }

            result.put(Constant.SUCCESS, true);
            result.put("data", data);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil((double) total / size));

        } catch (Exception e) {
            log.error("获取表数据失败: {}", tableName, e);
            result.put(Constant.SUCCESS, false);
            result.put(Constant.MESSAGE, "获取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 执行SQL查询
     */
    @TrackSql(sqlParam = "#sql", description = "执行SQL查询")
    @LogOperation(logParams = false)
    public Map<String, Object> executeQuery(String sql) {
        JdbcTemplate template = getJdbcTemplate();
        Map<String, Object> result = new HashMap<>();

        try {
            String originalSql = sql.trim();
            String upperSql = originalSql.toUpperCase();

            // 验证SQL安全性
            if (isUnsafeSql(upperSql)) {
                result.put(Constant.SUCCESS, false);
                result.put(Constant.MESSAGE, "不安全的SQL语句");
                return result;
            }

            // 判断是否是查询语句（SELECT, EXPLAIN, SHOW, DESC）
            boolean isQuery = upperSql.startsWith("SELECT") ||
                    upperSql.startsWith("EXPLAIN") ||
                    upperSql.startsWith("SHOW") ||
                    upperSql.startsWith("DESC") ||
                    upperSql.startsWith("DESCRIBE");

            if (isQuery) {
                // 查询语句
                List<Map<String, Object>> data = template.queryForList(originalSql);
                result.put(Constant.SUCCESS, true);
                result.put("data", data);
                result.put("count", data.size());
                result.put(Constant.MESSAGE, "查询成功，返回 " + data.size() + " 条记录");
            } else {
                int rows = template.update(originalSql);
                result.put(Constant.SUCCESS, true);
                result.put("rows", rows);
                result.put(Constant.MESSAGE, "操作成功，影响行数: " + rows);
            }

        } catch (Exception e) {
            log.error("执行SQL失败: {}", sql, e);
            result.put(Constant.SUCCESS, false);
            result.put(Constant.MESSAGE, "执行失败: " + e.getMessage());
        }

        return result;
    }

    // SQL安全检查（简化版）
    private boolean isUnsafeSql(String upperSql) {
        // 检查是否包含危险操作
        if (upperSql.contains("DROP DATABASE") ||
                upperSql.contains("DROP SCHEMA") ||
                upperSql.contains("TRUNCATE")) {
            return true;
        }

        // 检查DELETE和UPDATE是否包含WHERE条件
        return (upperSql.startsWith("DELETE") || upperSql.startsWith("UPDATE"))
                && !upperSql.contains("WHERE");
    }

    /**
     * 获取图表数据
     */
    @LogOperation(type = LogOperation.OperationType.QUERY, value = "获取图表数据")
    public Map<String, Object> getChartData(String sql, String chartType, String title) {
        JdbcTemplate template = getJdbcTemplate();
        Map<String, Object> result = new HashMap<>();

        try {
            // 验证SQL安全性
            String upperSql = sql.trim().toUpperCase();
            if (isSafeSql(upperSql) || !upperSql.startsWith("SELECT")) {
                result.put(Constant.SUCCESS, false);
                result.put(Constant.MESSAGE, "只允许SELECT查询语句");
                return result;
            }

            List<Map<String, Object>> data = template.queryForList(sql);

            if (data.isEmpty()) {
                result.put(Constant.SUCCESS, true);
                result.put("data", Collections.emptyList());
                result.put(Constant.MESSAGE, "查询成功，但无数据");
                return result;
            }

            // 构建图表数据
            Map<String, Object> chartResult = new HashMap<>();
            chartResult.put("title", title);
            chartResult.put("type", chartType);
            chartResult.put("rawData", data);

            // 根据图表类型格式化数据
            if ("pie".equals(chartType)) {
                chartResult.put(Constant.CHAT_DATA, formatPieChartData(data));
            } else if ("line".equals(chartType) || "bar".equals(chartType)) {
                chartResult.put(Constant.CHAT_DATA, formatLineBarChartData(data));
            } else if ("scatter".equals(chartType)) {
                chartResult.put(Constant.CHAT_DATA, formatScatterChartData(data));
            } else {
                chartResult.put(Constant.CHAT_DATA, data); // 默认返回原始数据
            }

            result.put(Constant.SUCCESS, true);
            result.put("data", chartResult);

        } catch (Exception e) {
            log.error("获取图表数据失败: {}", sql, e);
            result.put(Constant.SUCCESS, false);
            result.put(Constant.MESSAGE, "获取图表数据失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取数据库统计信息
     */
    public Map<String, Object> getDatabaseStats() {
        JdbcTemplate template = getJdbcTemplate();
        Map<String, Object> result = new HashMap<>();

        try {
            String currentDb = getCurrentDatabaseName(template);
            if (currentDb == null) {
                result.put(Constant.SUCCESS, false);
                result.put(Constant.MESSAGE, "无法获取当前数据库");
                return result;
            }

            // 基本统计
            String baseStatsSql = "SELECT " +
                    "(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?) as table_count, " +
                    "(SELECT SUM(TABLE_ROWS) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?) as row_count, " +
                    "(SELECT SUM(DATA_LENGTH + INDEX_LENGTH) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?) as total_size";

            Map<String, Object> baseStats = template.queryForMap(baseStatsSql, currentDb, currentDb, currentDb);

            // 表大小排名
            String sizeRankSql = "SELECT TABLE_NAME, TABLE_ROWS, " +
                    "DATA_LENGTH + INDEX_LENGTH as total_size " +
                    "FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = ? " +
                    "ORDER BY total_size DESC " +
                    "LIMIT 10";

            List<Map<String, Object>> sizeRank = template.queryForList(sizeRankSql, currentDb);

            result.put(Constant.SUCCESS, true);
            result.put("baseStats", baseStats);
            result.put("sizeRank", sizeRank);

        } catch (Exception e) {
            log.error("获取数据库统计失败", e);
            result.put(Constant.SUCCESS, false);
            result.put(Constant.MESSAGE, "获取统计信息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取图表示例
     */
    public List<Map<String, Object>> getChartExamples() {
        List<Map<String, Object>> examples = new ArrayList<>();

        examples.add(createExample(
                "按日期统计",
                "SELECT DATE(created_at) as date, COUNT(*) as count FROM your_table GROUP BY DATE(created_at) ORDER BY date",
                "line",
                "适合时间序列数据"
        ));

        examples.add(createExample(
                "分类统计",
                "SELECT category, COUNT(*) as count FROM products GROUP BY category ORDER BY count DESC",
                "pie",
                "适合显示占比"
        ));

        examples.add(createExample(
                "Top 10统计",
                "SELECT product_name, SUM(quantity) as total FROM sales GROUP BY product_name ORDER BY total DESC LIMIT 10",
                "bar",
                "适合排名数据"
        ));

        return examples;
    }

    // ============ 私有辅助方法 ============

    /**
     * 检查SQL安全性
     */
    private boolean isSafeSql(String sql) {
        List<String> dangerousKeywords = Arrays.asList(
                "DROP DATABASE", "SHUTDOWN", "GRANT", "REVOKE",
                "CREATE USER", "ALTER USER", "DROP USER", "FLUSH PRIVILEGES"
        );

        for (String keyword : dangerousKeywords) {
            if (sql.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 格式化饼图数据
     */
    private List<Map<String, Object>> formatPieChartData(List<Map<String, Object>> data) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        if (data.isEmpty()) return formatted;

        Set<String> keys = data.get(0).keySet();
        String[] keyArray = keys.toArray(new String[0]);

        if (keyArray.length >= 2) {
            for (Map<String, Object> row : data) {
                Map<String, Object> item = new HashMap<>();
                Object nameValue = row.get(keyArray[0]);
                Object valueValue = row.get(keyArray[1]);

                item.put("name", nameValue != null ? nameValue.toString() : "未知");
                item.put("value", valueValue != null ? valueValue : 0);
                formatted.add(item);
            }
        }
        return formatted;
    }

    /**
     * 格式化线图/柱状图数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> formatLineBarChartData(List<Map<String, Object>> data) {
        Map<String, Object> formatted = new HashMap<>();
        if (data.isEmpty()) return formatted;

        Set<String> keys = data.get(0).keySet();
        String[] keyArray = keys.toArray(new String[0]);

        List<Object> xAxis = new ArrayList<>();
        List<Map<String, Object>> series = new ArrayList<>();

        if (keyArray.length >= 2) {
            Map<String, Object> seriesItem = new HashMap<>();
            seriesItem.put("name", keyArray[1]);
            seriesItem.put("type", "line");
            seriesItem.put("data", new ArrayList<>());
            series.add(seriesItem);

            for (Map<String, Object> row : data) {
                xAxis.add(row.get(keyArray[0]));
                ((List<Object>) series.get(0).get("data")).add(row.get(keyArray[1]));
            }
        }

        formatted.put("xAxis", xAxis);
        formatted.put("series", series);
        return formatted;
    }

    /**
     * 格式化散点图数据
     */
    private List<Map<String, Object>> formatScatterChartData(List<Map<String, Object>> data) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        if (data.isEmpty()) return formatted;

        Set<String> keys = data.get(0).keySet();
        String[] keyArray = keys.toArray(new String[0]);

        if (keyArray.length >= 3) {
            for (Map<String, Object> row : data) {
                Map<String, Object> point = new HashMap<>();
                List<Object> value = new ArrayList<>();
                value.add(row.get(keyArray[0]));
                value.add(row.get(keyArray[1]));
                value.add(row.get(keyArray[2]));
                point.put("value", value);
                formatted.add(point);
            }
        }
        return formatted;
    }

    /**
     * 创建示例配置
     */
    private Map<String, Object> createExample(String title, String sql, String type, String desc) {
        Map<String, Object> example = new HashMap<>();
        example.put("title", title);
        example.put("sql", sql);
        example.put("type", type);
        example.put("description", desc);
        return example;
    }
}