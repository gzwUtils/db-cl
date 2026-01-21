package db.cl.gao.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class OperationLog {
    private Long id;
    private String databaseName;
    private String operationType;
    private String tableName;
    private String sqlText;
    private Integer affectedRows;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date executeTime;

    private Boolean success;
    private String message;
    private String ipAddress;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}