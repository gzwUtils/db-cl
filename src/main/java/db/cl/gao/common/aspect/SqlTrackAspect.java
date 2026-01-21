package db.cl.gao.common.aspect;

import db.cl.gao.common.Constant;
import db.cl.gao.common.annotation.TrackSql;
import db.cl.gao.common.mapper.SqlHistoryMapper;
import db.cl.gao.common.model.SqlHistory;
import db.cl.gao.common.param.DatabaseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;
@SuppressWarnings("unused")
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SqlTrackAspect {

    private final SqlHistoryMapper sqlHistoryMapper;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * SQL追踪切面
     */
    @Around("@annotation(db.cl.gao.common.annotation.TrackSql)")
    public Object trackSql(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        TrackSql annotation = method.getAnnotation(TrackSql.class);

        // 使用Spring EL表达式获取SQL参数值
        String sql = extractSqlFromExpression(annotation.sqlParam(), joinPoint);

        if (sql == null || sql.trim().isEmpty()) {
            log.warn("未找到SQL参数，跳过记录");
            return joinPoint.proceed();
        }

        // 创建SQL历史记录
        SqlHistory sqlHistory = new SqlHistory();
        sqlHistory.setDatabaseName(DatabaseContextHolder.getDatabase());
        sqlHistory.setSqlText(sql);
        sqlHistory.setExecuteTime(new Date());
        sqlHistory.setSuccess(false); // 默认为失败

        // 设置IP地址
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            sqlHistory.setIpAddress(getClientIp(request));
        }

        long startTime = System.currentTimeMillis();

        try {
            // 执行SQL
            Object result = joinPoint.proceed();

            long costTime = System.currentTimeMillis() - startTime;

            // 设置成功标志和耗时
            sqlHistory.setSuccess(true);
            sqlHistory.setCostTime(costTime);

            // 记录结果数量（如果支持）
            if (annotation.countResult()) {
                extracted(result, sqlHistory);
            }

            // 异步保存SQL历史记录
            saveSqlHistoryAsync(sqlHistory);

            return result;

        } catch (Throwable throwable) {
            long costTime = System.currentTimeMillis() - startTime;

            // 设置错误信息
            sqlHistory.setSuccess(false);
            sqlHistory.setCostTime(costTime);
            sqlHistory.setErrorMessage(throwable.getMessage());

            // 异步保存SQL历史记录
            saveSqlHistoryAsync(sqlHistory);

            if (annotation.ignoreError()) {
                log.error("SQL执行失败（已忽略）: {}", sql, throwable);
                return null;
            } else {
                throw throwable;
            }
        }
    }

    private void extracted(Object result, SqlHistory sqlHistory) {
        try {
            int resultCount = extractResultCount(result);
            sqlHistory.setResultCount(resultCount);
        } catch (Exception e) {
            log.warn("获取结果数量失败", e);
        }
    }

    /**
     * 从Spring EL表达式中提取SQL
     */
    private String extractSqlFromExpression(String expression, ProceedingJoinPoint joinPoint) {
        try {
            Expression expr = parser.parseExpression(expression);
            EvaluationContext context = new StandardEvaluationContext();

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            Object value = expr.getValue(context);
            return value != null ? value.toString() : null;

        } catch (Exception e) {
            log.error("解析SQL表达式失败: {}", expression, e);
            return null;
        }
    }

    /**
     * 从结果中提取结果数量
     */
    private int extractResultCount(Object result) {
        if (result == null) {
            return 0;
        }

        if (result instanceof java.util.List) {
            return ((java.util.List<?>) result).size();
        }

        if (result instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
            if (map.containsKey("data") && map.get("data") instanceof java.util.List) {
                return ((java.util.List<?>) map.get("data")).size();
            }
            if (map.containsKey("count")) {
                Object count = map.get("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
        }

        return 1; // 默认为1
    }

    /**
     * 异步保存SQL历史记录
     */
    private void saveSqlHistoryAsync(SqlHistory history) {
        try {
            // 这里可以使用线程池异步保存，简化为同步保存
            sqlHistoryMapper.insert(history);
        } catch (Exception e) {
            log.error("保存SQL历史记录失败", e);
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || Constant.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || Constant.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || Constant.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
