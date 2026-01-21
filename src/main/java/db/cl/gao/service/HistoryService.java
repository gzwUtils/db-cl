package db.cl.gao.service;

import com.github.pagehelper.PageInfo;
import com.github.pagehelper.page.PageMethod;
import db.cl.gao.common.excep.DbException;
import db.cl.gao.common.mapper.OperationLogMapper;
import db.cl.gao.common.mapper.SqlHistoryMapper;
import db.cl.gao.common.model.OperationLog;
import db.cl.gao.common.model.SqlHistory;
import db.cl.gao.common.param.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@SuppressWarnings("unused")
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final SqlHistoryMapper sqlHistoryMapper;
    private final OperationLogMapper operationLogMapper;

    /**
     * 保存SQL历史记录
     */
    @Transactional
    public void saveSqlHistory(SqlHistory history) {
        try {
            sqlHistoryMapper.insert(history);
        } catch (Exception e) {
            log.error("保存SQL历史记录失败", e);
            throw new DbException("保存SQL历史记录失败", e);
        }
    }

    /**
     * 保存操作日志
     */
    @Transactional
    public void saveOperationLog(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
            throw new DbException("保存操作日志失败", e);
        }
    }

    /**
     * 获取SQL历史记录（分页）
     */
    public PageResult<SqlHistory> getSqlHistory(Integer page, Integer size,
                                                String databaseName,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime,
                                                Boolean success) {

        try {
            // 使用PageHelper分页
            PageMethod.startPage(page, size);

            List<SqlHistory> data = sqlHistoryMapper.selectList(
                    databaseName, startTime, endTime, success);

            PageInfo<SqlHistory> pageInfo = new PageInfo<>(data);

            PageResult<SqlHistory> result = new PageResult<>();
            result.setData(data);
            result.setTotal(pageInfo.getTotal());
            result.setPage(page);
            result.setSize(size);
            result.setTotalPages(pageInfo.getPages());

            return result;
        } catch (Exception e) {
            log.error("查询SQL历史记录失败", e);
            throw new DbException("查询SQL历史记录失败", e);
        }
    }

    /**
     * 获取操作日志（分页）
     */
    public PageResult<OperationLog> getOperationLogs(Integer page, Integer size,
                                                     String databaseName,
                                                     String operationType,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime,
                                                     Boolean success) {

        try {
            // 使用PageHelper分页
            PageMethod.startPage(page, size);

            List<OperationLog> data = operationLogMapper.selectList(
                    databaseName, operationType, startTime, endTime, success);

            PageInfo<OperationLog> pageInfo = new PageInfo<>(data);

            PageResult<OperationLog> result = new PageResult<>();
            result.setData(data);
            result.setTotal(pageInfo.getTotal());
            result.setPage(page);
            result.setSize(size);
            result.setTotalPages(pageInfo.getPages());

            return result;
        } catch (Exception e) {
            log.error("查询操作日志失败", e);
            throw new DbException("查询操作日志失败", e);
        }
    }

    /**
     * 清空SQL历史记录
     */
    @Transactional
    public int clearSqlHistory(String databaseName) {
        try {
            return sqlHistoryMapper.delete(databaseName);
        } catch (Exception e) {
            log.error("清空SQL历史记录失败", e);
            throw new DbException("清空SQL历史记录失败", e);
        }
    }

    /**
     * 清空操作日志
     */
    @Transactional
    public int clearOperationLogs(String databaseName) {
        try {
            return operationLogMapper.delete(databaseName);
        } catch (Exception e) {
            log.error("清空操作日志失败", e);
            throw new DbException("清空操作日志失败", e);
        }
    }

    /**
     * 获取今日SQL执行次数
     */
    public int getTodaySqlCount(String databaseName) {
        try {
            return sqlHistoryMapper.countToday(databaseName);
        } catch (Exception e) {
            log.error("获取今日SQL执行次数失败", e);
            return 0;
        }
    }
}
