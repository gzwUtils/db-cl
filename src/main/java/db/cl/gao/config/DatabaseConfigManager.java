package db.cl.gao.config;

import com.alibaba.druid.pool.DruidDataSource;
import db.cl.gao.common.Constant;
import db.cl.gao.common.param.DatabaseConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.*;

@Slf4j
@Component
public class DatabaseConfigManager {

    // 存储所有数据源的配置
    private final Map<String, DatabaseConfig> configs = new HashMap<>();

    // 存储已创建的数据源
    private final Map<String, DataSource> dataSources = new HashMap<>();

    // 当前默认数据源
    private DataSource defaultDataSource;

    @PostConstruct
    public void init() {
        log.info("DatabaseConfigManager初始化完成");
    }

    /**
     * 设置默认数据源
     */
    public void setDefaultDataSource(DataSource dataSource) {
        this.defaultDataSource = dataSource;
        DatabaseConfig defaultConfig = new DatabaseConfig();
        defaultConfig.setName(Constant.DEFAULT_DATABASE);
        defaultConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        configs.put(Constant.DEFAULT_DATABASE, defaultConfig);
        dataSources.put(Constant.DEFAULT_DATABASE, dataSource);
        log.info("设置默认数据源: default");
    }

    /**
     * 获取数据源
     */
    public DataSource getDataSource(String dbKey) {
        if (dbKey == null || dbKey.trim().isEmpty()) {
            return getDefaultDataSource();
        }

        String key = dbKey.trim();

        // 如果已存在，直接返回
        if (dataSources.containsKey(key)) {
            return dataSources.get(key);
        }

        // 创建新的数据源
        DatabaseConfig config = configs.get(key);
        if (config == null) {
            log.warn("数据库配置不存在: {}，使用默认数据源", key);
            return getDefaultDataSource();
        }

        try {
            log.info("创建新的数据源: {}", key);
            DruidDataSource dataSource = getDruidDataSource(config);

            dataSources.put(key, dataSource);
            return dataSource;
        } catch (Exception e) {
            log.error("创建数据源失败: {}", key, e);
            return getDefaultDataSource();
        }
    }

    private static DruidDataSource getDruidDataSource(DatabaseConfig config) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setInitialSize(config.getInitialSize() > 0 ? config.getInitialSize() : 5);
        dataSource.setMinIdle(config.getMinIdle() > 0 ? config.getMinIdle() : 5);
        dataSource.setMaxActive(config.getMaxActive() > 0 ? config.getMaxActive() : 20);
        dataSource.setTestWhileIdle(true);
        dataSource.setValidationQuery("SELECT 1");
        return dataSource;
    }

    /**
     * 获取默认数据源
     */
    private DataSource getDefaultDataSource() {
        if (defaultDataSource == null) {
            throw new IllegalStateException("默认数据源未初始化");
        }
        return defaultDataSource;
    }

    /**
     * 获取所有数据库配置
     */
    public Map<String, DatabaseConfig> getAllConfigs() {
        return new HashMap<>(configs);
    }

    /**
     * 添加或更新数据库配置
     */
    public void addOrUpdateConfig(String key, DatabaseConfig config) {
        configs.put(key, config);
        // 移除旧的数据源，下次获取时会重新创建
        dataSources.remove(key);
        log.info("添加/更新数据库配置: {}", key);
    }

    /**
     * 获取所有数据库名称
     */
    public List<String> getDatabaseNames() {
        return new ArrayList<>(configs.keySet());
    }

    /**
     * 验证数据库配置是否存在
     */
    public boolean containsDatabase(String dbName) {
        return configs.containsKey(dbName);
    }

    /**
     * 清理数据源
     */
    public void clearDataSource(String dbKey) {
        if (dataSources.containsKey(dbKey)) {
            DataSource ds = dataSources.remove(dbKey);
            if (ds instanceof DruidDataSource) {
                ((DruidDataSource) ds).close();
            }
        }
    }
}