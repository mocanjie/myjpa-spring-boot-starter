package io.github.mocanjie.base.myjpa.configuration;

import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import io.github.mocanjie.base.myjpa.service.impl.BaseServiceImpl;
import io.github.mocanjie.base.myjpa.validation.DatabaseSchemaValidator;
import io.github.mocanjie.base.myjpa.validation.SchemaValidationRunner;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
@ConfigurationProperties(prefix ="myjpa.showsql")
public class MyJpaAutoConfiguration implements BeanPostProcessor, Ordered {

    @Value("${myjpa.showsql:true}")
    public boolean showSql;
    
    @Value("${myjpa.validate-schema:true}")
    public boolean validateSchema;

    @Bean
    @Primary
    public IBaseService getBaseService(){
        return new BaseServiceImpl();
    }

    @Bean
    @Primary
    public TableInfoBuilder getTableInfoBuilder(){
        return new TableInfoBuilder();
    }

    @Bean
    @Primary
    public IBaseDao getBaseDaoImpl(){
        return new BaseDaoImpl();
    }

    @Bean
    @Primary
    @ConditionalOnClass(DataSource.class)
    public SqlBuilder getSqlBuilder(){
        return new SqlBuilder();
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public DatabaseSchemaValidator getDatabaseSchemaValidator(JdbcTemplate jdbcTemplate, DataSource dataSource){
        return new DatabaseSchemaValidator(jdbcTemplate, dataSource);
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public SchemaValidationRunner getSchemaValidationRunner(){
        return new SchemaValidationRunner();
    }


    @PostConstruct
    void logInit(){
        try {
            // 使用反射来兼容不同的日志实现
            Object loggerContext = LoggerFactory.getILoggerFactory();

            // 设置 org.reflections 为 ERROR 级别
            setLogLevel(loggerContext, "org.reflections", "ERROR");

            if (showSql) {
                // 启用 SQL 日志
                setLogLevel(loggerContext, "org.springframework.jdbc.core.JdbcTemplate", "DEBUG");
                setLogLevel(loggerContext, "org.springframework.jdbc.core.StatementCreatorUtils", "TRACE");
            }
        } catch (Exception e) {
            // 如果日志级别设置失败，忽略
            System.out.println("警告: 无法设置日志级别: " + e.getMessage());
        }
    }

    private void setLogLevel(Object loggerContext, String loggerName, String level) {
        try {
            // 尝试 Logback 方式
            Class<?> loggerContextClass = loggerContext.getClass();
            if (loggerContextClass.getName().contains("logback")) {
                Object logger = loggerContextClass.getMethod("getLogger", String.class)
                    .invoke(loggerContext, loggerName);
                Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
                Object levelObj = levelClass.getField(level).get(null);
                logger.getClass().getMethod("setLevel", levelClass).invoke(logger, levelObj);
            }
        } catch (Exception e) {
            // 静默忽略日志级别设置失败
        }
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
