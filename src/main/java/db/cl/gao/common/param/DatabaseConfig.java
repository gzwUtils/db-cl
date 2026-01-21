package db.cl.gao.common.param;


import lombok.Data;

@Data
public class DatabaseConfig {
    private String name;        // 配置名称，如：测试库、生产库
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    // 可选：连接池配置
    private Integer initialSize = 5;
    private Integer minIdle = 5;
    private Integer maxActive = 20;
}
