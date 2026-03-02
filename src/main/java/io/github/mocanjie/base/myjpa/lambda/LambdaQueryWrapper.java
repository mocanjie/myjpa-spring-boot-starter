package io.github.mocanjie.base.myjpa.lambda;

import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.MyTableEntity;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;

import java.util.*;

/**
 * Lambda 链式查询构造器
 *
 * <p>两个类型参数：
 * <ul>
 *   <li>{@code T extends MyTableEntity}：实体类，决定表名和列映射（条件、排序、select 均基于此）</li>
 *   <li>{@code R}：结果类，终结方法的返回类型，可以是 {@code T} 本身或任意 DTO/VO</li>
 * </ul>
 *
 * <pre>
 * // 实体即结果（默认）
 * lambdaQuery(UserPO.class)
 *     .eq(UserPO::getStatus, 1)
 *     .list();                        // List&lt;UserPO&gt;
 *
 * // 映射到 DTO
 * lambdaQuery(UserPO.class, UserDTO.class)
 *     .select(UserPO::getId, UserPO::getName)
 *     .eq(UserPO::getStatus, 1)
 *     .list();                        // List&lt;UserDTO&gt;
 * </pre>
 *
 * 生成的 SQL 自动经过逻辑删除 + 租户隔离条件注入管道。
 */
public class LambdaQueryWrapper<T extends MyTableEntity, R> {

    private final Class<T> entityClazz;   // 实体类：确定表名与列映射
    private final Class<R> resultClazz;   // 结果类：终结方法的返回类型
    private final IBaseDao baseDao;
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private int paramIndex = 0;
    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();

    public LambdaQueryWrapper(Class<T> entityClazz, Class<R> resultClazz, IBaseDao baseDao) {
        this.entityClazz = entityClazz;
        this.resultClazz = resultClazz;
        this.baseDao = baseDao;
    }

    private String nextParam() {
        return "lwp" + (paramIndex++);
    }

    private String col(SFunction<T, ?> fn) {
        return LambdaUtils.getColumnName(fn, entityClazz);
    }

    // =========================================================
    // 条件方法（全部 AND 连接，列名基于实体类 T）
    // =========================================================

    public LambdaQueryWrapper<T, R> eq(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " = :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> ne(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " != :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> gt(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " > :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> ge(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " >= :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> lt(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " < :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> le(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " <= :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T, R> like(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, "%" + val + "%");
        return this;
    }

    public LambdaQueryWrapper<T, R> likeLeft(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, "%" + val);
        return this;
    }

    public LambdaQueryWrapper<T, R> likeRight(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, val + "%");
        return this;
    }

    public LambdaQueryWrapper<T, R> in(SFunction<T, ?> fn, Collection<?> vals) {
        String p = nextParam();
        conditions.add(col(fn) + " IN (:" + p + ")");
        params.put(p, vals);
        return this;
    }

    public LambdaQueryWrapper<T, R> notIn(SFunction<T, ?> fn, Collection<?> vals) {
        String p = nextParam();
        conditions.add(col(fn) + " NOT IN (:" + p + ")");
        params.put(p, vals);
        return this;
    }

    public LambdaQueryWrapper<T, R> between(SFunction<T, ?> fn, Object v1, Object v2) {
        String p1 = nextParam();
        String p2 = nextParam();
        conditions.add(col(fn) + " BETWEEN :" + p1 + " AND :" + p2);
        params.put(p1, v1);
        params.put(p2, v2);
        return this;
    }

    public LambdaQueryWrapper<T, R> isNull(SFunction<T, ?> fn) {
        conditions.add(col(fn) + " IS NULL");
        return this;
    }

    public LambdaQueryWrapper<T, R> isNotNull(SFunction<T, ?> fn) {
        conditions.add(col(fn) + " IS NOT NULL");
        return this;
    }

    // =========================================================
    // 列选择 & 排序（列名同样基于实体类 T）
    // =========================================================

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> select(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            selectColumns.add(col(fn));
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> orderByAsc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " ASC");
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> orderByDesc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " DESC");
        }
        return this;
    }

    // =========================================================
    // SQL 构建（可供测试直接断言）
    // =========================================================

    public String buildSql() {
        String tableName = TableCacheManager.getTableNameByClass(entityClazz);
        if (tableName == null) {
            throw new IllegalStateException(
                    "未找到实体类 " + entityClazz.getName() + " 对应的表名，请检查 @MyTable 注解");
        }
        StringBuilder sb = new StringBuilder("SELECT ");
        if (selectColumns.isEmpty()) {
            sb.append("*");
        } else {
            sb.append(String.join(", ", selectColumns));
        }
        sb.append(" FROM ").append(tableName);
        if (!conditions.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (!orderByClauses.isEmpty()) {
            sb.append(" ORDER BY ").append(String.join(", ", orderByClauses));
        }
        return sb.toString();
    }

    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(params);
    }

    // =========================================================
    // 终结方法（返回类型为 R）
    // =========================================================

    public List<R> list() {
        return baseDao.queryListForSql(buildSql(), params, resultClazz);
    }

    public R one() {
        List<R> results = list();
        return results.isEmpty() ? null : results.get(0);
    }

    public long count() {
        String countSql = "SELECT count(*) FROM (" + buildSql() + ") _lqw_count";
        Long result = baseDao.querySingleForSql(countSql, params, Long.class);
        return result == null ? 0L : result;
    }

    public Pager<R> page(Pager<R> pager) {
        return baseDao.queryPageForSql(buildSql(), params, pager, resultClazz);
    }

    public boolean exists() {
        return count() > 0;
    }
}
