package io.github.mocanjie.base.myjpa.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.github.mocanjie.base.mycommon.aspect.RequestParamValidAspect;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
@ConfigurationProperties(prefix ="myjpa.showsql")
public class MyJpaAutoConfiguration {

    @Value("${myjpa.showsql:true}")
    public boolean showSql;

    @Bean
    public TableInfoBuilder getTableInfoBuilder(){
        return new TableInfoBuilder();
    }

    @Bean
    @Primary
    public IBaseDao getBaseDaoImpl(){
        return new BaseDaoImpl();
    }

    @Bean
    public RequestParamValidAspect getRequestParamValidAspect(){
        return new RequestParamValidAspect();
    }

    @Bean
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


}
