package db.cl.gao.controller;

import db.cl.gao.common.ApiOutput;
import db.cl.gao.common.annotation.LogOperation;
import db.cl.gao.common.enums.ExportFormat;
import db.cl.gao.common.param.ExportRequest;
import db.cl.gao.common.param.ImportResult;
import db.cl.gao.service.DataImportExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.IOException;

/**
 * 数据导入导出控制器
 *
 * @author system
 * @since 2024-01-01
 */
@Valid
@SuppressWarnings("unused")
@Slf4j
@RestController
@RequestMapping("/api/data")
public class DataImportExportController {

    private static final int MAX_FILE_SIZE_MB = 1024;

    private final DataImportExportService dataImportExportService;

    @Autowired
    public DataImportExportController(DataImportExportService dataImportExportService) {
        this.dataImportExportService = dataImportExportService;
    }

    /**
     * 数据导出
     */
    @LogOperation(type = LogOperation.OperationType.EXPORT, tableName = "#request.tableName",
            dynamicType = false)
    @PostMapping("/export")
    public ResponseEntity<Resource> exportData(
            @RequestHeader(value = "X-Database", required = false) String database,
            @Valid @RequestBody ExportRequest request) throws IOException {

        log.info("导出数据请求: database={}, request={}", database, request);

        Resource resource = dataImportExportService.exportData(database, request);

        String fileName = String.format("%s_export.%s",
                request.getTableName(),
                request.getFormat().getExtension());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * 下载模板
     */
    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate(
            @RequestHeader(value = "X-Database", required = false) String database,
            @RequestParam @NotBlank(message = "表名不能为空") String tableName) throws IOException {

        log.info("下载模板请求: database={}, tableName={}", database, tableName);

        Resource resource = dataImportExportService.generateTemplate(database, tableName);

        String fileName = String.format("%s_template.csv", tableName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * CSV导入
     */
    @LogOperation(type = LogOperation.OperationType.IMPORT, tableName = "#tableName",
            dynamicType = false,value = "CSV导入")
    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiOutput<ImportResult> importCsv(
            @RequestHeader(value = "X-Database", required = false) String database,
            @RequestParam @NotBlank(message = "表名不能为空") String tableName,
            @RequestParam(defaultValue = "false") boolean truncateFirst,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file, "csv");
        log.info("CSV导入请求: database={}, tableName={}, truncateFirst={}",
                database, tableName, truncateFirst);

        ImportResult result = dataImportExportService.importCsv(
                database, tableName, truncateFirst, file);

        return ApiOutput.success(result);
    }

    /**
     * Excel导入
     */
    @LogOperation(type = LogOperation.OperationType.IMPORT, tableName = "#tableName",
            dynamicType = false,value = "Excel导入")
    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiOutput<ImportResult> importExcel(
            @RequestHeader(value = "X-Database", required = false) String database,
            @RequestParam @NotBlank(message = "表名不能为空") String tableName,
            @RequestParam(defaultValue = "false") boolean truncateFirst,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file, "excel");
        log.info("Excel导入请求: database={}, tableName={}, truncateFirst={}",
                database, tableName, truncateFirst);

        ImportResult result = dataImportExportService.importExcel(
                database, tableName, truncateFirst, file);

        return ApiOutput.success(result);
    }

    /**
     * SQL导入
     */
    @LogOperation(type = LogOperation.OperationType.IMPORT, tableName = "#tableName",
            dynamicType = false,value = "SQL导入")
    @PostMapping(value = "/import/sql", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiOutput<ImportResult> importSql(
            @RequestHeader(value = "X-Database", required = false) String database,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file, "sql");
        log.info("SQL导入请求: database={}", database);

        ImportResult result = dataImportExportService.importSql(database, file);

        return ApiOutput.success(result);
    }

    /**
     * 验证上传文件
     */
    private void validateFile(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        long fileSize = file.getSize();
        long maxSize = MAX_FILE_SIZE_MB * 1024 * 1024L;

        if (fileSize > maxSize) {
            throw new IllegalArgumentException(
                    String.format("文件大小不能超过%dMB", MAX_FILE_SIZE_MB));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        boolean isValid = isIsValid(fileType, originalFilename);

        if (!isValid) {
            throw new IllegalArgumentException(
                    String.format("只能上传%s格式文件", fileType.toUpperCase()));
        }
    }

    private static boolean isIsValid(String fileType, String originalFilename) {
        ExportFormat exportFormat = ExportFormat.fromExtension(fileType);
        boolean isValid = false;
        if(exportFormat != null ){
            isValid = originalFilename.toLowerCase().endsWith("."+exportFormat.getExtension());
        }
        return isValid;
    }
}
