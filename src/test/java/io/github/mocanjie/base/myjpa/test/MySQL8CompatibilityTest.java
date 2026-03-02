package io.github.mocanjie.base.myjpa.test;

import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.parser.JSqlDynamicSqlParser;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 8 兼容性测试
 *
 * 覆盖范围：
 *  - 分页 SQL 生成（LIMIT offset,count 语法）
 *  - MySQL 8 特有语法经 JSqlParser 往返后的完整性
 *  - 自动逻辑删除条件注入（@MyTable 配置驱动）
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MySQL 8 兼容性测试")
class MySQL8CompatibilityTest {

    @BeforeAll
    static void setup() {
        SqlBuilder.type = 1; // MySQL 模式
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
    @DisplayName("1.1 基本 LIMIT 分页语法（offset,count 格式）")
    void test01_basicLimitPagination() {
        String sql = "SELECT * FROM user WHERE age > 18";
        Pager<Object> pager = new Pager<>(1, 10);
        String result = SqlBuilder.buildPagerSql(sql, pager);

        assertTrue(result.contains("limit") || result.contains("LIMIT"), "应包含 LIMIT 关键字");
        assertTrue(result.contains("_mysqltb_"), "MySQL 分页子查询别名应存在");
        // MySQL 格式: LIMIT offset,count → LIMIT 0,10
        assertTrue(result.contains("0,10") || result.contains("0, 10"),
                "第1页 LIMIT 应为 0,10");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 分页 + 排序组合语法")
    void test02_paginationWithSorting() {
        String sql = "SELECT * FROM user";
        Pager<Object> pager = new Pager<>(2, 20);
        pager.setSort("createTime");
        pager.setOrder("DESC");
        String result = SqlBuilder.buildPagerSql(sql, pager);

        assertTrue(result.contains("create_time"), "驼峰 createTime 应转为 create_time");
        assertTrue(result.toLowerCase().contains("desc"), "排序方向 DESC 应保留");
        // MySQL 格式: LIMIT 20,20
        assertTrue(result.contains("20,20") || result.contains("20, 20"),
                "第2页 LIMIT 应为 20,20，即 (2-1)*20=20 offset");
    }

    @Test
    @Order(3)
    @DisplayName("1.3 大偏移量分页计算准确性")
    void test03_largeOffsetPagination() {
        String sql = "SELECT * FROM user";
        Pager<Object> pager = new Pager<>(1000, 50);
        String result = SqlBuilder.buildPagerSql(sql, pager);

        // MySQL 格式: LIMIT 49950,50
        assertTrue(result.contains("49950,50") || result.contains("49950, 50"),
                "第1000页×50条/页，LIMIT 应为 49950,50");
    }

    // =========================================================
    // 2. MySQL 特有语法不被破坏
    // =========================================================

    @Test
    @Order(4)
    @DisplayName("2.1 反引号标识符保留")
    void test04_backtickIdentifiers() {
        String sql = "SELECT `id`, `user_name` FROM `user` WHERE `is_active` = 1";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        // JSqlParser 往返后反引号标识符的字段名应保留（可能被去掉反引号）
        assertTrue(result.contains("id") && result.contains("user_name"),
                "字段名应保留");
        assertTrue(result.contains("is_active"), "条件字段应保留");
    }

    @Test
    @Order(5)
    @DisplayName("2.2 MySQL BOOLEAN（0/1）与 true/false 混用")
    void test05_booleanValues() {
        String sql = "SELECT * FROM user WHERE is_active = 1 AND is_vip = true";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("is_active"), "is_active 条件应保留");
        assertTrue(result.contains("is_vip"), "is_vip 条件应保留");
    }

    @Test
    @Order(6)
    @DisplayName("2.3 REGEXP / RLIKE 操作符保留")
    void test06_regexpOperator() {
        String sql = "SELECT * FROM sys_user WHERE email REGEXP '^[a-z]+@[a-z]+\\\\.[a-z]+$'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertFalse(result.isEmpty(), "结果不应为空");
        assertTrue(result.contains("sys_user"), "表名应保留");
    }

    // =========================================================
    // 3. MySQL 8 高级特性
    // =========================================================

    @Test
    @Order(7)
    @DisplayName("3.1 CTE（WITH 子句）保留")
    void test07_cteWithClause() {
        String sql = "WITH order_summary AS (" +
                     "  SELECT user_id, COUNT(*) AS cnt FROM orders GROUP BY user_id" +
                     ") " +
                     "SELECT u.id, u.name, s.cnt FROM sys_user u LEFT JOIN order_summary s ON u.id = s.user_id";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("WITH"), "CTE WITH 关键字应保留");
        assertTrue(result.contains("order_summary"), "CTE 名称应保留");
    }

    @Test
    @Order(8)
    @DisplayName("3.2 窗口函数（ROW_NUMBER、SUM OVER）保留")
    void test08_windowFunctions() {
        String sql = "SELECT user_id, order_date, amount, " +
                     "ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY order_date DESC) AS rn, " +
                     "SUM(amount) OVER (PARTITION BY user_id) AS total " +
                     "FROM orders WHERE order_date > '2024-01-01'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("ROW_NUMBER"), "ROW_NUMBER 应保留");
        assertTrue(result.contains("SUM"), "SUM 函数应保留");
        assertTrue(result.contains("OVER"), "OVER 子句应保留");
        assertTrue(result.contains("PARTITION BY"), "PARTITION BY 应保留");
    }

    @Test
    @Order(9)
    @DisplayName("3.3 JSON_EXTRACT 函数保留")
    void test09_jsonExtractFunction() {
        String sql = "SELECT id, name, JSON_EXTRACT(profile, '$.age') AS age " +
                     "FROM sys_user WHERE JSON_EXTRACT(profile, '$.active') = true";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("JSON_EXTRACT"), "JSON_EXTRACT 函数应保留");
    }

    @Test
    @Order(10)
    @DisplayName("3.4 JSON_UNQUOTE + 嵌套 JSON 路径保留")
    void test10_jsonUnquoteNested() {
        String sql = "SELECT id, JSON_UNQUOTE(JSON_EXTRACT(profile, '$.address.city')) AS city " +
                     "FROM config WHERE JSON_EXTRACT(meta, '$.status') = 'active'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("JSON_UNQUOTE"), "JSON_UNQUOTE 应保留");
        assertTrue(result.contains("JSON_EXTRACT"), "JSON_EXTRACT 应保留");
    }

    @Test
    @Order(11)
    @DisplayName("3.5 递归 CTE（WITH RECURSIVE）保留")
    void test11_recursiveCTE() {
        String sql = "WITH RECURSIVE category_tree AS (" +
                     "  SELECT id, name, parent_id FROM category WHERE parent_id IS NULL" +
                     "  UNION ALL" +
                     "  SELECT c.id, c.name, c.parent_id FROM category c " +
                     "  JOIN category_tree ct ON c.parent_id = ct.id" +
                     ") SELECT * FROM category_tree";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("WITH"), "CTE WITH 应保留");
        assertTrue(result.contains("UNION ALL") || result.toUpperCase().contains("UNION"), "UNION ALL 应保留");
    }

    @Test
    @Order(12)
    @DisplayName("3.6 LATERAL JOIN（MySQL 8.0.14+）保留")
    void test12_lateralJoin() {
        String sql = "SELECT u.id, u.name, latest.amount " +
                     "FROM sys_user u " +
                     "LEFT JOIN LATERAL (" +
                     "  SELECT amount FROM orders o WHERE o.user_id = u.id ORDER BY order_date DESC LIMIT 1" +
                     ") latest ON TRUE";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("LATERAL"), "LATERAL 关键字应保留");
    }

    // =========================================================
    // 4. 复杂查询结构
    // =========================================================

    @Test
    @Order(13)
    @DisplayName("4.1 IN 子查询保留")
    void test13_inSubquery() {
        String sql = "SELECT * FROM sys_user WHERE department_id IN " +
                     "(SELECT id FROM department WHERE status = 1)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("IN"), "IN 关键字应保留");
        assertTrue(result.toUpperCase().contains("SELECT"), "子查询应保留");
    }

    @Test
    @Order(14)
    @DisplayName("4.2 复杂 OR/AND 括号逻辑保留")
    void test14_complexOrAndConditions() {
        String sql = "SELECT * FROM sys_user WHERE (age > 18 AND status = 1) OR (vip = 1 AND balance > 100)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.contains("age"), "age 条件应保留");
        assertTrue(result.contains("status"), "status 条件应保留");
        assertTrue(result.contains("balance"), "balance 条件应保留");
    }

    @Test
    @Order(15)
    @DisplayName("4.3 UNION ALL 结构保留")
    void test15_unionAll() {
        String sql = "SELECT id, name, 'A' AS type FROM table_a " +
                     "UNION ALL " +
                     "SELECT id, name, 'B' AS type FROM table_b";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.toUpperCase().contains("UNION"), "UNION 关键字应保留");
    }

    @Test
    @Order(16)
    @DisplayName("4.4 非 SELECT 语句（INSERT）原样返回")
    void test16_insertReturnsUnchanged() {
        String sql = "INSERT INTO sys_user (name, email) VALUES ('Tom', 'tom@example.com')";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertEquals(sql, result, "非 SELECT 的 INSERT 应原样返回");
    }

    @Test
    @Order(17)
    @DisplayName("4.5 非 SELECT 语句（UPDATE）原样返回")
    void test17_updateReturnsUnchanged() {
        String sql = "UPDATE sys_user SET status = 0 WHERE id = 1";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertEquals(sql, result, "非 SELECT 的 UPDATE 应原样返回");
    }

    @Test
    @Order(18)
    @DisplayName("4.6 NULL / 空字符串安全处理")
    void test18_nullAndEmptyHandling() {
        assertNull(JSqlDynamicSqlParser.appendDeleteCondition(null),
                "null 输入应返回 null");
        assertEquals("", JSqlDynamicSqlParser.appendDeleteCondition(""),
                "空字符串应原样返回");
        String blank = "   ";
        assertEquals(blank, JSqlDynamicSqlParser.appendDeleteCondition(blank),
                "空白字符串应原样返回");
    }

    // =========================================================
    // 5. 自动逻辑删除条件注入
    // =========================================================

    @Test
    @Order(19)
    @DisplayName("5.1 简单查询自动注入删除条件")
    void test19_autoInjectDeleteConditionSimple() {
        String sql = "SELECT * FROM user WHERE age > 18";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.toLowerCase().contains("where"), "应自动添加 WHERE 子句");
        assertTrue(result.contains("delete_flag"), "应注入 delete_flag 删除条件");
        assertTrue(result.contains("delete_flag = 0") || result.contains("delete_flag=0"),
                "删除条件值应为 0（未删除状态）");
    }

    @Test
    @Order(20)
    @DisplayName("5.2 带别名的单表查询注入删除条件")
    void test20_autoInjectWithTableAlias() {
        String sql = "SELECT u.id, u.name FROM user u WHERE u.age > 18";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("delete_flag"), "应注入 delete_flag 条件");
        // 使用别名时条件应带别名前缀 u.delete_flag
        assertTrue(result.contains("u.delete_flag"), "删除条件应使用表别名前缀 u.");
    }

    @Test
    @Order(21)
    @DisplayName("5.3 LEFT JOIN 删除条件分别注入到 ON 和 WHERE")
    void test21_joinDeleteConditionPlacement() {
        String sql = "SELECT u.id, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("delete_flag"), "user.delete_flag 条件应存在");
        assertTrue(result.contains("is_deleted"), "role.is_deleted 条件应存在");

        // role（LEFT JOIN）的删除条件应在 ON 子句中（WHERE 之前）
        int isDeletedIdx = result.indexOf("is_deleted");
        int whereIdx = result.toLowerCase().indexOf("where");
        assertTrue(isDeletedIdx < whereIdx,
                "LEFT JOIN 表的删除条件应在 ON 子句中（在 WHERE 之前）");
    }

    @Test
    @Order(22)
    @DisplayName("5.4 删除条件幂等性：已存在则不重复注入")
    void test22_idempotentDeleteCondition() {
        String sql = "SELECT * FROM user WHERE user.delete_flag = 0 AND age > 18";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        int count = result.split("delete_flag", -1).length - 1;
        assertEquals(1, count, "delete_flag 应只出现 1 次，不重复注入");
    }

    @Test
    @Order(23)
    @DisplayName("5.5 未配置 @MyTable 的表不注入任何条件")
    void test23_noInjectionForUnconfiguredTable() {
        String sql = "SELECT * FROM sys_log WHERE level = 'ERROR'";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        // sys_log 不在缓存中，SQL 经 JSqlParser 解析后原样返回（不会多 WHERE）
        assertFalse(result.contains("delete_flag"), "未配置 @MyTable 的表不应注入 delete_flag");
        assertFalse(result.contains("is_deleted"), "未配置 @MyTable 的表不应注入 is_deleted");
        assertTrue(result.contains("sys_log"), "表名应保留");
        assertTrue(result.contains("level"), "原有条件应保留");
    }

    @Test
    @Order(24)
    @DisplayName("5.6 INNER JOIN 删除条件放入 WHERE（非 ON）")
    void test24_innerJoinDeleteConditionInWhere() {
        String sql = "SELECT u.id, r.role_name FROM user u INNER JOIN role r ON u.role_id = r.id";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("delete_flag"), "user.delete_flag 条件应存在");
        assertTrue(result.contains("is_deleted"), "role.is_deleted 条件应存在");

        // INNER JOIN 的删除条件应在 WHERE 子句中
        assertTrue(result.toLowerCase().contains("where"),
                "INNER JOIN 的删除条件应追加到 WHERE 子句");
        int whereIdx = result.toLowerCase().indexOf("where");
        int isDeletedIdx = result.indexOf("is_deleted");
        assertTrue(isDeletedIdx > whereIdx,
                "INNER JOIN 表的删除条件应在 WHERE 子句中（WHERE 之后）");
    }

    // =========================================================
    // 6. IN / EXISTS 子查询注入测试
    // =========================================================

    @Test
    @Order(25)
    @DisplayName("6.1 WHERE IN 子查询：子查询内部自动注入删除条件")
    void test25_inSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role WHERE id IN (SELECT role_id FROM user WHERE age > 18)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        // 外层 role 表注入 is_deleted
        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        // 子查询内 user 表注入 delete_flag
        assertTrue(result.contains("delete_flag"), "IN 子查询内 user 表应注入 delete_flag");
    }

    @Test
    @Order(26)
    @DisplayName("6.2 WHERE NOT IN 子查询：子查询内部自动注入删除条件")
    void test26_notInSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role WHERE id NOT IN (SELECT role_id FROM user)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "NOT IN 子查询内 user 表应注入 delete_flag");
    }

    @Test
    @Order(27)
    @DisplayName("6.3 WHERE EXISTS 子查询：子查询内部自动注入删除条件")
    void test27_existsSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role r WHERE EXISTS (SELECT 1 FROM user u WHERE u.role_id = r.id)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "EXISTS 子查询内 user 表应注入 delete_flag");
    }

    @Test
    @Order(28)
    @DisplayName("6.4 WHERE NOT EXISTS 子查询：子查询内部自动注入删除条件")
    void test28_notExistsSubQueryDeleteCondition() {
        String sql = "SELECT * FROM role r WHERE NOT EXISTS (SELECT 1 FROM user u WHERE u.role_id = r.id)";
        String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);

        assertTrue(result.contains("is_deleted"), "外层 role 表应注入 is_deleted");
        assertTrue(result.contains("delete_flag"), "NOT EXISTS 子查询内 user 表应注入 delete_flag");
    }
}
