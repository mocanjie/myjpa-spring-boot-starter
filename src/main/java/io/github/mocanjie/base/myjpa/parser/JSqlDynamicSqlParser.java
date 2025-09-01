package io.github.mocanjie.base.myjpa.parser;

import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 基于JSqlParser的动态SQL解析器
 * 提供更加精确和可靠的SQL解析和删除条件拼接功能
 */
public class JSqlDynamicSqlParser {
    
    private static final Logger log = LoggerFactory.getLogger(JSqlDynamicSqlParser.class);
    
    /**
     * 为SQL自动拼接逻辑删除条件
     * 
     * @param sql 原始SQL语句
     * @return 拼接删除条件后的SQL语句
     */
    public static String appendDeleteCondition(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            if (!(statement instanceof Select)) {
                // 非SELECT语句直接返回
                return sql;
            }
            
            Select selectStatement = (Select) statement;
            processSelectStatement(selectStatement);
            
            return selectStatement.toString();
            
        } catch (JSQLParserException e) {
            log.warn("解析SQL时发生异常，返回原始SQL: {}, 异常: {}", sql, e.getMessage());
            return sql;
        } catch (Exception e) {
            log.error("处理SQL时发生未知异常，返回原始SQL: {}", sql, e);
            return sql;
        }
    }
    
    /**
     * 为指定实体类的查询拼接删除条件
     * 
     * @param sql 原始SQL
     * @param entityClass 实体类
     * @param tableAlias 表别名（可选）
     * @return 拼接删除条件后的SQL
     */
    public static String appendDeleteConditionForEntity(String sql, Class<?> entityClass, String tableAlias) {
        if (sql == null || sql.trim().isEmpty() || entityClass == null) {
            return sql;
        }
        
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            if (!(statement instanceof Select)) {
                return sql;
            }
            
            TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByClass(entityClass);
            if (deleteInfo == null) {
                return sql;
            }
            
            Select selectStatement = (Select) statement;
            processSelectStatementForEntity(selectStatement, deleteInfo, tableAlias);
            
            return selectStatement.toString();
            
        } catch (JSQLParserException e) {
            log.warn("解析SQL时发生异常，返回原始SQL: {}, 异常: {}", sql, e.getMessage());
            return sql;
        } catch (Exception e) {
            log.error("为实体类{}拼接删除条件时发生异常: {}", entityClass.getName(), e.getMessage());
            return sql;
        }
    }
    
    /**
     * 处理SELECT语句，自动识别表并拼接删除条件
     */
    private static void processSelectStatement(Select selectStatement) {
        processSelect(selectStatement);
    }
    
    /**
     * 处理Select，支持各种类型的SELECT结构
     */
    private static void processSelect(Select select) {
        if (select instanceof PlainSelect) {
            processPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
            processSetOperationList((SetOperationList) select);
        }
    }
    
    /**
     * 处理简单SELECT语句
     */
    private static void processPlainSelect(PlainSelect plainSelect) {
        // 收集所有需要处理的表信息
        Map<String, TableDeleteCondition> tableConditions = new HashMap<>();
        
        // 获取现有的WHERE条件，用于检查是否已包含删除条件
        Expression existingWhere = plainSelect.getWhere();
        
        // 处理FROM子句中的表
        if (plainSelect.getFromItem() instanceof Table) {
            Table table = (Table) plainSelect.getFromItem();
            addTableCondition(tableConditions, table, existingWhere);
        } else if (plainSelect.getFromItem() instanceof ParenthesedSelect) {
            // 递归处理子查询
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) plainSelect.getFromItem();
            processSelect(parenthesedSelect.getSelect());
        }
        
        // 处理JOIN子句中的表
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getRightItem() instanceof Table) {
                    Table table = (Table) join.getRightItem();
                    addTableCondition(tableConditions, table, existingWhere);
                } else if (join.getRightItem() instanceof ParenthesedSelect) {
                    // 递归处理JOIN中的子查询
                    ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                    processSelect(parenthesedSelect.getSelect());
                }
            }
        }
        
        // 拼接删除条件到WHERE子句
        if (!tableConditions.isEmpty()) {
            Expression deleteConditions = buildDeleteConditionsExpression(tableConditions.values());
            
            Expression currentWhere = plainSelect.getWhere();
            if (currentWhere != null) {
                plainSelect.setWhere(new AndExpression(currentWhere, deleteConditions));
            } else {
                plainSelect.setWhere(deleteConditions);
            }
        }
        
        // 处理子查询中的SELECT
        processSubQueries(plainSelect);
    }
    
    /**
     * 处理UNION等复合查询
     */
    private static void processSetOperationList(SetOperationList setOperationList) {
        for (Select select : setOperationList.getSelects()) {
            processSelect(select);
        }
    }
    
    /**
     * 处理子查询
     */
    private static void processSubQueries(PlainSelect plainSelect) {
        // 处理SELECT列表中的子查询
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                processSelectItemSubQueries(selectItem);
            }
        }
        
        // 处理WHERE条件中的子查询
        if (plainSelect.getWhere() != null) {
            processExpressionSubQueries(plainSelect.getWhere());
        }
    }
    
    /**
     * 处理SELECT项中的子查询
     */
    private static void processSelectItemSubQueries(SelectItem selectItem) {
        if (selectItem.getExpression() != null) {
            processExpressionSubQueries(selectItem.getExpression());
        }
    }
    
    /**
     * 递归处理表达式中的子查询
     */
    private static void processExpressionSubQueries(Expression expression) {
        if (expression instanceof ParenthesedSelect) {
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) expression;
            processSelect(parenthesedSelect.getSelect());
        } else if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            processExpressionSubQueries(binaryExpression.getLeftExpression());
            processExpressionSubQueries(binaryExpression.getRightExpression());
        }
        // 可以根据需要添加更多表达式类型的处理
    }
    
    /**
     * 为特定实体类处理SELECT语句
     */
    private static void processSelectStatementForEntity(Select selectStatement, TableCacheManager.DeleteInfo deleteInfo, String tableAlias) {
        if (selectStatement instanceof PlainSelect) {
            processPlainSelectForEntity((PlainSelect) selectStatement, deleteInfo, tableAlias);
        }
    }
    
    /**
     * 为特定实体类处理简单SELECT语句
     */
    private static void processPlainSelectForEntity(PlainSelect plainSelect, TableCacheManager.DeleteInfo deleteInfo, String tableAlias) {
        // 检查现有WHERE条件中是否已包含删除字段条件
        Expression existingWhere = plainSelect.getWhere();
        if (isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), tableAlias, null)) {
            log.debug("SQL中已包含删除条件，跳过自动拼接");
            return;
        }
        
        Expression deleteCondition = createDeleteConditionExpression(deleteInfo, tableAlias);
        
        if (existingWhere != null) {
            plainSelect.setWhere(new AndExpression(existingWhere, deleteCondition));
        } else {
            plainSelect.setWhere(deleteCondition);
        }
    }
    
    /**
     * 添加表的删除条件信息（如果尚未存在相关条件）
     */
    private static void addTableCondition(Map<String, TableDeleteCondition> tableConditions, Table table, Expression existingWhere) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null) {
            // 检查现有WHERE条件中是否已包含删除字段条件
            if (!isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
                String key = alias != null ? alias : tableName;
                tableConditions.put(key, new TableDeleteCondition(tableName, alias, deleteInfo));
            } else {
                log.debug("表{}的删除条件已存在，跳过自动拼接", tableName);
            }
        }
    }
    
    /**
     * 构建删除条件表达式
     */
    private static Expression buildDeleteConditionsExpression(Collection<TableDeleteCondition> tableConditions) {
        List<Expression> conditions = new ArrayList<>();
        
        for (TableDeleteCondition tc : tableConditions) {
            Expression condition = createDeleteConditionExpressionForTable(tc);
            conditions.add(condition);
        }
        
        if (conditions.isEmpty()) {
            return null;
        }
        
        Expression result = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            result = new AndExpression(result, conditions.get(i));
        }
        
        return result;
    }
    
    /**
     * 检查WHERE表达式中是否已包含指定的删除字段条件
     * 
     * @param whereExpression WHERE表达式
     * @param deleteColumn 删除字段名
     * @param tableAlias 表别名
     * @param tableName 表名
     * @return 如果已存在删除条件返回true，否则返回false
     */
    private static boolean isDeleteConditionExists(Expression whereExpression, String deleteColumn, String tableAlias, String tableName) {
        if (whereExpression == null) {
            return false;
        }
        
        return containsDeleteColumn(whereExpression, deleteColumn, tableAlias, tableName);
    }
    
    /**
     * 递归检查表达式中是否包含删除字段
     */
    private static boolean containsDeleteColumn(Expression expression, String deleteColumn, String tableAlias, String tableName) {
        if (expression == null) {
            return false;
        }
        
        // 检查二元表达式（=, !=, <>, IS, IS NOT等）
        if (expression instanceof BinaryExpression) {
            BinaryExpression binExpr = (BinaryExpression) expression;
            Expression left = binExpr.getLeftExpression();
            
            // 检查左侧是否为我们关心的列
            if (left instanceof Column) {
                Column column = (Column) left;
                if (isTargetColumn(column, deleteColumn, tableAlias, tableName)) {
                    log.debug("发现删除字段条件: {}", expression.toString());
                    return true;
                }
            }
        }
        
        // 递归检查复合表达式（AND, OR等）
        if (expression instanceof AndExpression) {
            AndExpression andExpr = (AndExpression) expression;
            return containsDeleteColumn(andExpr.getLeftExpression(), deleteColumn, tableAlias, tableName) ||
                   containsDeleteColumn(andExpr.getRightExpression(), deleteColumn, tableAlias, tableName);
        }
        
        if (expression instanceof OrExpression) {
            OrExpression orExpr = (OrExpression) expression;
            return containsDeleteColumn(orExpr.getLeftExpression(), deleteColumn, tableAlias, tableName) ||
                   containsDeleteColumn(orExpr.getRightExpression(), deleteColumn, tableAlias, tableName);
        }
        
        return false;
    }
    
    /**
     * 检查列是否为目标删除字段
     */
    private static boolean isTargetColumn(Column column, String deleteColumn, String tableAlias, String tableName) {
        String columnName = column.getColumnName();
        
        // 检查列名是否匹配
        if (!deleteColumn.equalsIgnoreCase(columnName)) {
            return false;
        }
        
        // 如果列没有指定表，认为匹配（可能是无限定的字段引用）
        Table columnTable = column.getTable();
        if (columnTable == null) {
            return true;
        }
        
        String columnTableName = columnTable.getName();
        
        // 检查表名或别名是否匹配
        if (tableAlias != null && tableAlias.equalsIgnoreCase(columnTableName)) {
            return true;
        }
        
        if (tableName != null && tableName.equalsIgnoreCase(columnTableName)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 创建单个删除条件表达式
     */
    private static Expression createDeleteConditionExpression(TableCacheManager.DeleteInfo deleteInfo, String tableAlias) {
        Column column = new Column();
        if (tableAlias != null && !tableAlias.trim().isEmpty()) {
            column.setTable(new Table(tableAlias));
        }
        column.setColumnName(deleteInfo.getDelColumn());
        
        LongValue value = new LongValue(deleteInfo.getUnDelValue());
        
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(value);
        
        return equalsTo;
    }
    
    /**
     * 为表条件创建删除条件表达式
     */
    private static Expression createDeleteConditionExpressionForTable(TableDeleteCondition tableCondition) {
        Column column = new Column();
        String tableRef = tableCondition.alias != null ? tableCondition.alias : tableCondition.tableName;
        column.setTable(new Table(tableRef));
        column.setColumnName(tableCondition.deleteInfo.getDelColumn());
        
        LongValue value = new LongValue(tableCondition.deleteInfo.getUnDelValue());
        
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(value);
        
        return equalsTo;
    }
    
    /**
     * 表删除条件信息
     */
    private static class TableDeleteCondition {
        final String tableName;
        final String alias;
        final TableCacheManager.DeleteInfo deleteInfo;
        
        TableDeleteCondition(String tableName, String alias, TableCacheManager.DeleteInfo deleteInfo) {
            this.tableName = tableName;
            this.alias = alias;
            this.deleteInfo = deleteInfo;
        }
    }
}