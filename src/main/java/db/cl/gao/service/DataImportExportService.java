package db.cl.gao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import db.cl.gao.common.Constant;
import db.cl.gao.common.enums.ExportFormat;
import db.cl.gao.common.excep.DbException;
import db.cl.gao.common.param.DatabaseContextHolder;
import db.cl.gao.common.param.ExportRequest;
import db.cl.gao.common.param.ImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据导入导出服务实现
 *
 * @author gao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportExportService {

    private static final int BATCH_SIZE = 1000;
    private static final String ERROR_COLUMN = "__error";
    private static final String UTF_8_BOM = "\uFEFF";

    private final DatabaseService databaseService;

    /**
     * 数据导出
     *
     * @param database 数据库名称
     * @param request  导出请求参数
     * @return 导出结果
     */
    @SuppressWarnings("unchecked")
    public Resource exportData(String database, ExportRequest request) throws IOException {
        long startTime = System.currentTimeMillis();

        try {
            if (StringUtils.hasText(database)) {
                DatabaseContextHolder.setDatabase(database);
            }

            String tableName = request.getTableName();
            ExportFormat format = request.getFormat();
            String whereClause = request.getWhereClause();

            // 验证表是否存在
            validateTableExists(tableName);

            String sql = buildExportSql(tableName, whereClause);
            log.info("导出SQL: {}", sql);

            // 使用 DatabaseService 执行查询
            Map<String, Object> queryResult = databaseService.executeQuery(sql);

            if (Boolean.FALSE.equals(queryResult.getOrDefault(Constant.SUCCESS, false))) {
                throw new DbException("查询失败: " + queryResult.get(Constant.MESSAGE));
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");

            switch (format) {
                case EXCEL:
                    return exportToExcel(data, tableName);
                case CSV:
                    return exportToCsv(data);
                case JSON:
                    return exportToJson(data);
                case SQL:
                    return exportToSql(data, tableName);
                default:
                    throw new IllegalArgumentException("不支持的导出格式: " + format);
            }

        } finally {
            log.info("导出完成，耗时: {}ms", System.currentTimeMillis() - startTime);
            DatabaseContextHolder.clear();
        }
    }

    public Resource generateTemplate(String database, String tableName) throws IOException {
        try {
            if (StringUtils.hasText(database)) {
                DatabaseContextHolder.setDatabase(database);
            }

            // 使用 DatabaseService 获取表结构
            List<Map<String, Object>> columns = databaseService.getTableStructure(tableName);
            List<String> columnNames = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                columnNames.add((String) column.get("COLUMN_NAME"));
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            out(outputStream, columnNames);

            return new ByteArrayResource(outputStream.toByteArray());

        } finally {
            DatabaseContextHolder.clear();
        }
    }

    private static void out(ByteArrayOutputStream outputStream, List<String> columnNames) throws IOException {
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // 写入表头
            csvPrinter.printRecord(columnNames);

            // 写入示例数据
            csvPrinter.printComment("请按照此格式填写数据，注意：");
            csvPrinter.printComment("1. 不要删除或修改表头");
            csvPrinter.printComment("2. 日期格式请使用 yyyy-MM-dd HH:mm:ss");
            csvPrinter.printComment("3. 数字请直接填写数字，不要加引号");

            csvPrinter.flush();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCsv(String database, String tableName,
                                  boolean truncateFirst, MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        ImportResult result = new ImportResult();
        List<Map<String, Object>> errorDetails = new ArrayList<>();

        try {
            if (StringUtils.hasText(database)) {
                DatabaseContextHolder.setDatabase(database);
            }

            // 验证表是否存在
            validateTableExists(tableName);

            // 使用 DatabaseService 获取表结构
            List<Map<String, Object>> columnInfos = databaseService.getTableStructure(tableName);
            List<String> tableColumns = new ArrayList<>();
            for (Map<String, Object> column : columnInfos) {
                tableColumns.add((String) column.get("COLUMN_NAME"));
            }

            // 清空表数据
            if (truncateFirst) {
                truncateTable(tableName);
            }

            // 解析CSV文件
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            // 去除UTF-8 BOM
            if (content.startsWith(UTF_8_BOM)) {
                content = content.substring(1);
            }

            try (StringReader stringReader = new StringReader(content);
                 CSVParser csvParser = new CSVParser(stringReader,
                         CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase())) {

                List<Map<String, String>> records = new ArrayList<>();
                AtomicInteger successCount = new AtomicInteger(0);

                for (CSVRecord csvRecord : csvParser) {
                    Map<String, String> recordMap = csvRecord.toMap();
                    records.add(recordMap);

                    // 批量处理
                    if (records.size() >= BATCH_SIZE) {
                        processBatch(tableName, tableColumns, records,
                                successCount, errorDetails);
                        records.clear();
                    }
                }

                // 处理剩余记录
                if (!records.isEmpty()) {
                    processBatch(tableName, tableColumns, records,
                            successCount, errorDetails);
                }

                result.setImportedRows(successCount.get());
                result.setErrorRows(errorDetails.size());
            }

        } finally {
            result.setCostTime(System.currentTimeMillis() - startTime);
            if (!errorDetails.isEmpty()) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("errors", errorDetails);
                result.setErrorDetails(errorMap);
            }
            DatabaseContextHolder.clear();
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(String database, String tableName,
                                    boolean truncateFirst, MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        ImportResult result = new ImportResult();
        List<Map<String, Object>> errorDetails = new ArrayList<>();

        try {
            if (StringUtils.hasText(database)) {
                DatabaseContextHolder.setDatabase(database);
            }

            // 验证表是否存在
            validateTableExists(tableName);

            // 使用 DatabaseService 获取表结构
            List<Map<String, Object>> columnInfos = databaseService.getTableStructure(tableName);
            List<String> tableColumns = new ArrayList<>();
            for (Map<String, Object> column : columnInfos) {
                tableColumns.add((String) column.get("COLUMN_NAME"));
            }

            // 清空表数据
            if (truncateFirst) {
                truncateTable(tableName);
            }

            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.iterator();

                // 读取表头
                if (!rowIterator.hasNext()) {
                    throw new DbException("Excel文件为空");
                }

                Row headerRow = rowIterator.next();
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(getCellValue(cell));
                }

                List<Map<String, String>> records = new ArrayList<>();
                AtomicInteger successCount = new AtomicInteger(0);

                // 读取数据行
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    Map<String, String> stringMap = new HashMap<>();

                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        stringMap.put(headers.get(i), getCellValue(cell));
                    }

                    records.add(stringMap);

                    // 批量处理
                    if (records.size() >= BATCH_SIZE) {
                        processBatch(tableName, tableColumns, records,
                                successCount, errorDetails);
                        records.clear();
                    }
                }

                // 处理剩余记录
                if (!records.isEmpty()) {
                    processBatch(tableName, tableColumns, records,
                            successCount, errorDetails);
                }

                result.setImportedRows(successCount.get());
                result.setErrorRows(errorDetails.size());
            }

        } finally {
            result.setCostTime(System.currentTimeMillis() - startTime);
            if (!errorDetails.isEmpty()) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("errors", errorDetails);
                result.setErrorDetails(errorMap);
            }
            DatabaseContextHolder.clear();
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSql(String database, MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        ImportResult result = new ImportResult();

        try {
            if (StringUtils.hasText(database)) {
                DatabaseContextHolder.setDatabase(database);
            }

            String sqlContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            // 分割SQL语句
            String[] sqlStatements = sqlContent.split(";\\s*\n");

            int successCount = 0;
            int errorCount = 0;
            List<Map<String, Object>> errorDetails = new ArrayList<>();

            for (String sql : sqlStatements) {
                sql = sql.trim();
                if (sql.isEmpty()) {
                    continue;
                }

                try {
                    // 使用 DatabaseService 执行SQL
                    Map<String, Object> executeResult = databaseService.executeQuery(sql);
                    if (Boolean.TRUE.equals(executeResult.getOrDefault(Constant.SUCCESS, false))) {
                        successCount++;
                    } else {
                        errorCount++;
                        Map<String, Object> errorDetail = new HashMap<>();
                        errorDetail.put("sql", sql);
                        errorDetail.put(ERROR_COLUMN, executeResult.get(Constant.MESSAGE));
                        errorDetails.add(errorDetail);
                    }
                } catch (Exception e) {
                    errorCount++;
                    Map<String, Object> errorDetail = new HashMap<>();
                    errorDetail.put("sql", sql);
                    errorDetail.put(ERROR_COLUMN, e.getMessage());
                    errorDetails.add(errorDetail);
                    log.error("执行SQL失败: {}", sql, e);
                }
            }

            result.setImportedRows(successCount);
            result.setErrorRows(errorCount);
            if (!errorDetails.isEmpty()) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("errors", errorDetails);
                result.setErrorDetails(errorMap);
            }

        } finally {
            result.setCostTime(System.currentTimeMillis() - startTime);
            DatabaseContextHolder.clear();
        }

        return result;
    }

    /**
     * 构建导出SQL
     */
    private String buildExportSql(String tableName, String whereClause) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(tableName);

        if (StringUtils.hasText(whereClause)) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    /**
     * 导出为Excel
     */
    private Resource exportToExcel(List<Map<String, Object>> data, String tableName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            if (data.isEmpty()) {
                Sheet sheet = workbook.createSheet(tableName);
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("无数据");
                workbook.write(outputStream);
                return new ByteArrayResource(outputStream.toByteArray());
            }

            Sheet sheet = workbook.createSheet(tableName);

            // 创建表头
            Set<String> headers = data.get(0).keySet();
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;
            for (String header : headers) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellValue(header);
            }

            // 填充数据
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                colIndex = 0;
                for (String header : headers) {
                    Cell cell = row.createCell(colIndex++);
                    setCellValue(cell, rowData.get(header));
                }
            }

            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }

    /**
     * 导出为CSV
     */
    private Resource exportToCsv(List<Map<String, Object>> data) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            if (data.isEmpty()) {
                csvPrinter.printRecord("无数据");
                csvPrinter.flush();
                return new ByteArrayResource(outputStream.toByteArray());
            }

            // 写入表头
            Set<String> headers = data.get(0).keySet();
            csvPrinter.printRecord(headers);

            // 写入数据
            for (Map<String, Object> rowData : data) {
                List<Object> rowValues = new ArrayList<>();
                for (String header : headers) {
                    rowValues.add(rowData.get(header));
                }
                csvPrinter.printRecord(rowValues);
            }

            csvPrinter.flush();
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }

    /**
     * 导出为JSON
     */
    private Resource exportToJson(List<Map<String, Object>> data) throws IOException {
        String json = new ObjectMapper().writeValueAsString(data);
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 导出为SQL
     */
    private Resource exportToSql(List<Map<String, Object>> data, String tableName) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("-- 导出表: ").append(tableName).append("\n");
        sqlBuilder.append("-- 导出时间: ").append(new Date()).append("\n\n");

        if (data.isEmpty()) {
            sqlBuilder.append("-- 无数据\n");
            return new ByteArrayResource(sqlBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }

        Set<String> columns = data.get(0).keySet();

        export(data, tableName, columns, sqlBuilder);

        return new ByteArrayResource(sqlBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void export(List<Map<String, Object>> data, String tableName, Set<String> columns, StringBuilder sqlBuilder) {
        for (Map<String, Object> row : data) {
            StringBuilder insertSql = new StringBuilder();
            insertSql.append("INSERT INTO ").append(tableName).append(" (");

            // 列名
            List<String> columnList = new ArrayList<>(columns);
            for (int i = 0; i < columnList.size(); i++) {
                insertSql.append(columnList.get(i));
                if (i < columnList.size() - 1) {
                    insertSql.append(", ");
                }
            }
            insertSql.append(") VALUES (");

            // 值
            for (int i = 0; i < columnList.size(); i++) {
                String column = columnList.get(i);
                Object value = row.get(column);
                if (value == null) {
                    insertSql.append("NULL");
                } else if (value instanceof Number) {
                    insertSql.append(value);
                } else {
                    insertSql.append("'").append(value.toString().replace("'", "''")).append("'");
                }

                if (i < columnList.size() - 1) {
                    insertSql.append(", ");
                }
            }
            insertSql.append(");\n");

            sqlBuilder.append(insertSql);
        }
    }

    /**
     * 验证表是否存在 - 通过 DatabaseService 获取所有表
     */
    private void validateTableExists(String tableName) {
        List<Map<String, Object>> tables = databaseService.getTables();
        boolean exists = tables.stream()
                .anyMatch(table -> tableName.equals(table.get("TABLE_NAME")));

        if (!exists) {
            throw new DbException("表不存在: " + tableName);
        }
    }

    /**
     * 清空表数据
     */
    private void truncateTable(String tableName) {
        String sql = "TRUNCATE TABLE " + tableName;
        databaseService.executeQuery(sql);
    }

    /**
     * 批量处理数据
     */
    private void processBatch(String tableName, List<String> tableColumns,
                              List<Map<String, String>> records,
                              AtomicInteger successCount,
                              List<Map<String, Object>> errorDetails) {
        List<Map<String, Object>> validRecords = new ArrayList<>();

        for (Map<String, String> stringMap : records) {
            try {
                Map<String, Object> stringObjectMap = validateAndConvertRecord(stringMap, tableColumns);
                validRecords.add(stringObjectMap);
            } catch (Exception e) {
                Map<String, Object> errorDetail = new HashMap<>(stringMap);
                errorDetail.put(ERROR_COLUMN, e.getMessage());
                errorDetails.add(errorDetail);
            }
        }

        log(tableName, records, successCount, errorDetails, validRecords);
    }

    private void log(String tableName, List<Map<String, String>> records, AtomicInteger successCount, List<Map<String, Object>> errorDetails, List<Map<String, Object>> validRecords) {
        if (!validRecords.isEmpty()) {
            try {
                // 批量插入
                int totalSuccess = 0;
                for (Map<String, Object> stringObjectMap : validRecords) {
                    String insertSql = buildInsertSql(tableName, stringObjectMap);
                    Map<String, Object> result = databaseService.executeQuery(insertSql);
                    if (Boolean.TRUE.equals(result.getOrDefault(Constant.SUCCESS, false))) {
                        Integer rows = (Integer) result.get("rows");
                        totalSuccess += (rows != null ? rows : 0);
                    }
                }
                successCount.addAndGet(totalSuccess);
            } catch (Exception e) {
                // 记录批量插入失败的错误
                for (Map<String, String> stringMap : records) {
                    Map<String, Object> errorDetail = new HashMap<>(stringMap);
                    errorDetail.put(ERROR_COLUMN, "批量插入失败: " + e.getMessage());
                    errorDetails.add(errorDetail);
                }
            }
        }
    }

    /**
     * 验证和转换记录
     */
    private Map<String, Object> validateAndConvertRecord(Map<String, String> stringStringMap,
                                                         List<String> tableColumns) {
        Map<String, Object> convertedRecord = new HashMap<>();

        for (String columnName : tableColumns) {
            String value = stringStringMap.get(columnName);

            if (value == null || value.trim().isEmpty()) {
                convertedRecord.put(columnName, null);
            } else {
                convertedRecord.put(columnName, convertValue(value));
            }
        }

        return convertedRecord;
    }

    /**
     * 转换值类型
     */
    private Object convertValue(String value) {
        if (value == null) {
            return null;
        }

        // 尝试转换为整数
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e1) {
            // 尝试转换为浮点数
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e2) {
                // 尝试转换为布尔值
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return Boolean.parseBoolean(value);
                }
                // 返回字符串
                return value.trim();
            }
        }
    }

    /**
     * 构建插入SQL
     */
    private String buildInsertSql(String tableName, Map<String, Object> stringObjectMap) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");

        List<String> columns = new ArrayList<>(stringObjectMap.keySet());
        for (int i = 0; i < columns.size(); i++) {
            sql.append(columns.get(i));
            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(") VALUES (");

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            Object value = stringObjectMap.get(column);

            if (value == null) {
                sql.append("NULL");
            } else if (value instanceof Number) {
                sql.append(value);
            } else if (value instanceof Boolean) {
                sql.append(value);
            } else {
                sql.append("'").append(value.toString().replace("'", "''")).append("'");
            }

            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(")");
        return sql.toString();
    }

    /**
     * 获取单元格值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return sdf.format(date);
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 设置单元格值
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}