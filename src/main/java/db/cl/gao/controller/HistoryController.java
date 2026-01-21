package db.cl.gao.controller;



import db.cl.gao.common.ApiOutput;
import db.cl.gao.common.model.OperationLog;
import db.cl.gao.common.model.SqlHistory;
import db.cl.gao.common.param.PageResult;
import db.cl.gao.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@SuppressWarnings("unused")
@Slf4j
@RestController
@RequestMapping("/api/data/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 获取SQL历史记录
     */
    @GetMapping("/sql")
    public ApiOutput<Object> getSqlHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Boolean success) {

        try {
            PageResult<SqlHistory> result = historyService.getSqlHistory(
                    page, size, database, startTime, endTime, success);
            return ApiOutput.success(result);
        } catch (Exception e) {
            log.error("获取SQL历史记录失败", e);
            return ApiOutput.failure("获取SQL历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取操作日志
     */
    @GetMapping("/operation")
    public ApiOutput<Object> getOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Boolean success) {

        try {
            PageResult<OperationLog> result = historyService.getOperationLogs(
                    page, size, database, operationType, startTime, endTime, success);
            return ApiOutput.success(result);
        } catch (Exception e) {
            log.error("获取操作日志失败", e);
            return ApiOutput.failure("获取操作日志失败: " + e.getMessage());
        }
    }

    /**
     * 清空SQL历史记录
     */
    @DeleteMapping("/sql")
    public ApiOutput<Object> clearSqlHistory(@RequestParam(required = false) String database) {
        try {
            int deletedCount = historyService.clearSqlHistory(database);
            return ApiOutput.success("清空成功，共删除 " + deletedCount + " 条记录");
        } catch (Exception e) {
            log.error("清空SQL历史记录失败", e);
            return ApiOutput.failure("清空SQL历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 清空操作日志
     */
    @DeleteMapping("/operation")
    public ApiOutput<Object> clearOperationLogs(@RequestParam(required = false) String database) {
        try {
            int deletedCount = historyService.clearOperationLogs(database);
            return ApiOutput.success("清空成功，共删除 " + deletedCount + " 条记录");
        } catch (Exception e) {
            log.error("清空操作日志失败", e);
            return ApiOutput.failure("清空操作日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取今日SQL执行统计
     */
    @GetMapping("/sql/today")
    public ApiOutput<Object> getTodaySqlCount(@RequestParam(required = false) String database) {
        try {
            int count = historyService.getTodaySqlCount(database);
            return ApiOutput.success(count);
        } catch (Exception e) {
            log.error("获取今日SQL执行次数失败", e);
            return ApiOutput.failure("获取今日SQL执行次数失败: " + e.getMessage());
        }
    }
}
