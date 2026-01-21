package db.cl.gao.controller;

import db.cl.gao.common.ApiOutput;
import db.cl.gao.common.Constant;
import db.cl.gao.common.param.DatabaseConfig;
import db.cl.gao.common.param.DatabaseContextHolder;
import db.cl.gao.config.DatabaseConfigManager;
import db.cl.gao.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
@SuppressWarnings("unused")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseService databaseService;
    private final DatabaseConfigManager configManager;

    /**
     * 获取所有数据库列表
     */
    @GetMapping("/databases")
    public ApiOutput<Object> getDatabases() {
        try {
            return ApiOutput.success(databaseService.getDatabases());
        } catch (Exception e) {
            log.error("获取数据库列表失败", e);
            return ApiOutput.failure("获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 切换到指定数据库
     */
    @PostMapping("/database/switch")
    public ApiOutput<Object> switchDatabase(@RequestBody Map<String, String> request) {
        String database = request.get("database");

        if (database == null || database.trim().isEmpty()) {
            return ApiOutput.failure("数据库名不能为空");
        }

        try {
            boolean success = databaseService.switchDatabase(database);
            if (success) {
                // 设置数据库上下文
                DatabaseContextHolder.setDatabase(database);
                return ApiOutput.success("切换到数据库: " + database);
            } else {
                return ApiOutput.failure("数据库切换失败: " + database);
            }
        } catch (Exception e) {
            log.error("切换数据库失败: {}", database, e);
            return ApiOutput.failure("切换数据库失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前数据库
     */
    @GetMapping("/database/current")
    public ApiOutput<Object> getCurrentDatabase() {
        try {
            String currentDb = DatabaseContextHolder.getDatabase();
            if (currentDb == null) {
                currentDb = "default";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("database", currentDb);
            result.put("isDefault", currentDb.equals("default"));

            return ApiOutput.success(result);
        } catch (Exception e) {
            log.error("获取当前数据库失败", e);
            return ApiOutput.failure("获取当前数据库失败: " + e.getMessage());
        }
    }

    /**
     * 添加数据库配置
     */
    @PostMapping("/database/config")
    public ApiOutput<Object> addDatabaseConfig(@RequestBody DatabaseConfig config) {
        try {
            if (config.getName() == null || config.getUrl() == null) {
                return ApiOutput.failure("配置信息不完整");
            }

            // 设置默认值
            if (config.getDriverClassName() == null || config.getDriverClassName().isEmpty()) {
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
            if (config.getInitialSize() <= 0) {
                config.setInitialSize(5);
            }
            if (config.getMinIdle() <= 0) {
                config.setMinIdle(5);
            }
            if (config.getMaxActive() <= 0) {
                config.setMaxActive(20);
            }

            configManager.addOrUpdateConfig(config.getName(), config);
            return ApiOutput.success("添加数据库配置成功");
        } catch (Exception e) {
            log.error("添加数据库配置失败", e);
            return ApiOutput.failure("添加数据库配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有数据库配置
     */
    @GetMapping("/database/configs")
    public ApiOutput<Object> getDatabaseConfigs() {
        try {
            return ApiOutput.success(configManager.getAllConfigs());
        } catch (Exception e) {
            log.error("获取数据库配置失败", e);
            return ApiOutput.failure("获取数据库配置失败: " + e.getMessage());
        }
    }

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
                // 移除success字段
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

            if (Boolean.TRUE.equals(result.get("success"))) {
                return ApiOutput.success(result);
            } else {
                return ApiOutput.failure((String) result.get("message"));
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