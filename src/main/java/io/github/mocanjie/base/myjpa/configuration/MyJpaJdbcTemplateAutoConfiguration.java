package io.github.mocanjie.base.myjpa.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * MyJPA JdbcTemplate Auto Configuration
 * <p>
 * Automatically provides JdbcTemplate and NamedParameterJdbcTemplate beans
 * for projects using MyJPA framework.
 * <p>
 * This eliminates the need for manual DataSourceConfig in each project.
 *
 * @author MyJPA
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class MyJpaJdbcTemplateAutoConfiguration {

    /**
     * Create JdbcTemplate bean
     * <p>
     * Only creates if no JdbcTemplate bean already exists in the application context.
     *
     * @param dataSource the DataSource to use
     * @return JdbcTemplate instance
     */
    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Create NamedParameterJdbcTemplate bean
     * <p>
     * Required by MyJPA framework for named parameter queries.
     * Only creates if no NamedParameterJdbcTemplate bean already exists.
     *
     * @param jdbcTemplate the JdbcTemplate to wrap
     * @return NamedParameterJdbcTemplate instance
     */
    @Bean
    @ConditionalOnMissingBean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}
