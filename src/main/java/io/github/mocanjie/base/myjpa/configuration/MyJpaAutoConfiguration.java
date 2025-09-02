package io.github.mocanjie.base.myjpa.configuration;

import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import io.github.mocanjie.base.myjpa.service.impl.BaseServiceImpl;
import io.github.mocanjie.base.myjpa.validation.DatabaseSchemaValidator;
import io.github.mocanjie.base.myjpa.validation.SchemaValidationRunner;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
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

import javax.annotation.PostConstruct;
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
        Configurator.setLevel("org.reflections", Level.ERROR);

        if (showSql) {
            Configurator.setLevel("org.springframework.jdbc.core.JdbcTemplate", Level.DEBUG);
            Configurator.setLevel("org.springframework.jdbc.core.StatementCreatorUtils", Level.TRACE);
        }
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
