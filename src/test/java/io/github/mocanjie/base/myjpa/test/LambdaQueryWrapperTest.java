package io.github.mocanjie.base.myjpa.test;

import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.lambda.LambdaQueryWrapper;
import io.github.mocanjie.base.myjpa.test.entity.TestUser;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LambdaQueryWrapper 链式查询 API 测试
 * 不依赖 Spring 容器，直接验证 SQL 生成结果
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LambdaQueryWrapper 链式查询 API 测试")
class LambdaQueryWrapperTest {

    @BeforeAll
    static void setup() {
        SqlBuilder.type = 1;
        TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
    }

    private LambdaQueryWrapper<TestUser, TestUser> wrapper() {
        return new LambdaQueryWrapper<>(TestUser.class, TestUser.class, null);
    }

    @Test
    @Order(1)
    @DisplayName("1. eq 条件生成")
    void testEq() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().eq(TestUser::getUsername, "张三");
        assertEquals("SELECT * FROM user WHERE username = :lwp0", q.buildSql());
        assertEquals("张三", q.getParams().get("lwp0"));
    }

    @Test
    @Order(2)
    @DisplayName("2. ne 条件生成")
    void testNe() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().ne(TestUser::getUsername, "李四");
        assertEquals("SELECT * FROM user WHERE username != :lwp0", q.buildSql());
        assertEquals("李四", q.getParams().get("lwp0"));
    }

    @Test
    @Order(3)
    @DisplayName("3. gt / ge / lt / le 条件生成")
    void testCompare() {
        assertTrue(wrapper().gt(TestUser::getId, 10L).buildSql().contains("> :lwp0"));
        assertTrue(wrapper().ge(TestUser::getId, 10L).buildSql().contains(">= :lwp0"));
        assertTrue(wrapper().lt(TestUser::getId, 10L).buildSql().contains("< :lwp0"));
        assertTrue(wrapper().le(TestUser::getId, 10L).buildSql().contains("<= :lwp0"));
    }

    @Test
    @Order(4)
    @DisplayName("4. like / likeLeft / likeRight 条件生成")
    void testLike() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().like(TestUser::getUsername, "张");
        assertEquals("SELECT * FROM user WHERE username LIKE :lwp0", q.buildSql());
        assertEquals("%张%", q.getParams().get("lwp0"));

        assertEquals("%三", wrapper().likeLeft(TestUser::getUsername, "三").getParams().get("lwp0"));
        assertEquals("张%", wrapper().likeRight(TestUser::getUsername, "张").getParams().get("lwp0"));
    }

    @Test
    @Order(5)
    @DisplayName("5. in / notIn 条件生成")
    void testIn() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().in(TestUser::getId, ids);
        assertEquals("SELECT * FROM user WHERE id IN (:lwp0)", q.buildSql());
        assertEquals(ids, q.getParams().get("lwp0"));

        assertTrue(wrapper().notIn(TestUser::getId, ids).buildSql().contains("NOT IN (:lwp0)"));
    }

    @Test
    @Order(6)
    @DisplayName("6. between 条件生成")
    void testBetween() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().between(TestUser::getId, 10L, 100L);
        assertEquals("SELECT * FROM user WHERE id BETWEEN :lwp0 AND :lwp1", q.buildSql());
        assertEquals(10L, q.getParams().get("lwp0"));
        assertEquals(100L, q.getParams().get("lwp1"));
    }

    @Test
    @Order(7)
    @DisplayName("7. isNull / isNotNull 条件生成")
    void testNullCheck() {
        assertEquals("SELECT * FROM user WHERE username IS NULL",
                wrapper().isNull(TestUser::getUsername).buildSql());
        assertEquals("SELECT * FROM user WHERE username IS NOT NULL",
                wrapper().isNotNull(TestUser::getUsername).buildSql());
    }

    @Test
    @Order(8)
    @DisplayName("8. select 指定列")
    void testSelect() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().select(TestUser::getId, TestUser::getUsername);
        assertEquals("SELECT id, username FROM user", q.buildSql());
    }

    @Test
    @Order(9)
    @DisplayName("9. orderByAsc / orderByDesc")
    void testOrder() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getDeleteFlag, 0)
                .orderByAsc(TestUser::getId)
                .orderByDesc(TestUser::getUsername);
        assertTrue(q.buildSql().contains("ORDER BY id ASC, username DESC"));
    }

    @Test
    @Order(10)
    @DisplayName("10. 多条件 AND 组合")
    void testMultipleConditions() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getUsername, "张三")
                .gt(TestUser::getId, 0L)
                .isNotNull(TestUser::getDeleteFlag);
        assertEquals(
                "SELECT * FROM user WHERE username = :lwp0 AND id > :lwp1 AND delete_flag IS NOT NULL",
                q.buildSql());
    }

    @Test
    @Order(11)
    @DisplayName("11. 空条件时无 WHERE 子句")
    void testNoCondition() {
        assertEquals("SELECT * FROM user", wrapper().buildSql());
    }

    @Test
    @Order(12)
    @DisplayName("12. select + 条件 + 排序组合")
    void testFullQuery() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .select(TestUser::getId, TestUser::getUsername)
                .eq(TestUser::getDeleteFlag, 0)
                .orderByAsc(TestUser::getId);
        assertEquals(
                "SELECT id, username FROM user WHERE delete_flag = :lwp0 ORDER BY id ASC",
                q.buildSql());
    }
}
