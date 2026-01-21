package db.cl.gao.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;


@Data
public class SqlHistory {

    private Long id;

    private String databaseName;
    // 执行的数据库名
    private String sqlText;
    // SQL语句
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date executeTime;
    // 执行时间
    private Long costTime;
    // 耗时(毫秒)
    private Integer resultCount;
    // 结果数量
    private Boolean success;
    // 是否成功
    private String errorMessage;
    // 错误信息
    private String ipAddress;
    // 客户端IP
    private String createdBy;
    // 创建人
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    // 创建时间
}
