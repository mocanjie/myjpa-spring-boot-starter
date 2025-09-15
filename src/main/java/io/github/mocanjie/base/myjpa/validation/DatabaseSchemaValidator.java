package io.github.mocanjie.base.myjpa.validation;

import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 数据库模式验证器
 * 在启动时验证@MyTable配置的字段是否在数据库中真实存在
 * 实现BeanPostProcessor和Ordered接口，确保在TableInfoBuilder之后但优先级很高的时候执行验证
 */
public class DatabaseSchemaValidator implements BeanPostProcessor, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaValidator.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private String databaseType;
    
    public DatabaseSchemaValidator(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.databaseType = detectDatabaseType();
    }
    
    /**
     * 在Bean初始化完成后自动执行数据库模式验证
     * 通过Order机制确保在TableInfoBuilder之后执行，此时TableCacheManager已经初始化完成
     */
    @PostConstruct
    private void init() {
        log.info("开始执行数据库删除字段验证...");
        try {
            ValidationResult result = validateAllTables();
            
            if (result.hasErrors()) {
                log.error("数据库删除字段验证发现错误，相关表的删除条件将被禁用");
            } else {
                log.info("数据库删除字段验证通过");
            }
        } catch (Exception e) {
            log.error("数据库删除字段验证过程中发生异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 验证所有@MyTable配置
     * 
     * @return 验证结果
     */
    public ValidationResult validateAllTables() {
        ValidationResult result = new ValidationResult();
        
        // 获取所有缓存的表信息
        Set<String> allTableNames = getAllCachedTableNames();
        
        if (allTableNames.isEmpty()) {
            log.warn("没有找到任何@MyTable注解配置的表");
            return result;
        }
        
        log.info("开始验证{}个表的数据库模式...", allTableNames.size());
        
        for (String tableName : allTableNames) {
            validateTable(tableName, result);
        }
        
        logValidationSummary(result);
        return result;
    }
    
    /**
     * 验证单个表
     */
    private void validateTable(String tableName, ValidationResult result) {
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo == null) {
            result.addWarning(tableName, "未找到删除条件配置信息");
            return;
        }
        
        try {
            // 检查表是否存在
            if (!tableExists(tableName)) {
                result.addError(tableName, String.format("表 '%s' 在数据库中不存在", tableName));
                return;
            }
            
            // 获取表的所有列
            Set<String> columns = getTableColumns(tableName);
            
            // 验证主键字段
            String pkColumn = getPkColumnFromCache(tableName);
            if (pkColumn != null && !columns.contains(pkColumn.toLowerCase())) {
                result.addError(tableName, String.format("主键字段 '%s' 在表 '%s' 中不存在", pkColumn, tableName));
            }
            
            // 验证删除标记字段
            String delColumn = deleteInfo.getDelColumn();
            if (delColumn != null && !delColumn.isEmpty() && !columns.contains(delColumn.toLowerCase())) {
                // 标记字段为无效，不报错
                TableCacheManager.markDeleteFieldAsInvalid(tableName);
                result.addWarning(tableName, String.format("删除标记字段 '%s' 在表 '%s' 中不存在，已跳过删除条件拼接", delColumn, tableName));
            } else if (delColumn != null && !delColumn.isEmpty()) {
                result.addSuccess(tableName, String.format("删除标记字段 '%s' 验证通过", delColumn));
            }
            
            log.debug("表 '{}' 验证完成，字段数: {}", tableName, columns.size());
            
        } catch (Exception e) {
            result.addError(tableName, String.format("验证表 '%s' 时发生异常: %s", tableName, e.getMessage()));
            log.error("验证表 '{}' 时发生异常", tableName, e);
        }
    }
    
    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try {
            switch (databaseType.toLowerCase()) {
                case "mysql":
                    return checkTableExistsMySQL(tableName);
                case "oracle":
                    return checkTableExistsOracle(tableName);
                case "sqlserver":
                    return checkTableExistsSQLServer(tableName);
                case "postgresql":
                case "kingbasees":
                    return checkTableExistsPostgreSQL(tableName);
                default:
                    return checkTableExistsGeneric(tableName);
            }
        } catch (Exception e) {
            log.warn("检查表 '{}' 是否存在时发生异常: {}", tableName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取表的所有列名
     */
    private Set<String> getTableColumns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        
        try (ResultSet rs = dataSource.getConnection().getMetaData().getColumns(
                null, null, tableName.toUpperCase(), null)) {
            
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        } catch (SQLException e) {
            // 如果元数据查询失败，尝试使用SQL查询
            log.warn("使用DatabaseMetaData查询表 '{}' 列信息失败，尝试SQL查询: {}", tableName, e.getMessage());
            columns = getTableColumnsUsingSql(tableName);
        }
        
        return columns;
    }
    
    /**
     * 使用SQL查询获取表列信息（备用方案）
     */
    private Set<String> getTableColumnsUsingSql(String tableName) {
        Set<String> columns = new HashSet<>();
        
        try {
            String sql = buildColumnQuerySql(tableName);
            jdbcTemplate.query(sql, rs -> {
                columns.add(rs.getString(1).toLowerCase());
            });
        } catch (DataAccessException e) {
            log.error("使用SQL查询表 '{}' 列信息也失败: {}", tableName, e.getMessage());
        }
        
        return columns;
    }
    
    /**
     * 构建查询表列的SQL语句
     */
    private String buildColumnQuerySql(String tableName) {
        switch (databaseType.toLowerCase()) {
            case "mysql":
                return String.format("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s'", tableName);
            case "oracle":
                return String.format("SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = '%s'", tableName.toUpperCase());
            case "sqlserver":
                return String.format("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s'", tableName);
            case "postgresql":
            case "kingbasees":
                return String.format("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s'", tableName.toLowerCase());
            default:
                return String.format("SELECT * FROM %s WHERE 1=0", tableName);
        }
    }
    
    // 数据库特定的表存在性检查方法
    private boolean checkTableExistsMySQL(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", 
                Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkTableExistsOracle(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = ?", 
                Integer.class, tableName.toUpperCase());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkTableExistsSQLServer(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", 
                Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkTableExistsPostgreSQL(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", 
                Integer.class, tableName.toLowerCase());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkTableExistsGeneric(String tableName) {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + tableName + " WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检测数据库类型
     */
    private String detectDatabaseType() {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();
            
            if (productName.contains("mysql")) {
                return "mysql";
            } else if (productName.contains("oracle")) {
                return "oracle";
            } else if (productName.contains("microsoft") || productName.contains("sql server")) {
                return "sqlserver";
            } else if (productName.contains("postgresql")) {
                return "postgresql";
            } else if (productName.contains("kingbase")) {
                return "kingbasees";
            } else {
                log.warn("未识别的数据库类型: {}, 将使用通用方式", productName);
                return "generic";
            }
        } catch (SQLException e) {
            log.error("检测数据库类型时发生异常", e);
            return "generic";
        }
    }
    
    /**
     * 获取所有缓存的表名
     */
    private Set<String> getAllCachedTableNames() {
        return TableCacheManager.getAllTableNames();
    }
    
    /**
     * 从缓存中获取主键字段名
     */
    private String getPkColumnFromCache(String tableName) {
        TableCacheManager.PkInfo pkInfo = TableCacheManager.getPkInfoByTableName(tableName);
        return pkInfo != null ? pkInfo.getPkColumn() : null;
    }
    
    /**
     * 记录验证总结
     */
    private void logValidationSummary(ValidationResult result) {
        log.info("数据库模式验证完成：");
        log.info("  - 验证表数: {}", result.getTotalTables());
        log.info("  - 成功: {}", result.getSuccessCount());
        log.info("  - 警告: {}", result.getWarningCount());
        log.info("  - 错误: {}", result.getErrorCount());
        
        if (result.hasErrors()) {
            log.error("发现配置错误，建议检查@MyTable注解配置：");
            result.getErrors().forEach((table, errors) -> {
                errors.forEach(error -> log.error("  [{}] {}", table, error));
            });
        }
        
        if (result.hasWarnings()) {
            log.warn("发现配置警告：");
            result.getWarnings().forEach((table, warnings) -> {
                warnings.forEach(warning -> log.warn("  [{}] {}", table, warning));
            });
        }
    }
    
    /**
     * 设置Bean初始化的优先级
     * 返回1，确保在TableInfoBuilder(优先级0)之后立即执行
     */
    @Override
    public int getOrder() {
        return 1;
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final Map<String, List<String>> errors = new HashMap<>();
        private final Map<String, List<String>> warnings = new HashMap<>();
        private final Map<String, List<String>> successes = new HashMap<>();
        
        public void addError(String tableName, String message) {
            errors.computeIfAbsent(tableName, k -> new ArrayList<>()).add(message);
        }
        
        public void addWarning(String tableName, String message) {
            warnings.computeIfAbsent(tableName, k -> new ArrayList<>()).add(message);
        }
        
        public void addSuccess(String tableName, String message) {
            successes.computeIfAbsent(tableName, k -> new ArrayList<>()).add(message);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public int getErrorCount() {
            return errors.values().stream().mapToInt(List::size).sum();
        }
        
        public int getWarningCount() {
            return warnings.values().stream().mapToInt(List::size).sum();
        }
        
        public int getSuccessCount() {
            return successes.values().stream().mapToInt(List::size).sum();
        }
        
        public int getTotalTables() {
            Set<String> allTables = new HashSet<>();
            allTables.addAll(errors.keySet());
            allTables.addAll(warnings.keySet());
            allTables.addAll(successes.keySet());
            return allTables.size();
        }
        
        public Map<String, List<String>> getErrors() {
            return new HashMap<>(errors);
        }
        
        public Map<String, List<String>> getWarnings() {
            return new HashMap<>(warnings);
        }
        
        public Map<String, List<String>> getSuccesses() {
            return new HashMap<>(successes);
        }
    }
}