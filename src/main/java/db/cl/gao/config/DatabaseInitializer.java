package db.cl.gao.config;

import db.cl.gao.common.param.DatabaseConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final DatabaseConfigManager configManager;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${spring.datasource.driver-class-name:}")
    private String driverClassName;

    public DatabaseInitializer(DatabaseConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void run(String... args) {
        log.info("初始化数据库配置...");

        // 如果配置文件中有数据库配置，添加到配置管理器中
        if (datasourceUrl != null && !datasourceUrl.isEmpty()) {
            try {
                DatabaseConfig config = new DatabaseConfig();
                config.setName("default");
                config.setUrl(datasourceUrl);
                config.setUsername(datasourceUsername);
                config.setPassword(datasourcePassword);
                config.setDriverClassName(driverClassName);
                config.setInitialSize(5);
                config.setMinIdle(5);
                config.setMaxActive(20);

                configManager.addOrUpdateConfig("default", config);
                log.info("已添加默认数据库配置: {}", datasourceUrl);
            } catch (Exception e) {
                log.error("添加默认数据库配置失败", e);
            }
        }

        log.info("数据库配置初始化完成");
    }
}
