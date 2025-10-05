package com.trading.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Database configuration using HikariCP connection pool
 */
public class DatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DataSource dataSource;
    
    /**
     * Get or create the datasource singleton
     */
    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatabaseConfig.class) {
                if (dataSource == null) {
                    dataSource = createDataSource();
                }
            }
        }
        return dataSource;
    }
    
    private static DataSource createDataSource() {
        Properties props = loadProperties();
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url", "jdbc:postgresql://localhost:5432/trading_db"));
        config.setUsername(props.getProperty("db.username", "trading_user"));
        config.setPassword(props.getProperty("db.password", "trading_pass"));
        
        // HikariCP settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        logger.info("Initializing database connection pool");
        return new HikariDataSource(config);
    }
    
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            logger.warn("Could not load application.properties, using defaults");
        }
        return props;
    }
}