package db.cl.gao.service;
import db.cl.gao.common.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
public class DatabaseService {


    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * 获取所有表信息
     */
    public List<Map<String, Object>> getTables() {
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
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "ORDER BY TABLE_NAME";

        log.debug("执行SQL: {}", sql);
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 获取表结构
     */
    public List<Map<String, Object>> getTableStructure(String tableName) {

        String sql = "SELECT " +
                "COLUMN_NAME, " +
                "COLUMN_TYPE, " +
                "IS_NULLABLE, " +
                "COLUMN_KEY, " +
                "COLUMN_DEFAULT, " +
                "COLUMN_COMMENT, " +
                "EXTRA " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        log.debug("获取表结构: {}, SQL: {}", tableName, sql);
        return jdbcTemplate.queryForList(sql, tableName);
    }

    /**
     * 获取表数据（支持分页和排序）
     */
    public Map<String, Object> getTableData(String tableName, int page, int size, String sortField, String sortOrder) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 构建排序SQL
            String orderBy = "";
            if (StringUtils.hasText(sortField)) {
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
            List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql);

            // 获取总数
            String countSql = String.format("SELECT COUNT(*) as total FROM %s", tableName);
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            
            if(total ==  null ){
                total  = 0;
            }
            
            log.debug("获取表数据: {}, SQL: {}, 结果: {}", tableName, dataSql, data);

            // 获取主键信息
            List<String> primaryKeys = getPrimaryKeys(tableName);

            result.put(Constant.SUCCESS, true);
            result.put("data", data);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil((double) total / size));
            result.put("primaryKeys", primaryKeys);

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
    public Map<String, Object> executeQuery(String sql) {
        Map<String, Object> result = new HashMap<>();

        try {
            String upperSql = sql.trim().toUpperCase();

            // 验证SQL安全性（简单检查）
            if (!isSafeSql(upperSql)) {
                result.put(Constant.SUCCESS, false);
                result.put(Constant.MESSAGE, "不安全的SQL语句");
                return result;
            }

            if (upperSql.startsWith("SELECT")) {
                // 查询操作
                List<Map<String, Object>> data = jdbcTemplate.queryForList(sql);
                result.put(Constant.SUCCESS, true);
                result.put("data", data);
                result.put("type", "query");
                result.put("count", data.size());
                result.put(Constant.MESSAGE, "查询成功，返回 " + data.size() + " 条记录");

                // 如果是空结果集，添加列信息
                if (data.isEmpty()) {
                    result.put("columns", getQueryColumns(sql));
                }

            } else if (upperSql.startsWith("INSERT") || upperSql.startsWith("UPDATE") ||
                    upperSql.startsWith("DELETE") || upperSql.startsWith("ALTER") ||
                    upperSql.startsWith("CREATE") || upperSql.startsWith("DROP") ||
                    upperSql.startsWith("TRUNCATE")) {
                // DML/DDL操作
                int rows = jdbcTemplate.update(sql);
                result.put(Constant.SUCCESS, true);
                result.put("rows", rows);
                result.put("type", "update");
                result.put(Constant.MESSAGE, "操作成功，影响行数: " + rows);

            } else {
                result.put(Constant.SUCCESS, false);
                result.put(Constant.MESSAGE, "不支持的SQL语句类型");
            }

        } catch (Exception e) {
            log.error("执行SQL失败: {}", sql, e);
            result.put(Constant.SUCCESS, false);
            result.put(Constant.MESSAGE, "执行失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取图表数据
     */
    public Map<String, Object> getChartData(String sql, String chartType, String title) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql);

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
            }

            result.put(Constant.SUCCESS, true);
            result.put("data", chartResult);

        } catch (Exception e) {
            log.error("获取图表数据失败: {}", sql, e);
            result.put(Constant.SUCCESS, false);
            result.put("message", "获取图表数据失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取数据库统计信息
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 基本统计
            String baseStatsSql = "SELECT " +
                    "(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) as table_count, " +
                    "(SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()) as view_count, " +
                    "(SELECT SUM(TABLE_ROWS) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) as row_count, " +
                    "(SELECT SUM(DATA_LENGTH + INDEX_LENGTH) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) as total_size, " +
                    "(SELECT MAX(CREATE_TIME) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) as last_create_time";

            Map<String, Object> baseStats = jdbcTemplate.queryForMap(baseStatsSql);

            // 表大小排名
            String sizeRankSql = "SELECT TABLE_NAME, TABLE_ROWS, " +
                    "DATA_LENGTH as data_size, INDEX_LENGTH as index_size, " +
                    "DATA_LENGTH + INDEX_LENGTH as total_size " +
                    "FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "ORDER BY total_size DESC " +
                    "LIMIT 10";

            List<Map<String, Object>> sizeRank = jdbcTemplate.queryForList(sizeRankSql);

            result.put(Constant.SUCCESS, true);
            result.put("baseStats", baseStats);
            result.put("sizeRank", sizeRank);

        } catch (Exception e) {
            log.error("获取数据库统计失败", e);
            result.put(Constant.SUCCESS, false);
            result.put("message", "获取统计信息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取示例图表SQL
     */
    public List<Map<String, Object>> getChartExamples() {
        List<Map<String, Object>> examples = new ArrayList<>();

        // 示例1：按日期统计
        examples.add(createExample(
                "按日期统计",
                "SELECT DATE(created_at) as date, COUNT(*) as count FROM your_table GROUP BY DATE(created_at) ORDER BY date",
                "line",
                "适合时间序列数据"
        ));

        // 示例2：分类统计
        examples.add(createExample(
                "分类统计",
                "SELECT category, COUNT(*) as count FROM products GROUP BY category ORDER BY count DESC",
                "pie",
                "适合显示占比"
        ));

        // 示例3：Top N统计
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
     * 检查SQL安全性（简单实现）
     */
    private boolean isSafeSql(String sql) {
        // 禁止的危险操作
        List<String> dangerousKeywords = Arrays.asList(
                "DROP DATABASE", "SHUTDOWN", "GRANT", "REVOKE",
                "CREATE USER", "ALTER USER", "DROP USER"
        );

        for (String keyword : dangerousKeywords) {
            if (sql.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取主键列
     */
    private List<String> getPrimaryKeys(String tableName) {
        String sql = "SELECT COLUMN_NAME " +
                "FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'";

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, tableName);
        List<String> primaryKeys = new ArrayList<>();

        for (Map<String, Object> row : result) {
            primaryKeys.add((String) row.get("COLUMN_NAME"));
        }

        return primaryKeys;
    }

    /**
     * 获取查询的列信息
     */
    private List<String> getQueryColumns(String sql) {
        List<String> columns = new ArrayList<>();
        try {
            // 简化版：从LIMIT之前的部分提取SELECT后的字段
            String selectPart = sql.toUpperCase();
            int selectIndex = selectPart.indexOf("SELECT");
            int fromIndex = selectPart.indexOf("FROM");

            if (selectIndex >= 0 && fromIndex > selectIndex) {
                String columnStr = sql.substring(selectIndex + 6, fromIndex).trim();
                String[] parts = columnStr.split(",");
                for (String part : parts) {
                    // 去除别名
                    String column = part.trim();
                    if (column.contains(" AS ")) {
                        column = column.substring(column.lastIndexOf(" AS ") + 4).trim();
                    } else if (column.contains(" ")) {
                        column = column.substring(column.lastIndexOf(" ") + 1).trim();
                    }
                    columns.add(column);
                }
            }
        } catch (Exception e) {
            log.warn("解析列信息失败", e);
        }
        return columns;
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
                item.put("name", row.get(keyArray[0]));
                item.put("value", row.get(keyArray[1]));
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

        for (int i = 1; i < keyArray.length; i++) {
            Map<String, Object> seriesItem = new HashMap<>();
            seriesItem.put("name", keyArray[i]);
            seriesItem.put("type", "line");
            seriesItem.put("data", new ArrayList<>());
            series.add(seriesItem);
        }

        for (Map<String, Object> row : data) {
            xAxis.add(row.get(keyArray[0]));

            for (int i = 0; i < series.size(); i++) {
                String key = keyArray[i + 1];
                ((List<Object>) series.get(i).get("data")).add(row.get(key));
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
                point.put("value", Arrays.asList(
                        row.get(keyArray[0]),
                        row.get(keyArray[1]),
                        row.get(keyArray[2])
                ));
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
