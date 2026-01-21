package db.cl.gao.common.param;

import db.cl.gao.common.enums.ExportFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;


@Data
public class ExportRequest {

    @NotBlank(message = "表名不能为空")
    private String tableName;

    private ExportFormat format = ExportFormat.EXCEL;

    private String whereClause;
}
