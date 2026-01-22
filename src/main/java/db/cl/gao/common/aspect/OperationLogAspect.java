package db.cl.gao.common.aspect;
import db.cl.gao.common.annotation.LogOperation;
import db.cl.gao.common.mapper.OperationLogMapper;
import db.cl.gao.common.model.OperationLog;
import db.cl.gao.common.param.DatabaseContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static db.cl.gao.common.Constant.*;

@SuppressWarnings("unused")
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final String TABLE_NAME_PARAM = "tableName";
    private static final String IP_SEPARATOR = ",";
    private static final int SQL_MAX_LENGTH = 4000;
    private static final int RESULT_MAX_LENGTH = 1000;
    private static final int PARAMS_MAX_LENGTH = 2000;
    private static final int PARAM_VALUE_MAX_LENGTH = 200;
    private static final String TRUNCATED_SUFFIX = "...";
    private static final String SQL_TRUNCATED_SUFFIX = "... [已截断，原长:";
    private static final String SELECT_PREFIX = "SELECT";
    private static final String INSERT_PREFIX = "INSERT";
    private static final String UPDATE_PREFIX = "UPDATE";
    private static final String DELETE_PREFIX = "DELETE";
    private static final String CREATE_PREFIX = "CREATE";
    private static final String ALTER_PREFIX = "ALTER";
    private static final String DROP_PREFIX = "DROP";
    private static final String TRUNCATE_PREFIX = "TRUNCATE";
    private static final String RENAME_PREFIX = "RENAME";
    private static final String SHOW_PREFIX = "SHOW";
    private static final String DESC_PREFIX = "DESC";
    private static final String EXPLAIN_PREFIX = "EXPLAIN";
    private static final String GRANT_PREFIX = "GRANT";
    private static final String REVOKE_PREFIX = "REVOKE";
    private static final String BEGIN_PREFIX = "BEGIN";
    private static final String COMMIT_PREFIX = "COMMIT";
    private static final String ROLLBACK_PREFIX = "ROLLBACK";
    private static final String FROM_KEYWORD = "FROM ";
    private static final String INTO_KEYWORD = "INTO ";
    private static final String TABLE_KEYWORD = "TABLE ";



    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 定义切点
     */
    @Pointcut("@annotation(db.cl.gao.common.annotation.LogOperation)")
    public void operationLogPointcut() {
        // 切点定义，方法体为空
    }

    /**
     * 环绕通知
     */
    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogOperation annotation = method.getAnnotation(LogOperation.class);

        OperationLog operationLog = createOperationLog(annotation);
        setupRequestInfo(operationLog);

        try {
            extractTableNameAndSql(operationLog, joinPoint, method.getName());
            if (annotation.dynamicType()) {
                adjustOperationTypeBySql(operationLog, joinPoint);
            }
        } catch (Exception e) {
            log.warn("提取表名和SQL内容失败", e);
        }

        saveParams(joinPoint, annotation, operationLog);

        try {
            Object result = joinPoint.proceed();
            handleSuccess(operationLog, result, annotation);
            saveOperationLog(operationLog);
            return result;
        } catch (Throwable throwable) {
            handleFailure(operationLog, throwable, annotation);
            throw throwable;
        }
    }

    private OperationLog createOperationLog(LogOperation annotation) {
        OperationLog operationLog = new OperationLog();
        operationLog.setOperationType(annotation.type().name());
        operationLog.setDatabaseName(DatabaseContextHolder.getDatabase());
        operationLog.setSuccess(false);
        operationLog.setCreatedBy("system");
        operationLog.setExecuteTime(new Date());
        return operationLog;
    }

    private void setupRequestInfo(OperationLog operationLog) {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operationLog.setIpAddress(getClientIp(request));
        }
    }

    private void handleSuccess(OperationLog operationLog, Object result, LogOperation annotation) {
        operationLog.setSuccess(true);

        if (annotation.logResult()) {
            logResult(result, operationLog);
        }

        extractTableInfoAndAffectedRows(operationLog, result);
    }

    private void handleFailure(OperationLog operationLog, Throwable throwable, LogOperation annotation) {
        if (isIgnoredException(throwable, annotation.ignoreExceptions())) {
            operationLog.setSuccess(true);
            operationLog.setMessage("预期异常: " + throwable.getMessage());
        } else {
            operationLog.setSuccess(false);
            operationLog.setMessage("异常: " + throwable.getMessage());
        }
        saveOperationLog(operationLog);
    }

    private boolean isIgnoredException(Throwable throwable, Class<? extends Throwable>[] ignoreExceptions) {
        return Arrays.stream(ignoreExceptions)
                .anyMatch(ignoreException -> ignoreException.isInstance(throwable));
    }

    private void logResult(Object result, OperationLog operationLog) {
        try {
            // 检查是否为文件下载响应
            if (isFileDownloadResponse(result)) {
                // 对于文件下载，记录简化信息
                Map<String, Object> simplifiedResult = new HashMap<>();
                simplifiedResult.put("type", "FILE_DOWNLOAD");
                simplifiedResult.put("status", "SUCCESS");

                getFileInfo(result, simplifiedResult);

                // 记录简化信息
                String resultJson = objectMapper.writeValueAsString(simplifiedResult);
                appendMessage(operationLog, "结果", resultJson, RESULT_MAX_LENGTH);
                return;
            }

            // 普通结果的序列化逻辑
            String resultJson = objectMapper.writeValueAsString(result);
            appendMessage(operationLog, "结果", resultJson, RESULT_MAX_LENGTH);
        } catch (Exception e) {
            log.warn("记录结果失败", e);
            // 尝试记录一个简化的错误信息
            try {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "无法序列化响应结果");
                errorResult.put("type", result != null ? result.getClass().getSimpleName() : "NULL");
                String errorJson = objectMapper.writeValueAsString(errorResult);
                appendMessage(operationLog, "结果", errorJson, RESULT_MAX_LENGTH);
            } catch (Exception ex) {
                log.error("记录操作日志失败", ex);
            }
        }
    }

    private void getFileInfo(Object result, Map<String, Object> simplifiedResult) {
        // 尝试获取文件信息
        try {
            if (result instanceof ResponseEntity) {
                ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
                HttpHeaders headers = responseEntity.getHeaders();

                // 获取内容类型
                MediaType contentType = headers.getContentType();
                if (contentType != null) {
                    simplifiedResult.put("contentType", contentType.toString());
                }

                // 获取文件名
                String contentDisposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (contentDisposition != null && contentDisposition.contains("filename=")) {
                    String fileName = contentDisposition.substring(
                            contentDisposition.indexOf("filename=") + 9
                    ).replace("\"", "");
                    simplifiedResult.put("fileName", fileName);
                }

                // 获取文件大小
                Object body = responseEntity.getBody();
                if (body instanceof ByteArrayResource) {
                    long contentLength = ((ByteArrayResource) body).contentLength();
                    simplifiedResult.put("fileSize", contentLength);
                    simplifiedResult.put("fileSizeReadable", formatFileSize(contentLength));
                }
            }
        } catch (Exception e) {
            log.debug("获取文件信息失败", e);
        }
    }

    /**
     * 判断是否为文件下载响应
     */
    private boolean isFileDownloadResponse(Object result) {
        if (!(result instanceof ResponseEntity)) {
            return false;
        }

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        Object body = response.getBody();

        // 检查是否是文件资源
        return body instanceof Resource || body != null && (body.getClass().getSimpleName().contains("Resource") || body.getClass().getSimpleName().contains("InputStream"));
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    private void saveParams(ProceedingJoinPoint joinPoint, LogOperation annotation, OperationLog operationLog) {
        if (annotation.logParams()) {
            try {
                Map<String, Object> params = buildParams(joinPoint);
                String paramsJson = objectMapper.writeValueAsString(params);
                appendMessage(operationLog, "参数", paramsJson, PARAMS_MAX_LENGTH);
            } catch (Exception e) {
                log.warn("记录参数失败", e);
            }
        }
    }

    private void appendMessage(OperationLog operationLog, String prefix, String content, int maxLength) {
        String truncatedContent = truncateMessage(content, maxLength);
        String newMessage = prefix + ": " + truncatedContent;

        if (operationLog.getMessage() == null) {
            operationLog.setMessage(newMessage);
        } else {
            operationLog.setMessage(operationLog.getMessage() + " | " + newMessage);
        }
    }

    /**
     * 根据SQL动态调整操作类型
     */
    private void adjustOperationTypeBySql(OperationLog operationLog, ProceedingJoinPoint joinPoint) {
        try {
            String sql = extractSqlFromParams(joinPoint);
            if (sql != null) {
                String operationType = getOperationTypeFromSql(sql);
                if (operationType != null) {
                    operationLog.setOperationType(operationType);
                    log.debug("动态调整操作类型为: {}", operationType);
                }
            }
        } catch (Exception e) {
            log.warn("动态调整操作类型失败", e);
        }
    }

    /**
     * 从SQL语句判断操作类型
     */
    private String getOperationTypeFromSql(String sql) {
        if (sql == null) {
            return null;
        }

        String upperSql = sql.trim().toUpperCase();

        if (upperSql.startsWith(SELECT_PREFIX) ||
                upperSql.startsWith(SHOW_PREFIX) ||
                upperSql.startsWith(DESC_PREFIX) ||
                upperSql.startsWith(EXPLAIN_PREFIX)) {
            return LogOperation.OperationType.QUERY.name();
        } else if (upperSql.startsWith(INSERT_PREFIX)) {
            return LogOperation.OperationType.INSERT.name();
        } else if (upperSql.startsWith(UPDATE_PREFIX)) {
            return LogOperation.OperationType.UPDATE.name();
        } else if (upperSql.startsWith(DELETE_PREFIX)) {
            return LogOperation.OperationType.DELETE.name();
        } else if (upperSql.startsWith(CREATE_PREFIX)) {
            return LogOperation.OperationType.CREATE.name();
        } else if (upperSql.startsWith(ALTER_PREFIX)) {
            return LogOperation.OperationType.ALTER.name();
        } else if (upperSql.startsWith(DROP_PREFIX)) {
            return LogOperation.OperationType.DROP.name();
        } else if (upperSql.startsWith(TRUNCATE_PREFIX)) {
            return LogOperation.OperationType.TRUNCATE.name();
        } else if (upperSql.startsWith(RENAME_PREFIX)) {
            return LogOperation.OperationType.RENAME.name();
        } else if (upperSql.startsWith(GRANT_PREFIX) || upperSql.startsWith(REVOKE_PREFIX)) {
            return LogOperation.OperationType.PERMISSION.name();
        } else if (upperSql.startsWith(BEGIN_PREFIX) ||
                upperSql.startsWith(COMMIT_PREFIX) ||
                upperSql.startsWith(ROLLBACK_PREFIX)) {
            return LogOperation.OperationType.TRANSACTION.name();
        }

        return LogOperation.OperationType.OTHER.name();
    }

    /**
     * 提取表名和SQL内容
     */
    private void extractTableNameAndSql(OperationLog operationLog, ProceedingJoinPoint joinPoint, String methodName) {
        try {
            String tableName = extractTableNameFromMethod(joinPoint, methodName);
            if (tableName != null) {
                operationLog.setTableName(tableName);
            }

            String sql = extractSqlFromParams(joinPoint);
            if (sql != null) {
                operationLog.setSqlText(truncateSql(sql));

                if (tableName == null) {
                    String tableFromSql = extractTableNameFromSql(sql);
                    if (tableFromSql != null) {
                        operationLog.setTableName(tableFromSql);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取表名和SQL失败", e);
        }
    }

    /**
     * 从方法参数中提取表名
     */
    private String extractTableNameFromMethod(ProceedingJoinPoint joinPoint, String methodName) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            // 查找名为tableName的参数
            for (int i = 0; i < paramNames.length; i++) {
                if (TABLE_NAME_PARAM.equals(paramNames[i]) && args[i] instanceof String) {
                    return (String) args[i];
                }
            }

            // 尝试使用SpEL表达式获取表名
            String tableNameFromSpel = getTableNameFromSpel(signature, joinPoint);
            if (tableNameFromSpel != null) {
                return tableNameFromSpel;
            }

            // 根据方法名推断表名
            return inferTableNameFromMethodName(args, methodName);
        } catch (Exception e) {
            log.warn("从方法参数提取表名失败", e);
            return null;
        }
    }

    private String getTableNameFromSpel(MethodSignature signature, ProceedingJoinPoint joinPoint) {
        try {
            Method method = signature.getMethod();
            LogOperation annotation = method.getAnnotation(LogOperation.class);
            if (annotation != null && !annotation.tableName().isEmpty()) {
                String tableNameSpel = annotation.tableName();
                StandardEvaluationContext context = new StandardEvaluationContext();
                String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
                Object[] parameterValues = joinPoint.getArgs();

                if (parameterNames != null) {
                    for (int i = 0; i < parameterNames.length; i++) {
                        context.setVariable(parameterNames[i], parameterValues[i]);
                    }
                }

                Expression expression = parser.parseExpression(tableNameSpel);
                Object result = expression.getValue(context);
                if (result instanceof String) {
                    return (String) result;
                }
            }
        } catch (Exception e) {
            log.debug("使用SpEL表达式获取表名失败", e);
        }
        return null;
    }

    private String inferTableNameFromMethodName(Object[] args, String methodName) {
        if (methodName.contains("Table") || methodName.contains("Data")) {
            for (Object arg : args) {
                if (arg instanceof String) {
                    String strArg = (String) arg;
                    if (TABLE_NAME_PATTERN.matcher(strArg).matches() &&
                            !strArg.contains(" ") &&
                            !strArg.toUpperCase().contains(SELECT_PREFIX)) {
                        return strArg;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从参数中提取SQL
     */
    private String extractSqlFromParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();

            for (Object arg : args) {
                if (arg instanceof String) {
                    String strArg = (String) arg;
                    String upperArg = strArg.trim().toUpperCase();
                    if (isSqlStatement(upperArg)) {
                        return strArg;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("从参数中提取SQL失败", e);
            return null;
        }
    }

    private boolean isSqlStatement(String upperSql) {
        return upperSql.startsWith(SELECT_PREFIX) ||
                upperSql.startsWith(INSERT_PREFIX) ||
                upperSql.startsWith(UPDATE_PREFIX) ||
                upperSql.startsWith(DELETE_PREFIX) ||
                upperSql.startsWith(CREATE_PREFIX) ||
                upperSql.startsWith(ALTER_PREFIX) ||
                upperSql.startsWith(DROP_PREFIX) ||
                upperSql.startsWith(TRUNCATE_PREFIX) ||
                upperSql.startsWith(RENAME_PREFIX) ||
                upperSql.startsWith(SHOW_PREFIX) ||
                upperSql.startsWith(DESC_PREFIX) ||
                upperSql.startsWith(EXPLAIN_PREFIX) ||
                upperSql.startsWith(GRANT_PREFIX) ||
                upperSql.startsWith(REVOKE_PREFIX) ||
                upperSql.startsWith(BEGIN_PREFIX) ||
                upperSql.startsWith(COMMIT_PREFIX) ||
                upperSql.startsWith(ROLLBACK_PREFIX);
    }

    /**
     * 提取表信息和影响行数
     */
    private void extractTableInfoAndAffectedRows(OperationLog operationLog, Object result) {
        try {
            extractAffectedRows(operationLog, result);
            extractTableNameFromSqlIfMissing(operationLog);
        } catch (Exception e) {
            log.warn("提取表信息和影响行数失败", e);
        }
    }

    private void extractAffectedRows(OperationLog operationLog, Object result) {
        if (result instanceof Map) {
            extractAffectedRowsFromMap(operationLog, (Map<?, ?>) result);
        } else if (result instanceof Integer) {
            operationLog.setAffectedRows((Integer) result);
        } else if (result instanceof Long) {
            operationLog.setAffectedRows(((Long) result).intValue());
        }
    }

    private void extractAffectedRowsFromMap(OperationLog operationLog, Map<?, ?> resultMap) {
        String[] rowKeys = {"rows", "rowCount", "affectedRows", "count", "total", "updateCount"};

        for (String key : rowKeys) {
            if (resultMap.containsKey(key)) {
                Object rows = resultMap.get(key);
                if (rows instanceof Number) {
                    operationLog.setAffectedRows(((Number) rows).intValue());
                    break;
                }
            }
        }
    }

    private void extractTableNameFromSqlIfMissing(OperationLog operationLog) {
        if (operationLog.getTableName() == null && operationLog.getSqlText() != null) {
            String tableName = extractTableNameFromSql(operationLog.getSqlText());
            if (tableName != null) {
                operationLog.setTableName(tableName);
            }
        }
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableNameFromSql(String sql) {
        try {
            String cleanSql = SQL_COMMENT_PATTERN.matcher(sql).replaceAll("").trim();
            String upperSql = cleanSql.toUpperCase();

            if (upperSql.startsWith(SELECT_PREFIX)) {
                return extractTableFromSelect(cleanSql);
            } else if (upperSql.startsWith(INSERT_PREFIX)) {
                return extractTableFromInsert(cleanSql);
            } else if (upperSql.startsWith(UPDATE_PREFIX)) {
                return extractTableFromUpdate(cleanSql);
            } else if (upperSql.startsWith(DELETE_PREFIX)) {
                return extractTableFromDelete(cleanSql);
            } else if (upperSql.startsWith(CREATE_PREFIX)) {
                return extractTableFromCreate(cleanSql);
            } else if (upperSql.startsWith(ALTER_PREFIX)) {
                return extractTableFromAlter(cleanSql);
            } else if (upperSql.startsWith(DROP_PREFIX)) {
                return extractTableFromDrop(cleanSql);
            } else if (upperSql.startsWith(TRUNCATE_PREFIX)) {
                return extractTableFromTruncate(cleanSql);
            } else if (upperSql.startsWith(SHOW_PREFIX)) {
                return extractTableFromShow(cleanSql);
            }

            return null;
        } catch (Exception e) {
            log.warn("从SQL提取表名失败: {}", sql, e);
            return null;
        }
    }

    private String extractTableFromSelect(String sql) {
        return extractTableAfterKeyword(sql, FROM_KEYWORD, 5);
    }

    private String extractTableFromInsert(String sql) {
        return extractTableAfterKeyword(sql, INTO_KEYWORD, 5);
    }

    private String extractTableFromUpdate(String sql) {
        return extractFirstWord(sql.substring(UPDATE_PREFIX.length() + 1));
    }

    private String extractTableFromDelete(String sql) {
        return extractTableAfterKeyword(sql, FROM_KEYWORD, 5);
    }

    private String extractTableFromCreate(String sql) {
        return extractTableAfterKeyword(sql, TABLE_KEYWORD, 6);
    }

    private String extractTableFromAlter(String sql) {
        return extractTableAfterKeyword(sql, TABLE_KEYWORD, 6);
    }

    private String extractTableFromDrop(String sql) {
        return extractTableAfterKeyword(sql, TABLE_KEYWORD, 6);
    }

    private String extractTableFromTruncate(String sql) {
        return extractTableAfterKeyword(sql, TABLE_KEYWORD, 6);
    }

    private String extractTableFromShow(String sql) {
        String[] parts = sql.substring(5).split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (TABLE_KEYWORD.trim().equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                return cleanTableName(parts[i + 1]);
            }
        }
        return null;
    }

    private String extractTableAfterKeyword(String sql, String keyword, int keywordLength) {
        int keywordIndex = sql.toUpperCase().indexOf(keyword);
        if (keywordIndex > 0) {
            String afterKeyword = sql.substring(keywordIndex + keywordLength);
            if (afterKeyword.trim().startsWith("(")) {
                return null;
            }
            String[] parts = afterKeyword.split("[\\s,;)]");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return cleanTableName(parts[0]);
            }
        }
        return null;
    }

    private String extractFirstWord(String str) {
        String[] parts = str.split("\\s+");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            return cleanTableName(parts[0]);
        }
        return null;
    }

    private String cleanTableName(String tableName) {
        return TABLE_CLEAN_PATTERN.matcher(tableName).replaceAll("")
                .replaceAll("^[a-zA-Z_]+\\.", "");
    }

    /**
     * 保存操作日志
     */
    private void saveOperationLog(OperationLog logs) {
        try {
            if (logs.getCreatedAt() == null) {
                logs.setCreatedAt(new Date());
            }

            operationLogMapper.insert(logs);

            log.debug("操作日志保存成功: 类型={}, 表名={}, 影响行数={}",
                    logs.getOperationType(), logs.getTableName(), logs.getAffectedRows());
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
            // 这里不抛出异常，避免影响主流程
        }
    }

    /**
     * 构建参数Map
     */
    private Map<String, Object> buildParams(ProceedingJoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                processParam(params, paramNames[i], args[i]);
            }
        }
        return params;
    }

    private void processParam(Map<String, Object> params, String paramName, Object arg) {
        if (isSensitiveParam(paramName)) {
            params.put(paramName, "***");
        } else {
            params.put(paramName, processParamValue(arg));
        }
    }

    private Object processParamValue(Object arg) {
        if (arg instanceof String) {
            String strValue = (String) arg;
            if (strValue.length() > PARAM_VALUE_MAX_LENGTH) {
                return truncateMessage(strValue, PARAM_VALUE_MAX_LENGTH);
            }
        }
        return arg;
    }

    /**
     * 检查是否为敏感参数
     */
    private boolean isSensitiveParam(String paramName) {
        String lowerParamName = paramName.toLowerCase();
        return lowerParamName.contains("password") ||
                lowerParamName.contains("secret") ||
                lowerParamName.contains("token") ||
                lowerParamName.contains("key") ||
                lowerParamName.contains("credential");
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        String ip = null;
        for (String headerName : headerNames) {
            ip = request.getHeader(headerName);
            if (isValidIp(ip)) {
                break;
            }
        }

        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多个IP时取第一个
        if (ip != null && ip.contains(IP_SEPARATOR)) {
            ip = ip.split(IP_SEPARATOR)[0].trim();
        }

        return ip;
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * 截断SQL文本
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return null;
        }
        if (sql.length() <= OperationLogAspect.SQL_MAX_LENGTH) {
            return sql;
        }
        return sql.substring(0, OperationLogAspect.SQL_MAX_LENGTH) + SQL_TRUNCATED_SUFFIX + sql.length() + "]";
    }

    /**
     * 截断消息文本
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }
}