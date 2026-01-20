package db.cl.gao.controller;
import db.cl.gao.common.ApiOutput;
import db.cl.gao.common.Constant;
import db.cl.gao.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@SuppressWarnings("unused")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class DatabaseController {

    private final DatabaseService databaseService;

    /**
     * 获取所有数据表
     */
    @GetMapping("/tables")
    public ApiOutput<Object> getTables() {
        try {
            return ApiOutput.success(databaseService.getTables());
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return ApiOutput.failure("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取表结构
     */
    @GetMapping("/table/{tableName}/structure")
    public ApiOutput<Object> getTableStructure(@PathVariable String tableName) {
        try {
            return ApiOutput.success(databaseService.getTableStructure(tableName));
        } catch (IllegalArgumentException e) {
            return ApiOutput.failure(Constant.BAD_REQUEST_CODE, e.getMessage());
        } catch (Exception e) {
            log.error("获取表结构失败: {}", tableName, e);
            return ApiOutput.failure("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取表数据
     */
    @GetMapping("/table/{tableName}/data")
    public ApiOutput<Object> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {

        try {
            Map<String, Object> result = databaseService.getTableData(tableName, page, size, sortField, sortOrder);

            if (Boolean.TRUE.equals(result.get(Constant.SUCCESS))) {
                // 移除success字段，因为ApiOutput已经包含
                result.remove(Constant.SUCCESS);
                return ApiOutput.success(result);
            } else {
                return ApiOutput.failure((String) result.get(Constant.MESSAGE));
            }
        } catch (IllegalArgumentException e) {
            return ApiOutput.failure(Constant.BAD_REQUEST_CODE, e.getMessage());
        } catch (Exception e) {
            log.error("获取表数据失败: {}", tableName, e);
            return ApiOutput.failure("获取表数据失败: " + e.getMessage());
        }
    }

    /**
     * 执行SQL查询
     */
    @PostMapping("/query")
    public ApiOutput<Object> executeQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");

        if (sql == null || sql.trim().isEmpty()) {
            return ApiOutput.failure("SQL语句不能为空");
        }

        try {
            Map<String, Object> result = databaseService.executeQuery(sql.trim());

            if (Boolean.TRUE.equals(result.get(Constant.SUCCESS))) {
                // 构建响应数据
                Map<String, Object> responseData = new HashMap<>();

                if ("query".equals(result.get("type"))) {
                    responseData.put("type", "query");
                    responseData.put("data", result.get("data"));
                    responseData.put("count", result.get("count"));
                    responseData.put(Constant.MESSAGE, result.get(Constant.MESSAGE));
                } else {
                    responseData.put("type", "update");
                    responseData.put("rows", result.get("rows"));
                    responseData.put(Constant.MESSAGE, result.get(Constant.MESSAGE));
                }

                return ApiOutput.success(responseData);
            } else {
                return ApiOutput.failure((String) result.get(Constant.MESSAGE));
            }
        } catch (Exception e) {
            log.error("执行SQL失败: {}", sql, e);
            return ApiOutput.failure("执行SQL失败: " + e.getMessage());
        }
    }

    /**
     * 获取图表数据
     */
    @PostMapping("/chart")
    public ApiOutput<Object> getChartData(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        String chartType = request.get("chartType");
        String title = request.get("title");

        if (sql == null || sql.trim().isEmpty()) {
            return ApiOutput.failure("SQL语句不能为空");
        }

        if (!isValidChartType(chartType)) {
            return ApiOutput.failure("不支持的图表类型");
        }

        try {
            Map<String, Object> result = databaseService.getChartData(sql.trim(), chartType, title);

            if (Boolean.TRUE.equals(result.get(Constant.SUCCESS))) {
                return ApiOutput.success(result.get("data"));
            } else {
                return ApiOutput.failure((String) result.get(Constant.MESSAGE));
            }
        } catch (Exception e) {
            log.error("获取图表数据失败", e);
            return ApiOutput.failure("获取图表数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库统计信息
     */
    @GetMapping("/stats")
    public ApiOutput<Object> getDatabaseStats() {
        try {
            Map<String, Object> result = databaseService.getDatabaseStats();

            if (Boolean.TRUE.equals(result.get(Constant.SUCCESS))) {
                // 移除success字段
                result.remove(Constant.SUCCESS);
                return ApiOutput.success(result);
            } else {
                return ApiOutput.failure((String) result.get(Constant.MESSAGE));
            }
        } catch (Exception e) {
            log.error("获取数据库统计失败", e);
            return ApiOutput.failure("获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取图表示例
     */
    @GetMapping("/chart/examples")
    public ApiOutput<Object> getChartExamples() {
        try {
            return ApiOutput.success(databaseService.getChartExamples());
        } catch (Exception e) {
            log.error("获取图表示例失败", e);
            return ApiOutput.failure("获取图表示例失败: " + e.getMessage());
        }
    }

    /**
     * 批量获取多个表的信息
     */
    @PostMapping("/tables/batch")
    public ApiOutput<Object> getTablesBatch(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> tableNames = (java.util.List<String>) request.get("tableNames");

            if (tableNames == null || tableNames.isEmpty()) {
                return ApiOutput.failure("表名列表不能为空");
            }

            Map<String, Object> result = new HashMap<>();
            for (String tableName : tableNames) {
                getTableStructure(tableName, result);
            }

            return ApiOutput.success(result);
        } catch (Exception e) {
            log.error("批量获取表信息失败", e);
            return ApiOutput.failure("批量获取表信息失败: " + e.getMessage());
        }
    }

    private void getTableStructure(String tableName, Map<String, Object> result) {
        try {
            result.put(tableName, databaseService.getTableStructure(tableName));
        } catch (Exception e) {
            result.put(tableName, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库元信息
     */
    @GetMapping("/meta")
    public ApiOutput<Object> getDatabaseMeta() {
        try {
            Map<String, Object> meta = new HashMap<>();

            // 数据库版本
            String version = databaseService.executeQuery("SELECT VERSION() as version")
                    .get("data").toString();
            meta.put("version", version);

            // 字符集
            String charset = databaseService.executeQuery("SHOW VARIABLES LIKE 'character_set_database'")
                    .get("data").toString();
            meta.put("charset", charset);

            return ApiOutput.success(meta);
        } catch (Exception e) {
            log.error("获取数据库元信息失败", e);
            return ApiOutput.failure("获取数据库元信息失败: " + e.getMessage());
        }
    }

    /**
     * 验证图表类型
     */
    private boolean isValidChartType(String chartType) {
        return chartType != null &&
                (chartType.equals("line") ||
                        chartType.equals("bar") ||
                        chartType.equals("pie") ||
                        chartType.equals("scatter"));
    }
}
