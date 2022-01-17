package io.github.mocanjie.base.myjpa.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import io.github.mocanjie.base.myjpa.service.impl.BaseServiceImpl;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
@ConfigurationProperties(prefix ="myjpa.showsql")
public class MyJpaAutoConfiguration implements BeanPostProcessor, Ordered {

    @Value("${myjpa.showsql:true}")
    public boolean showSql;

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


    @PostConstruct
    void logInit(){
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("org.reflections").setLevel(Level.ERROR);
        if(showSql){
            loggerContext.getLogger("org.springframework.jdbc.core.JdbcTemplate").setLevel(Level.DEBUG);
            loggerContext.getLogger("org.springframework.jdbc.core.StatementCreatorUtils").setLevel(Level.TRACE);
        }
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
