package io.github.mocanjie.base.myjpa.parser;

import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.tenant.TenantContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 基于JSqlParser的动态SQL解析器
 * 提供更加精确和可靠的SQL解析和删除条件拼接功能
 */
public class JSqlDynamicSqlParser {

    private static final Logger log = LoggerFactory.getLogger(JSqlDynamicSqlParser.class);

    // ===================== 租户配置（由 MyJpaAutoConfiguration 初始化时设置）=====================

    /** 是否启用多租户隔离，默认 false（需显式配置 myjpa.tenant.enabled=true 开启） */
    public static volatile boolean tenantEnabled = false;

    /** 租户字段的数据库列名，默认 tenant_id */
    public static volatile String tenantColumn = "tenant_id";

    /** 租户参数名（SQL 占位符名称），内部固定，不对外暴露 */
    public static final String TENANT_PARAM_NAME = "myjpaTenantId";

    /**
     * 为 INSERT SQL 追加租户列和值占位符（幂等：已含租户列时直接返回原 SQL）。
     *
     * <pre>
     * INSERT INTO user(name, email) VALUES (:name, :email)
     * → INSERT INTO user(name, email, tenant_id) VALUES (:name, :email, :myjpaTenantId)
     * </pre>
     *
     * <p>注意：此方法不检查表名注册，由调用方（{@code BaseDaoImpl}）负责判断表是否有租户字段。
     *
     * @param sql 原始 INSERT SQL
     * @return 追加租户列后的 SQL；全局关闭、已含租户列或格式无法识别时返回原 SQL
     */
    public static String appendTenantToInsertSql(String sql) {
        if (!tenantEnabled || sql == null || sql.isBlank()) return sql;
        if (sql.toLowerCase().contains(tenantColumn.toLowerCase())) return sql;
        String upperSql = sql.toUpperCase();
        int closeParen = upperSql.indexOf(") VALUES");
        if (closeParen == -1) return sql;
        int lastClose = sql.lastIndexOf(")");
        if (lastClose <= closeParen) return sql;
        return sql.substring(0, closeParen)
                + ", " + tenantColumn + ")"
                + sql.substring(closeParen + 1, lastClose)
                + ", :" + TENANT_PARAM_NAME + ")";
    }

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
    
    // ===================== 多租户条件注入 =====================

    /**
     * 为 SQL 自动拼接租户隔离条件（参数化，生成 :myjpaTenantId 占位符）
     *
     * <p>以下情况不注入：
     * <ul>
     *   <li>全局开关 {@code myjpa.tenant.enabled=false}</li>
     *   <li>当前线程调用了 {@link TenantContext#skip()}</li>
     *   <li>SQL 中的表在数据库中不存在租户字段（由启动时扫描确定）</li>
     * </ul>
     *
     * @param sql 原始 SQL（通常已经过 appendDeleteCondition 处理）
     * @return 拼接租户条件后的 SQL，不需要拼接则返回原 SQL
     */
    public static String appendTenantCondition(String sql) {
        if (!tenantEnabled || sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        if (TenantContext.isSkipped()) {
            log.debug("当前线程已标记跳过租户隔离，不注入租户条件");
            return sql;
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                return sql;
            }
            Select selectStatement = (Select) statement;
            processTenantSelect(selectStatement);
            return selectStatement.toString();
        } catch (JSQLParserException e) {
            log.warn("解析SQL时发生异常（租户条件），返回原始SQL: {}, 异常: {}", sql, e.getMessage());
            return sql;
        } catch (Exception e) {
            log.error("处理SQL时发生未知异常（租户条件），返回原始SQL: {}", sql, e);
            return sql;
        }
    }

    /**
     * 处理 SELECT 语句，注入租户条件（复用 processSelect 框架，但走租户分支）
     */
    private static void processTenantSelect(Select select) {
        if (select instanceof PlainSelect) {
            processTenantPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
            for (Select s : ((SetOperationList) select).getSelects()) {
                processTenantSelect(s);
            }
        }
    }

    /**
     * 处理简单 SELECT 语句的租户条件注入，逻辑与 processPlainSelect 对称
     */
    private static void processTenantPlainSelect(PlainSelect plainSelect) {
        List<TableTenantCondition> whereConditions = new ArrayList<>();
        List<TableTenantCondition> joinConditions = new ArrayList<>();

        Expression existingWhere = plainSelect.getWhere();

        // 主表
        if (plainSelect.getFromItem() instanceof Table) {
            Table table = (Table) plainSelect.getFromItem();
            addTableTenantConditionByType(table, existingWhere, JoinType.FROM_TABLE, null, whereConditions, joinConditions);
        } else if (plainSelect.getFromItem() instanceof ParenthesedSelect) {
            processTenantSelect(((ParenthesedSelect) plainSelect.getFromItem()).getSelect());
        }

        // JOIN 表
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getRightItem() instanceof Table) {
                    addTableTenantConditionByType((Table) join.getRightItem(), existingWhere,
                            getJoinType(join), join, whereConditions, joinConditions);
                } else if (join.getRightItem() instanceof ParenthesedSelect) {
                    processTenantSelect(((ParenthesedSelect) join.getRightItem()).getSelect());
                }
            }
        }

        // 注入 WHERE 条件
        if (!whereConditions.isEmpty()) {
            Expression tenantConditions = buildTenantConditionsExpression(whereConditions);
            Expression currentWhere = plainSelect.getWhere();
            plainSelect.setWhere(currentWhere != null
                    ? new AndExpression(currentWhere, tenantConditions)
                    : tenantConditions);
        }

        // 注入 JOIN ON 条件
        for (TableTenantCondition condition : joinConditions) {
            addTenantConditionToJoinOn(condition);
        }

        // 递归处理子查询
        processTenantSubQueries(plainSelect);
    }

    private static void addTableTenantConditionByType(Table table, Expression existingWhere, JoinType joinType,
                                                       Join joinObject,
                                                       List<TableTenantCondition> whereConditions,
                                                       List<TableTenantCondition> joinConditions) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;

        if (!TableCacheManager.hasTenantColumn(tableName)) {
            return;
        }

        // 幂等检查：如果 WHERE 中已经有 tenant 条件，跳过
        if (isTenantConditionExists(existingWhere, tenantColumn, alias, tableName)) {
            log.debug("表 {} 的租户条件已存在，跳过自动拼接", tableName);
            return;
        }

        TableTenantCondition condition = new TableTenantCondition(tableName, alias, joinType, joinObject);
        if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
            joinConditions.add(condition);
        } else {
            whereConditions.add(condition);
        }
        log.debug("表 {}({}) 的租户条件将添加到 {}", tableName, joinType,
                (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
    }

    private static Expression buildTenantConditionsExpression(List<TableTenantCondition> conditions) {
        Expression result = null;
        for (TableTenantCondition tc : conditions) {
            Expression cond = createTenantConditionExpression(tc.alias != null ? tc.alias : tc.tableName);
            result = (result == null) ? cond : new AndExpression(result, cond);
        }
        return result;
    }

    private static void addTenantConditionToJoinOn(TableTenantCondition condition) {
        if (condition.joinObject == null) return;

        Expression tenantCond = createTenantConditionExpression(
                condition.alias != null ? condition.alias : condition.tableName);

        Collection<Expression> onExpressions = condition.joinObject.getOnExpressions();
        if (onExpressions != null && !onExpressions.isEmpty()) {
            String tenantColumnRef = (condition.alias != null ? condition.alias : condition.tableName)
                    + "." + tenantColumn;
            for (Expression expr : onExpressions) {
                if (expr.toString().contains(tenantColumnRef)) {
                    return; // 已存在，跳过
                }
            }
            Expression combined = null;
            for (Expression expr : onExpressions) {
                combined = (combined == null) ? expr : new AndExpression(combined, expr);
            }
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(new AndExpression(combined, tenantCond));
            condition.joinObject.setOnExpressions(newExpressions);
        } else {
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(tenantCond);
            condition.joinObject.setOnExpressions(newExpressions);
        }
    }

    /**
     * 创建租户条件表达式：tableRef.tenant_id = :myjpaTenantId
     */
    private static Expression createTenantConditionExpression(String tableRef) {
        Column column = new Column();
        if (tableRef != null && !tableRef.trim().isEmpty()) {
            column.setTable(new Table(tableRef));
        }
        column.setColumnName(tenantColumn);

        JdbcNamedParameter param = new JdbcNamedParameter();
        param.setName(TENANT_PARAM_NAME);

        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(param);
        return equalsTo;
    }

    /**
     * 幂等检查：WHERE 表达式中是否已包含租户条件
     */
    private static boolean isTenantConditionExists(Expression whereExpression, String column,
                                                    String tableAlias, String tableName) {
        return isDeleteConditionExists(whereExpression, column, tableAlias, tableName);
    }

    /**
     * 递归处理子查询中的租户条件
     */
    private static void processTenantSubQueries(PlainSelect plainSelect) {
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem item : plainSelect.getSelectItems()) {
                if (item.getExpression() instanceof ParenthesedSelect) {
                    processTenantSelect(((ParenthesedSelect) item.getExpression()).getSelect());
                }
            }
        }
        if (plainSelect.getWhere() != null) {
            processTenantExpressionSubQueries(plainSelect.getWhere());
        }
    }

    private static void processTenantExpressionSubQueries(Expression expression) {
        if (expression instanceof ParenthesedSelect ps) {
            processTenantSelect(ps.getSelect());
        } else if (expression instanceof NotExpression notExpr) {
            processTenantExpressionSubQueries(notExpr.getExpression());
        } else if (expression instanceof InExpression inExpr) {
            if (inExpr.getRightExpression() != null) {
                processTenantExpressionSubQueries(inExpr.getRightExpression());
            }
        } else if (expression instanceof ExistsExpression existsExpr) {
            processTenantExpressionSubQueries(existsExpr.getRightExpression());
        } else if (expression instanceof BinaryExpression binExpr) {
            processTenantExpressionSubQueries(binExpr.getLeftExpression());
            processTenantExpressionSubQueries(binExpr.getRightExpression());
        }
    }

    /**
     * 租户条件信息
     */
    private static class TableTenantCondition {
        final String tableName;
        final String alias;
        final JoinType joinType;
        final Join joinObject;

        TableTenantCondition(String tableName, String alias, JoinType joinType, Join joinObject) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
            this.joinObject = joinObject;
        }
    }

    // ===================== 合并处理（单次解析同时注入逻辑删除 + 租户条件）=====================

    /**
     * 一次 SQL 解析，同时注入逻辑删除条件和租户隔离条件。
     *
     * <p>仅在租户功能开启且 tenantId 非 null 时由 BaseDaoImpl 调用，
     * 调用前 TenantContext.isSkipped() 已检查，此处无需重复判断。
     *
     * @param sql 原始 SQL
     * @return 注入两类条件后的 SQL
     */
    public static String appendConditions(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                return sql;
            }
            Select selectStatement = (Select) statement;
            processUnifiedSelect(selectStatement);
            return selectStatement.toString();
        } catch (JSQLParserException e) {
            log.warn("解析SQL时发生异常（合并条件），返回原始SQL: {}, 异常: {}", sql, e.getMessage());
            return sql;
        } catch (Exception e) {
            log.error("处理SQL时发生未知异常（合并条件），返回原始SQL: {}", sql, e);
            return sql;
        }
    }

    private static void processUnifiedSelect(Select select) {
        if (select instanceof PlainSelect) {
            processUnifiedPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
            for (Select s : ((SetOperationList) select).getSelects()) {
                processUnifiedSelect(s);
            }
        }
    }

    private static void processUnifiedPlainSelect(PlainSelect plainSelect) {
        List<UnifiedTableConditions> whereConditions = new ArrayList<>();
        List<UnifiedTableConditions> joinConditions = new ArrayList<>();

        Expression existingWhere = plainSelect.getWhere();

        // 主表
        if (plainSelect.getFromItem() instanceof Table) {
            collectUnifiedConditions((Table) plainSelect.getFromItem(), existingWhere,
                    JoinType.FROM_TABLE, null, whereConditions, joinConditions);
        } else if (plainSelect.getFromItem() instanceof ParenthesedSelect) {
            processUnifiedSelect(((ParenthesedSelect) plainSelect.getFromItem()).getSelect());
        }

        // JOIN 表
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getRightItem() instanceof Table) {
                    collectUnifiedConditions((Table) join.getRightItem(), existingWhere,
                            getJoinType(join), join, whereConditions, joinConditions);
                } else if (join.getRightItem() instanceof ParenthesedSelect) {
                    processUnifiedSelect(((ParenthesedSelect) join.getRightItem()).getSelect());
                }
            }
        }

        // 注入 WHERE 条件
        if (!whereConditions.isEmpty()) {
            Expression allConditions = buildUnifiedWhereExpression(whereConditions);
            Expression currentWhere = plainSelect.getWhere();
            plainSelect.setWhere(currentWhere != null
                    ? new AndExpression(currentWhere, allConditions)
                    : allConditions);
        }

        // 注入 JOIN ON 条件
        for (UnifiedTableConditions utc : joinConditions) {
            applyUnifiedConditionsToJoinOn(utc);
        }

        // 递归处理子查询
        processUnifiedSubQueries(plainSelect);
    }

    /**
     * 收集单个表所有需要注入的条件（逻辑删除 + 租户），并按 JOIN 类型分组
     */
    private static void collectUnifiedConditions(Table table, Expression existingWhere,
                                                  JoinType joinType, Join joinObject,
                                                  List<UnifiedTableConditions> whereConditions,
                                                  List<UnifiedTableConditions> joinConditions) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        String tableRef = alias != null ? alias : tableName;

        List<Expression> exprs = new ArrayList<>();

        // 1. 逻辑删除条件
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid()
                && !isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
            Column col = new Column();
            col.setTable(new Table(tableRef));
            col.setColumnName(deleteInfo.getDelColumn());
            EqualsTo eq = new EqualsTo();
            eq.setLeftExpression(col);
            eq.setRightExpression(new LongValue(deleteInfo.getUnDelValue()));
            exprs.add(eq);
        }

        // 2. 租户条件
        if (tenantEnabled && TableCacheManager.hasTenantColumn(tableName)
                && !isDeleteConditionExists(existingWhere, tenantColumn, alias, tableName)) {
            exprs.add(createTenantConditionExpression(tableRef));
        }

        if (exprs.isEmpty()) return;

        UnifiedTableConditions utc = new UnifiedTableConditions(tableName, alias, joinType, joinObject, exprs);
        if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
            joinConditions.add(utc);
        } else {
            whereConditions.add(utc);
        }
        log.debug("表 {}({}) 收集到 {} 个条件，将添加到 {}", tableName, joinType, exprs.size(),
                (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
    }

    private static Expression buildUnifiedWhereExpression(List<UnifiedTableConditions> conditions) {
        Expression result = null;
        for (UnifiedTableConditions utc : conditions) {
            for (Expression expr : utc.expressions) {
                result = (result == null) ? expr : new AndExpression(result, expr);
            }
        }
        return result;
    }

    private static void applyUnifiedConditionsToJoinOn(UnifiedTableConditions utc) {
        if (utc.joinObject == null) return;

        Expression newConditions = null;
        for (Expression expr : utc.expressions) {
            newConditions = (newConditions == null) ? expr : new AndExpression(newConditions, expr);
        }

        Collection<Expression> onExpressions = utc.joinObject.getOnExpressions();
        if (onExpressions != null && !onExpressions.isEmpty()) {
            Expression combined = null;
            for (Expression expr : onExpressions) {
                combined = (combined == null) ? expr : new AndExpression(combined, expr);
            }
            List<Expression> newExprs = new ArrayList<>();
            newExprs.add(new AndExpression(combined, newConditions));
            utc.joinObject.setOnExpressions(newExprs);
        } else {
            List<Expression> newExprs = new ArrayList<>();
            newExprs.add(newConditions);
            utc.joinObject.setOnExpressions(newExprs);
        }
    }

    private static void processUnifiedSubQueries(PlainSelect plainSelect) {
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem item : plainSelect.getSelectItems()) {
                if (item.getExpression() instanceof ParenthesedSelect) {
                    processUnifiedSelect(((ParenthesedSelect) item.getExpression()).getSelect());
                }
            }
        }
        if (plainSelect.getWhere() != null) {
            processUnifiedExpressionSubQueries(plainSelect.getWhere());
        }
    }

    private static void processUnifiedExpressionSubQueries(Expression expression) {
        if (expression instanceof ParenthesedSelect ps) {
            processUnifiedSelect(ps.getSelect());
        } else if (expression instanceof NotExpression notExpr) {
            processUnifiedExpressionSubQueries(notExpr.getExpression());
        } else if (expression instanceof InExpression inExpr) {
            if (inExpr.getRightExpression() != null) {
                processUnifiedExpressionSubQueries(inExpr.getRightExpression());
            }
        } else if (expression instanceof ExistsExpression existsExpr) {
            processUnifiedExpressionSubQueries(existsExpr.getRightExpression());
        } else if (expression instanceof BinaryExpression binExpr) {
            processUnifiedExpressionSubQueries(binExpr.getLeftExpression());
            processUnifiedExpressionSubQueries(binExpr.getRightExpression());
        }
    }

    /**
     * 单个表收集到的所有条件（逻辑删除 + 租户），及其 JOIN 元数据
     */
    private static class UnifiedTableConditions {
        final String tableName;
        final String alias;
        final JoinType joinType;
        final Join joinObject;
        final List<Expression> expressions;

        UnifiedTableConditions(String tableName, String alias, JoinType joinType,
                               Join joinObject, List<Expression> expressions) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
            this.joinObject = joinObject;
            this.expressions = expressions;
        }
    }

    // ===================== 逻辑删除条件处理 =====================

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
        // 收集需要处理的表信息，按JOIN类型分类
        List<TableDeleteCondition> whereConditions = new ArrayList<>(); // 放入WHERE的条件
        List<TableDeleteCondition> joinConditions = new ArrayList<>();  // 放入JOIN ON的条件
        
        // 获取现有的WHERE条件，用于检查是否已包含删除条件
        Expression existingWhere = plainSelect.getWhere();
        
        // 处理FROM子句中的表（主表）
        if (plainSelect.getFromItem() instanceof Table) {
            Table table = (Table) plainSelect.getFromItem();
            addTableConditionByType(table, existingWhere, JoinType.FROM_TABLE, null, whereConditions, joinConditions);
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
                    JoinType joinType = getJoinType(join);
                    addTableConditionByType(table, existingWhere, joinType, join, whereConditions, joinConditions);
                } else if (join.getRightItem() instanceof ParenthesedSelect) {
                    // 递归处理JOIN中的子查询
                    ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                    processSelect(parenthesedSelect.getSelect());
                }
            }
        }
        
        // 处理WHERE条件（主表和INNER JOIN）
        if (!whereConditions.isEmpty()) {
            Expression whereDeleteConditions = buildDeleteConditionsExpression(whereConditions);
            Expression currentWhere = plainSelect.getWhere();
            if (currentWhere != null) {
                plainSelect.setWhere(new AndExpression(currentWhere, whereDeleteConditions));
            } else {
                plainSelect.setWhere(whereDeleteConditions);
            }
        }
        
        // 处理JOIN ON条件（LEFT/RIGHT JOIN）
        if (!joinConditions.isEmpty()) {
            for (TableDeleteCondition condition : joinConditions) {
                addDeleteConditionToJoinOn(condition);
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
        if (expression instanceof ParenthesedSelect ps) {
            processSelect(ps.getSelect());
        } else if (expression instanceof NotExpression notExpr) {
            // NOT EXISTS / NOT IN：剥开 NOT 层继续递归
            processExpressionSubQueries(notExpr.getExpression());
        } else if (expression instanceof InExpression inExpr) {
            // WHERE id IN (SELECT ...) / WHERE id NOT IN (SELECT ...)
            if (inExpr.getRightExpression() != null) {
                processExpressionSubQueries(inExpr.getRightExpression());
            }
        } else if (expression instanceof ExistsExpression existsExpr) {
            // WHERE EXISTS (SELECT ...)
            processExpressionSubQueries(existsExpr.getRightExpression());
        } else if (expression instanceof BinaryExpression binExpr) {
            processExpressionSubQueries(binExpr.getLeftExpression());
            processExpressionSubQueries(binExpr.getRightExpression());
        }
    }
    
    
    /**
     * 根据JOIN类型添加表的删除条件信息
     */
    private static void addTableConditionByType(Table table, Expression existingWhere, JoinType joinType, Join joinObject,
                                               List<TableDeleteCondition> whereConditions, List<TableDeleteCondition> joinConditions) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid()) {
            // 检查现有WHERE条件中是否已包含删除字段条件
            if (!isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
                TableDeleteCondition condition = new TableDeleteCondition(tableName, alias, deleteInfo, joinType, joinObject);
                
                // 根据JOIN类型决定条件放置位置
                if (joinType == JoinType.FROM_TABLE || joinType == JoinType.INNER_JOIN) {
                    // 主表和INNER JOIN的条件放入WHERE
                    whereConditions.add(condition);
                } else if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
                    // LEFT/RIGHT JOIN的条件放入JOIN ON
                    joinConditions.add(condition);
                } else {
                    // FULL JOIN 也放入WHERE（较少使用）
                    whereConditions.add(condition);
                }
                
                log.debug("表{}({})的删除条件将添加到{}", tableName, joinType, 
                    (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
            } else {
                log.debug("表{}的删除条件已存在，跳过自动拼接", tableName);
            }
        }
    }
    
    /**
     * 获取JOIN类型
     */
    private static JoinType getJoinType(Join join) {
        if (join.isLeft()) {
            return JoinType.LEFT_JOIN;
        } else if (join.isRight()) {
            return JoinType.RIGHT_JOIN;
        } else if (join.isFull()) {
            return JoinType.FULL_JOIN;
        } else {
            // 默认为INNER JOIN（包括JOIN、INNER JOIN等）
            return JoinType.INNER_JOIN;
        }
    }
    
    /**
     * 将删除条件添加到JOIN ON子句中
     * 使用 setOnExpressions 避免 JSqlParser 废弃的 setOnExpression 方法导致的重复 ON 子句问题
     */
    private static void addDeleteConditionToJoinOn(TableDeleteCondition condition) {
        if (condition.joinObject == null) {
            log.warn("JOIN对象为空，无法添加删除条件到ON子句");
            return;
        }

        Expression deleteCondition = createDeleteConditionExpression(condition.deleteInfo, condition.alias);

        log.debug("要添加的删除条件: {}", deleteCondition);

        // 获取现有的ON条件
        Collection<Expression> onExpressions = condition.joinObject.getOnExpressions();

        if (onExpressions != null && !onExpressions.isEmpty()) {
            // 检查删除条件是否已经存在
            String deleteColumnRef = (condition.alias != null ? condition.alias : condition.tableName) + "." + condition.deleteInfo.getDelColumn();
            for (Expression expr : onExpressions) {
                if (expr.toString().contains(deleteColumnRef)) {
                    log.debug("表{}的删除条件已存在于ON子句中，跳过添加", condition.tableName);
                    return;
                }
            }

            // 组合所有现有条件
            Expression combined = null;
            for (Expression expr : onExpressions) {
                if (combined == null) {
                    combined = expr;
                } else {
                    combined = new AndExpression(combined, expr);
                }
            }

            // 添加删除条件
            combined = new AndExpression(combined, deleteCondition);

            // 使用正确的 setOnExpressions 方法
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(combined);
            condition.joinObject.setOnExpressions(newExpressions);

            log.debug("组合后的ON条件: {}", combined);
        } else {
            // 没有现有条件，直接设置删除条件
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(deleteCondition);
            condition.joinObject.setOnExpressions(newExpressions);
        }

        log.debug("已将表{}的删除条件添加到JOIN ON子句", condition.tableName);
    }
    
    /**
     * 添加表的删除条件信息（如果尚未存在相关条件）- 保留用于兼容性
     */
    @Deprecated
    private static void addTableCondition(Map<String, TableDeleteCondition> tableConditions, Table table, Expression existingWhere) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid()) {
            // 检查现有WHERE条件中是否已包含删除字段条件
            if (!isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
                String key = alias != null ? alias : tableName;
                tableConditions.put(key, new TableDeleteCondition(tableName, alias, deleteInfo, JoinType.FROM_TABLE));
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
     * JOIN类型枚举
     */
    private enum JoinType {
        FROM_TABLE,    // 主表
        INNER_JOIN,    // 内连接
        LEFT_JOIN,     // 左连接
        RIGHT_JOIN,    // 右连接
        FULL_JOIN      // 全连接
    }
    
    /**
     * 表删除条件信息
     */
    private static class TableDeleteCondition {
        final String tableName;
        final String alias;
        final TableCacheManager.DeleteInfo deleteInfo;
        final JoinType joinType;
        final Join joinObject; // 用于修改JOIN的ON条件
        
        TableDeleteCondition(String tableName, String alias, TableCacheManager.DeleteInfo deleteInfo, JoinType joinType) {
            this(tableName, alias, deleteInfo, joinType, null);
        }
        
        TableDeleteCondition(String tableName, String alias, TableCacheManager.DeleteInfo deleteInfo, JoinType joinType, Join joinObject) {
            this.tableName = tableName;
            this.alias = alias;
            this.deleteInfo = deleteInfo;
            this.joinType = joinType;
            this.joinObject = joinObject;
        }
    }
}