package db.cl.gao.common.param;

import lombok.Data;

import java.util.Map;


@Data
public class ImportResult {

    private int importedRows;
    private int errorRows;
    private long costTime;
    private Map<String, Object> errorDetails;
}
