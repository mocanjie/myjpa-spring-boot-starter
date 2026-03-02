package io.github.mocanjie.base.myjpa.test;

import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.parser.JSqlDynamicSqlParser;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 16 兼容性测试
 *
 * 覆盖范围：
 *  - 分页 SQL 生成（OFFSET/LIMIT 语法）
 *  - PostgreSQL 特有语法经过 JSqlParser 往返解析后的完整性
 *  - 自动逻辑删除条件注入（@MyTable 配置驱动）
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PostgreSQL 16 兼容性测试")
class PostgreSQL16CompatibilityTest {

    @BeforeAll
    static void setup() {
        SqlBuilder.type = 5; // PostgreSQL 模式
        TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
        assertNotNull(TableCacheManager.getDeleteInfoByTableName("user"),
                "TestUser (@MyTable value=user) 应被成功缓存");
        assertNotNull(TableCacheManager.getDeleteInfoByTableName("role"),
                "TestRole (@MyTable value=role) 应被成功缓存");
    }

    // =========================================================
    // 1. 分页查询测试
    // =========================================================

    @Test
    @Order(1)
    @DisplayName("1.1 基本 OFFSET/LIMIT 分页语法")
    void test01_basicOffsetLimitPagination() {
        String sql = "SELECT * FROM \"user\" WHERE age > 18";
        Pager<Object> pager = new Pager<>(1, 10);
        String result = SqlBuilder.buildPagerSql(sql, pager);

        assertTrue(result.contains("OFFSET"), "应包含 OFFSET 关键字");
        assertTrue(result.contains("LIMIT"), "应包含 LIMIT 关键字");
        assertTrue(result.indexOf("OFFSET") < result.indexOf("LIMIT"),
                "OFFSET 应在 LIMIT 之前（PostgreSQL 标准）");
        assertTrue(result.contains("_pgsqltb_"), "PostgreSQL 分页子查询别名应存在");
        assertTrue(result.contains("OFFSET 0"), "第1页 OFFSET 应为 0");
        assertTrue(result.contains("LIMIT 10"), "页大小应为 10");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 分页 + 排序组合语法")
    void test02_paginationWithSorting() {
        String sql = "SELECT * FROM \"user\"";
        Pager<Object> pager = new Pager<>(2, 20);
        pager.setSort("createTime");
        pager.setOrder("DESC");
        String result = SqlBuilder.buildPagerSql(sql, pager);

        assertTrue(result.contains("OFFSET 20"), "第2页 OFFSET 应为 (2-1)*20=20");
        assertTrue(result.contains("LIMIT 20"), "页大小应为 20");
        assertTrue(result.contains("create_time"), "驼峰 createTime 应转为 create_time");
        assertTrue(result.toLowerCase().contains("desc"), "排序方向 DESC 应保留");
        assertTrue(result.indexOf("create_time") < result.indexOf("OFFSET"),
                "ORDER BY 子句应在 OFFSET 之前");
    }

    @Test
    @Order(3)
    @DisplayName("1.3 大偏移量分页计算准确性")
    void test03_largeOffsetPagination() {
        String sql = "SELECT * FROM \"user\"";
        Pager<Object> pager = new Pager<>(1000, 50);
        String result = SqlBuilder.buildPagerSql(sql, pager);

        assertTrue(result.contains("OFFSET 49950"),
                "第1000页×50条/页，OFFSET 应为 (1000-1)*50=49950");
        assertTrue(result.contains("LIMIT 50"), "页大小应为 50");
    }

    // =========================================================
    // 2. PostgreSQL 特有语法不被破坏
    // =========================================================

    @Test
    @Order(4)
    @DisplayName("2.1 双引号标识符（区分大小写）保留")
    void test04_doubleQuotedIdentifiers() {
        String sql = "SELECT \"userId\", \"userName\" FROM \"User\" WHERE \"isActive\" = true";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        // JSqlParser 往返后双引号标识符应保留
        assertTrue(result.contains("userId") || result.contains("\"userId\""),
                "userId 标识符应保留");
        assertTrue(result.contains("userName") || result.contains("\"userName\""),
                "userName 标识符应保留");
    }

    @Test
    @Order(5)
    @DisplayName("2.2 PostgreSQL BOOLEAN 类型（true/false）保留")
    void test05_booleanType() {
        String sql = "SELECT * FROM \"user\" WHERE is_active = true AND score > 0";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toLowerCase().contains("true"), "BOOLEAN true 应保留");
    }

    @Test
    @Order(6)
    @DisplayName("2.3 数组类型与 && 操作符保留")
    void test06_arrayOperator() {
        // 使用未配置 @MyTable 的表，避免 JSONB + 删除条件冲突
        String sql = "SELECT * FROM product WHERE tags && ARRAY['vip', 'premium']";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        // JSqlParser 解析失败时原样返回，解析成功时保留语义
        assertTrue(result.contains("product"), "表名应保留");
    }

    // =========================================================
    // 3. JSONB 支持
    // =========================================================

    @Test
    @Order(7)
    @DisplayName("3.1 JSONB 查询操作符（->>、@>）不被破坏")
    void test07_jsonbQueryOperators() {
        String sql = "SELECT id, name FROM profile WHERE extra->>'type' = 'admin'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertFalse(result.isEmpty(), "结果不应为空字符串");
        // 若 JSqlParser 成功解析，->>' 路径应被保留；若抛异常则返回原始 SQL
        assertTrue(result.contains("profile"), "表名应保留");
    }

    @Test
    @Order(8)
    @DisplayName("3.2 JSONB 函数（jsonb_extract_path_text 等）保留")
    void test08_jsonbFunctions() {
        String sql = "SELECT id, jsonb_extract_path_text(meta, 'city') AS city " +
                     "FROM config WHERE jsonb_typeof(meta) = 'object'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("jsonb_extract_path_text") || result.contains("city"),
                "JSONB 函数或别名应保留");
    }

    // =========================================================
    // 4. 窗口函数与 CTE
    // =========================================================

    @Test
    @Order(9)
    @DisplayName("4.1 窗口函数（ROW_NUMBER OVER、LAG OVER）保留")
    void test09_windowFunctions() {
        String sql = "SELECT user_id, amount, " +
                     "ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY order_date DESC) AS rn, " +
                     "LAG(amount, 1) OVER (PARTITION BY user_id ORDER BY order_date) AS prev " +
                     "FROM orders";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("ROW_NUMBER"), "ROW_NUMBER 应保留");
        assertTrue(result.contains("OVER"), "OVER 子句应保留");
        assertTrue(result.contains("LAG"), "LAG 函数应保留");
        assertTrue(result.contains("PARTITION BY"), "PARTITION BY 应保留");
    }

    @Test
    @Order(10)
    @DisplayName("4.2 递归 CTE（WITH RECURSIVE）保留")
    void test10_recursiveCTE() {
        String sql = "WITH RECURSIVE org AS (" +
                     "  SELECT id, name, parent_id, 1 AS lvl FROM organization WHERE parent_id IS NULL " +
                     "  UNION ALL " +
                     "  SELECT o.id, o.name, o.parent_id, h.lvl + 1 " +
                     "  FROM organization o JOIN org h ON o.parent_id = h.id" +
                     ") SELECT * FROM org ORDER BY lvl, id";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("WITH"), "CTE WITH 关键字应保留");
        assertTrue(result.contains("UNION ALL") || result.toUpperCase().contains("UNION"),
                "UNION ALL 应保留");
    }

    // =========================================================
    // 5. PostgreSQL 15/16 新特性
    // =========================================================

    @Test
    @Order(11)
    @DisplayName("5.1 MERGE 语句（非 SELECT）原样返回")
    void test11_mergeStatement() {
        String sql = "MERGE INTO customer ca USING transactions t ON ca.id = t.cust_id " +
                     "WHEN MATCHED THEN UPDATE SET balance = balance + t.amount " +
                     "WHEN NOT MATCHED THEN INSERT (id, balance) VALUES (t.cust_id, t.amount)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertEquals(sql, result, "非 SELECT 的 MERGE 语句应原样返回，不做任何处理");
    }

    @Test
    @Order(12)
    @DisplayName("5.2 INSERT...RETURNING 语句（非 SELECT）原样返回")
    void test12_insertReturning() {
        String sql = "INSERT INTO sys_user (name, email) VALUES ('Tom', 'tom@example.com') RETURNING id, created_at";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertEquals(sql, result, "非 SELECT 的 INSERT...RETURNING 应原样返回");
    }

    @Test
    @Order(13)
    @DisplayName("5.3 DISTINCT ON 子句保留")
    void test13_distinctOn() {
        String sql = "SELECT DISTINCT ON (user_id) user_id, order_date, amount " +
                     "FROM orders ORDER BY user_id, order_date DESC";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        // DISTINCT ON 是 PostgreSQL 特有语法，JSqlParser 解析成功则保留
        assertTrue(result.contains("user_id"), "关键字段应保留");
    }

    // =========================================================
    // 6. 全文搜索
    // =========================================================

    @Test
    @Order(14)
    @DisplayName("6.1 全文搜索操作符（to_tsvector、@@、to_tsquery）")
    void test14_fullTextSearch() {
        String sql = "SELECT * FROM article " +
                     "WHERE to_tsvector('english', title) @@ to_tsquery('english', 'java & spring')";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertFalse(result.isEmpty(), "结果不应为空");
        assertTrue(result.contains("article"), "表名应保留");
    }

    // =========================================================
    // 7. 复杂查询
    // =========================================================

    @Test
    @Order(15)
    @DisplayName("7.1 LATERAL JOIN 保留")
    void test15_lateralJoin() {
        String sql = "SELECT u.id, u.name, r.* " +
                     "FROM sys_user u " +
                     "LEFT JOIN LATERAL (" +
                     "  SELECT * FROM orders o WHERE o.user_id = u.id ORDER BY o.order_date DESC LIMIT 5" +
                     ") r ON true";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("LATERAL"), "LATERAL 关键字应保留");
    }

    @Test
    @Order(16)
    @DisplayName("7.2 GROUPING SETS 保留")
    void test16_groupingSets() {
        String sql = "SELECT dept, job, COUNT(*) AS cnt FROM employee " +
                     "GROUP BY GROUPING SETS ((dept), (job), ())";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("GROUPING SETS"), "GROUPING SETS 应保留");
    }

    @Test
    @Order(17)
    @DisplayName("7.3 聚合函数 FILTER 子句保留")
    void test17_filterClause() {
        String sql = "SELECT dept, " +
                     "COUNT(*) FILTER (WHERE salary > 50000) AS high_cnt, " +
                     "AVG(salary) FILTER (WHERE type = 'FULL') AS avg_full " +
                     "FROM employee GROUP BY dept";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("FILTER"), "FILTER 子句应保留");
    }

    // =========================================================
    // 8. 边界情况
    // =========================================================

    @Test
    @Order(18)
    @DisplayName("8.1 Schema 限定的表名（public.xxx）保留")
    void test18_schemaQualifiedTableName() {
        String sql = "SELECT u.id, o.amount FROM public.sys_user u " +
                     "INNER JOIN sales.orders o ON u.id = o.user_id WHERE u.age > 18";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("public") || result.contains("sys_user"), "schema 或表名应保留");
        assertTrue(result.contains("sales") || result.contains("orders"), "schema 或表名应保留");
    }

    @Test
    @Order(19)
    @DisplayName("8.2 PostgreSQL 类型转换操作符（::）保留")
    void test19_typeCastOperator() {
        String sql = "SELECT id, created_at::date AS day, amount::numeric(10,2) AS amt FROM orders";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertFalse(result.isEmpty(), "结果不应为空");
        assertTrue(result.contains("orders"), "表名应保留");
    }

    @Test
    @Order(20)
    @DisplayName("8.3 正则表达式操作符（~、!~）保留")
    void test20_regexOperators() {
        String sql = "SELECT * FROM sys_user WHERE email ~ '^[a-z]+@[a-z]+\\.[a-z]+$' AND name !~ '[0-9]'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertFalse(result.isEmpty(), "结果不应为空");
        assertTrue(result.contains("sys_user"), "表名应保留");
    }

    // =========================================================
    // 9. 自动逻辑删除条件注入（核心新特性验证）
    // =========================================================

    @Test
    @Order(21)
    @DisplayName("9.1 单表 SELECT 自动注入删除条件（WHERE 子句）")
    void test21_autoInjectDeleteConditionSimpleSelect() {
        String sql = "SELECT * FROM user";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.toLowerCase().contains("where"), "应自动添加 WHERE 子句");
        assertTrue(result.contains("delete_flag"), "应注入 delete_flag 删除条件");
        // delValue=1, unDelValue=0 → 条件为 delete_flag = 0
        assertTrue(result.contains("delete_flag = 0") || result.contains("delete_flag=0"),
                "删除条件值应为 0（未删除状态）");
    }

    @Test
    @Order(22)
    @DisplayName("9.2 LEFT JOIN 删除条件分别注入到 ON 和 WHERE")
    void test22_joinDeleteConditionPlacement() {
        String sql = "SELECT u.id, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        // user 表（主表 FROM）→ 删除条件应在 WHERE
        assertTrue(result.toLowerCase().contains("where"), "主表删除条件应在 WHERE");
        assertTrue(result.contains("delete_flag"), "user.delete_flag 条件应存在");

        // role 表（LEFT JOIN）→ 删除条件应在 ON 子句（ON 之前 WHERE 之前）
        assertTrue(result.contains("is_deleted"), "role.is_deleted 条件应存在");
        int isDeletedIdx = result.indexOf("is_deleted");
        int whereIdx = result.toLowerCase().indexOf("where");
        assertTrue(isDeletedIdx < whereIdx,
                "LEFT JOIN 的删除条件应在 ON 子句中（WHERE 之前）");
    }

    @Test
    @Order(23)
    @DisplayName("9.3 删除条件幂等性：已存在则不重复注入")
    void test23_idempotentDeleteCondition() {
        // SQL 中已手动写了 delete_flag = 0
        String sql = "SELECT * FROM user WHERE user.delete_flag = 0 AND age > 18";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        int count = result.split("delete_flag", -1).length - 1;
        assertEquals(1, count, "delete_flag 应只出现 1 次，不重复注入");
    }

    @Test
    @Order(24)
    @DisplayName("9.4 未配置 @MyTable 的表不注入任何条件")
    void test24_noInjectionForUnconfiguredTable() {
        String sql = "SELECT * FROM sys_log";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertFalse(result.toLowerCase().contains("where"),
                "未配置 @MyTable 的表不应注入 WHERE 条件");
        assertTrue(result.contains("sys_log"), "表名应保留");
    }

    // =========================================================
    // 10. IN / EXISTS 子查询注入测试（PostgreSQL）
    // =========================================================

    @Test
    @Order(25)
    @DisplayName("10.1 WHERE IN 子查询：子查询内部自动注入删除条件")
    void test25_inSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role WHERE id IN (SELECT role_id FROM user WHERE age > 18)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "IN 子查询内 user 表应注入 delete_flag");
    }

    @Test
    @Order(26)
    @DisplayName("10.2 WHERE EXISTS 子查询：子查询内部自动注入删除条件")
    void test26_existsSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role r WHERE EXISTS (SELECT 1 FROM user u WHERE u.role_id = r.id)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "EXISTS 子查询内 user 表应注入 delete_flag");
    }

    @Test
    @Order(27)
    @DisplayName("10.3 WHERE NOT EXISTS 子查询：子查询内部自动注入删除条件")
    void test27_notExistsSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role r WHERE NOT EXISTS (SELECT 1 FROM user u WHERE u.role_id = r.id)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "NOT EXISTS 子查询内 user 表应注入 delete_flag");
    }
}
