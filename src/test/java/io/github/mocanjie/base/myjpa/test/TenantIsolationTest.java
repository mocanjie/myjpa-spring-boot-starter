package io.github.mocanjie.base.myjpa.test;

import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.parser.JSqlDynamicSqlParser;
import io.github.mocanjie.base.myjpa.tenant.TenantAwareSqlParameterSource;
import io.github.mocanjie.base.myjpa.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多租户隔离功能测试
 *
 * 覆盖范围：
 *  - 全局开关（默认关闭 / 开启后生效）
 *  - 基本租户条件注入（单表 / JOIN）
 *  - 超管（tenantId=null）不注入
 *  - TenantContext.skip() / withoutTenant() 临时跳过
 *  - 幂等性（已有条件不重复注入）
 *  - TenantAwareSqlParameterSource 参数包装
 *  - 无 tenant_id 字段的表不注入
 *  - 删除条件 + 租户条件同时注入
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("多租户隔离功能测试")
class TenantIsolationTest {

    @BeforeAll
    static void setup() {
        // 初始化 @MyTable 缓存（user 有 delete_flag，role 有 is_deleted）
        TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");

        // 模拟启动时数据库扫描结果：user 表有 tenant_id，role 表没有
        TableCacheManager.registerTenantTable("user");
        // role 表不注册 → 不注入租户条件
    }

    @BeforeEach
    void resetTenantState() {
        // 每个测试前重置状态，避免相互干扰
        TenantContext.clear();
        JSqlDynamicSqlParser.tenantEnabled = true;
        JSqlDynamicSqlParser.tenantColumn = "tenant_id";
    }

    @AfterAll
    static void teardown() {
        // 恢复默认值，避免影响其他测试类
        JSqlDynamicSqlParser.tenantEnabled = false;
        TenantContext.clear();
    }

    // =========================================================
    // 1. 全局开关测试
    // =========================================================

    @Test
    @Order(1)
    @DisplayName("1.1 默认关闭时不注入租户条件")
    void test01_globalDisabledSkips() {
        JSqlDynamicSqlParser.tenantEnabled = false;
        String sql = "SELECT * FROM user";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
        assertEquals(sql, result, "全局关闭时 SQL 不应被修改");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 全局开启后注入租户条件")
    void test02_globalEnabledInjects() {
        String result = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
        assertTrue(result.contains(":myjpaTenantId"),
                "全局开启时应注入 :myjpaTenantId 占位符");
        assertTrue(result.toLowerCase().contains("tenant_id"),
                "SQL 应包含 tenant_id 字段");
    }

    // =========================================================
    // 2. 基本租户注入
    // =========================================================

    @Test
    @Order(3)
    @DisplayName("2.1 单表注入 — WHERE 子句")
    void test03_singleTableInjection() {
        String result = JSqlDynamicSqlParser.appendTenantCondition(
                "SELECT id, username FROM user WHERE age > 18");
        assertTrue(result.contains("user.tenant_id = :myjpaTenantId"),
                "应在 WHERE 注入 user.tenant_id = :myjpaTenantId");
        assertTrue(result.contains("age > 18"), "原有 WHERE 条件应保留");
    }

    @Test
    @Order(4)
    @DisplayName("2.2 带别名的表注入")
    void test04_aliasedTableInjection() {
        String result = JSqlDynamicSqlParser.appendTenantCondition(
                "SELECT u.id, u.username FROM user u WHERE u.age > 18");
        assertTrue(result.contains("u.tenant_id = :myjpaTenantId"),
                "使用别名时应用别名限定 tenant_id");
    }

    @Test
    @Order(5)
    @DisplayName("2.3 无 WHERE 的 SQL 自动添加 WHERE")
    void test05_noWhereClause() {
        String result = JSqlDynamicSqlParser.appendTenantCondition(
                "SELECT * FROM user");
        assertTrue(result.toLowerCase().contains("where"),
                "无 WHERE 时应自动添加 WHERE 子句");
        assertTrue(result.contains(":myjpaTenantId"));
    }

    // =========================================================
    // 3. JOIN 查询的租户注入位置
    // =========================================================

    @Test
    @Order(6)
    @DisplayName("3.1 LEFT JOIN — 租户条件追加到 ON 子句")
    void test06_leftJoinTenantInOn() {
        String sql = "SELECT u.id, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
        // user 有 tenant_id → 注入到 WHERE；role 没有 tenant_id → 不注入
        assertTrue(result.contains("u.tenant_id = :myjpaTenantId"),
                "user 表租户条件应在 WHERE 中");
        // role 表未注册 tenant → 不应出现在 ON 或 WHERE
        long tenantCount = result.chars().filter(c -> c == ':').count();
        assertEquals(1, tenantCount, "只有 user 表有 tenant_id，:myjpaTenantId 应只出现一次");
    }

    @Test
    @Order(7)
    @DisplayName("3.2 两个表都有 tenant_id — LEFT JOIN 时 JOIN 表加在 ON，主表加在 WHERE")
    void test07_bothTablesHaveTenant() {
        // 临时注册 role 也有 tenant_id
        TableCacheManager.registerTenantTable("role");
        try {
            String sql = "SELECT u.id, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id";
            String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
            // user（主表） → WHERE
            assertTrue(result.contains("u.tenant_id = :myjpaTenantId"),
                    "user 主表租户条件应在 WHERE 中");
            // role（LEFT JOIN） → ON
            assertTrue(result.contains("r.tenant_id = :myjpaTenantId"),
                    "role LEFT JOIN 表租户条件应追加到 ON 子句");
        } finally {
            // 恢复：清除 role 的租户注册（简单方案：重新清空并重注册 user）
            TableCacheManager.clearCache();
            TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
            TableCacheManager.registerTenantTable("user");
        }
    }

    @Test
    @Order(8)
    @DisplayName("3.3 INNER JOIN — 租户条件追加到 WHERE")
    void test08_innerJoinTenantInWhere() {
        // 临时注册 role
        TableCacheManager.registerTenantTable("role");
        try {
            String sql = "SELECT u.id, r.role_name FROM user u INNER JOIN role r ON u.role_id = r.id";
            String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
            // 两者都应在 WHERE 中
            assertTrue(result.contains("u.tenant_id = :myjpaTenantId"),
                    "user INNER JOIN 租户条件应在 WHERE");
            assertTrue(result.contains("r.tenant_id = :myjpaTenantId"),
                    "role INNER JOIN 租户条件应在 WHERE");
        } finally {
            TableCacheManager.clearCache();
            TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
            TableCacheManager.registerTenantTable("user");
        }
    }

    // =========================================================
    // 4. 超管（tenantId=null）不注入
    // =========================================================

    @Test
    @Order(9)
    @DisplayName("4.1 tenantId=null 时 appendTenantCondition 仍注入占位符（SQL层）")
    void test09_parserAlwaysInjectsWhenEnabled() {
        // Parser 层不知道 tenantId 是否为 null，只要表有 tenant_id 就注入占位符
        // tenantId=null 的过滤在 BaseDaoImpl.applyTenant() 中处理（不调用 appendTenantCondition）
        String result = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
        assertTrue(result.contains(":myjpaTenantId"),
                "Parser 层只管 SQL 改写，占位符应存在");
    }

    @Test
    @Order(10)
    @DisplayName("4.2 TenantAwareSqlParameterSource 正确提供 tenantId 参数")
    void test10_tenantAwareSpsProvidesTenantId() {
        var original = new EmptySqlParameterSource();
        var sps = new TenantAwareSqlParameterSource(original, "myjpaTenantId", 42L);

        assertTrue(sps.hasValue("myjpaTenantId"), "应能找到 myjpaTenantId 参数");
        assertEquals(42L, sps.getValue("myjpaTenantId"), "tenantId 值应为 42");
    }

    @Test
    @Order(11)
    @DisplayName("4.3 TenantAwareSqlParameterSource 委托原始参数")
    void test11_tenantAwareSpsDelegate() {
        var original = new MapSqlParameterSource("userId", 100L);
        var sps = new TenantAwareSqlParameterSource(original, "myjpaTenantId", 5L);

        assertTrue(sps.hasValue("userId"), "原始参数 userId 应可访问");
        assertEquals(100L, sps.getValue("userId"), "userId 值应正确委托");
        assertEquals(5L, sps.getValue("myjpaTenantId"), "tenantId 应为 5");
    }

    // =========================================================
    // 5. TenantContext 跳过租户条件
    // =========================================================

    @Test
    @Order(12)
    @DisplayName("5.1 TenantContext.skip() 跳过注入")
    void test12_skipViaContext() {
        TenantContext.skip();
        try {
            String result = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
            assertFalse(result.contains(":myjpaTenantId"),
                    "skip() 后不应注入租户条件");
            assertEquals("SELECT * FROM user", result, "SQL 应保持不变");
        } finally {
            TenantContext.restore();
        }
    }

    @Test
    @Order(13)
    @DisplayName("5.2 TenantContext.withoutTenant(Lambda) 跳过注入，执行后自动恢复")
    void test13_withoutTenantLambda() {
        String[] resultHolder = new String[1];
        TenantContext.withoutTenant(() -> {
            resultHolder[0] = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
        });

        assertFalse(resultHolder[0].contains(":myjpaTenantId"),
                "withoutTenant 内不应注入");
        // 执行完后状态应自动恢复
        assertFalse(TenantContext.isSkipped(), "withoutTenant 执行后 skip 状态应清除");

        // 恢复后再调用应该正常注入
        String normal = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
        assertTrue(normal.contains(":myjpaTenantId"), "恢复后应正常注入");
    }

    @Test
    @Order(14)
    @DisplayName("5.3 TenantContext.setTenantId() ThreadLocal 方式")
    void test14_threadLocalTenantId() {
        TenantContext.setTenantId(99L);
        try {
            assertEquals(99L, TenantContext.getTenantId(), "ThreadLocal 应返回设置的值");
        } finally {
            TenantContext.clearTenantId();
        }
        assertNull(TenantContext.getTenantId(), "clear 后应为 null");
    }

    // =========================================================
    // 6. 幂等性
    // =========================================================

    @Test
    @Order(15)
    @DisplayName("6.1 已有 tenant_id 条件不重复注入")
    void test15_idempotency() {
        String sqlWithTenant = "SELECT * FROM user WHERE user.tenant_id = :myjpaTenantId";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sqlWithTenant);
        // :myjpaTenantId 应只出现一次
        int count = countOccurrences(result, ":myjpaTenantId");
        assertEquals(1, count, "已有租户条件时，:myjpaTenantId 不应重复注入，当前出现次数=" + count);
    }

    @Test
    @Order(16)
    @DisplayName("6.2 多次调用幂等")
    void test16_multipleCallsIdempotent() {
        String sql = "SELECT * FROM user";
        String once = JSqlDynamicSqlParser.appendTenantCondition(sql);
        String twice = JSqlDynamicSqlParser.appendTenantCondition(once);
        assertEquals(once, twice, "多次调用结果应相同（幂等）");
    }

    // =========================================================
    // 7. 无 tenant_id 字段的表不注入
    // =========================================================

    @Test
    @Order(17)
    @DisplayName("7.1 role 表无 tenant_id 字段 — 不注入")
    void test17_tableWithoutTenantSkipped() {
        String result = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM role");
        assertFalse(result.contains(":myjpaTenantId"),
                "role 表未注册租户字段，不应注入租户条件");
    }

    @Test
    @Order(18)
    @DisplayName("7.2 未知表不注入")
    void test18_unknownTableSkipped() {
        String sql = "SELECT * FROM sys_config WHERE key = 'value'";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
        assertEquals(sql, result, "未知表不应被修改");
    }

    // =========================================================
    // 8. 删除条件 + 租户条件组合
    // =========================================================

    @Test
    @Order(19)
    @DisplayName("8.1 删除条件和租户条件同时注入")
    void test19_deleteAndTenantCombined() {
        String sql = "SELECT * FROM user";
        String withDelete = JSqlDynamicSqlParser.appendDeleteCondition(sql);
        String withBoth = JSqlDynamicSqlParser.appendTenantCondition(withDelete);

        assertTrue(withBoth.contains("delete_flag"),
                "应包含逻辑删除条件");
        assertTrue(withBoth.contains("tenant_id = :myjpaTenantId"),
                "应包含租户条件");
        // 两个条件都在 WHERE 中，用 AND 连接
        assertTrue(withBoth.toLowerCase().contains("and"),
                "两个条件应通过 AND 连接");
    }

    @Test
    @Order(20)
    @DisplayName("8.2 复杂 JOIN 下删除 + 租户同时注入")
    void test20_joinDeleteAndTenantCombined() {
        // 临时注册 role 也有 tenant_id
        TableCacheManager.registerTenantTable("role");
        try {
            String sql = "SELECT u.id, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id WHERE u.age > 18";
            String withDelete = JSqlDynamicSqlParser.appendDeleteCondition(sql);
            String withBoth = JSqlDynamicSqlParser.appendTenantCondition(withDelete);

            // user 主表：delete_flag 和 tenant_id 都在 WHERE
            assertTrue(withBoth.contains("u.delete_flag"), "user 删除条件应在 WHERE");
            assertTrue(withBoth.contains("u.tenant_id = :myjpaTenantId"), "user 租户条件应在 WHERE");
            // role LEFT JOIN：is_deleted 和 tenant_id 都在 ON
            assertTrue(withBoth.contains("r.is_deleted"), "role 删除条件应在 ON");
            assertTrue(withBoth.contains("r.tenant_id = :myjpaTenantId"), "role 租户条件应在 ON");
        } finally {
            TableCacheManager.clearCache();
            TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
            TableCacheManager.registerTenantTable("user");
        }
    }

    // =========================================================
    // 9. 可配置列名
    // =========================================================

    @Test
    @Order(21)
    @DisplayName("9.1 自定义租户列名（org_id）")
    void test21_customTenantColumnName() {
        JSqlDynamicSqlParser.tenantColumn = "org_id";
        // 重新注册：原来基于 "tenant_id" 的注册对 "org_id" 无效
        // 直接注册一个测试用表来验证列名替换逻辑
        TableCacheManager.registerTenantTable("user"); // user 已注册，org_id 作为列名
        try {
            String result = JSqlDynamicSqlParser.appendTenantCondition("SELECT * FROM user");
            assertTrue(result.contains("org_id = :myjpaTenantId"),
                    "自定义列名时应使用 org_id 而非 tenant_id");
            assertFalse(result.contains("tenant_id = :myjpaTenantId"),
                    "不应再出现默认的 tenant_id");
        } finally {
            JSqlDynamicSqlParser.tenantColumn = "tenant_id"; // 恢复
        }
    }

    // =========================================================
    // 10. 子查询中的租户注入
    // =========================================================

    @Test
    @Order(22)
    @DisplayName("10.1 子查询中的 user 表也注入租户条件")
    void test22_subQueryInjection() {
        String sql = "SELECT * FROM (SELECT id, username FROM user WHERE age > 18) t";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
        assertTrue(result.contains("tenant_id = :myjpaTenantId"),
                "子查询中的 user 表也应注入租户条件");
    }

    @Test
    @Order(23)
    @DisplayName("10.2 UNION 查询两边都注入")
    void test23_unionQueryInjection() {
        String sql = "SELECT id, username FROM user WHERE status = 1 "
                + "UNION ALL SELECT id, username FROM user WHERE status = 2";
        String result = JSqlDynamicSqlParser.appendTenantCondition(sql);
        int count = countOccurrences(result, ":myjpaTenantId");
        assertEquals(2, count, "UNION 两边的 user 表各应注入一次租户条件，共2次");
    }

    // =========================================================
    // 辅助方法
    // =========================================================

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
