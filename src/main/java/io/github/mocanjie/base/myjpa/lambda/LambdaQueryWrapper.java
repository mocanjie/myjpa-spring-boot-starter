package io.github.mocanjie.base.myjpa.lambda;

import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;

import java.util.*;

/**
 * Lambda 链式查询构造器
 * <p>
 * 用法示例：
 * <pre>
 *   lambdaQuery(UserPO.class)
 *       .select(UserPO::getId, UserPO::getName)
 *       .eq(UserPO::getStatus, 1)
 *       .like(UserPO::getName, "张")
 *       .orderByDesc(UserPO::getCreateTime)
 *       .list();
 * </pre>
 * 生成的 SQL 会自动经过逻辑删除 + 租户隔离条件注入管道。
 */
public class LambdaQueryWrapper<T> {

    private final Class<T> clazz;
    private final IBaseDao baseDao;
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private int paramIndex = 0;
    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();

    public LambdaQueryWrapper(Class<T> clazz, IBaseDao baseDao) {
        this.clazz = clazz;
        this.baseDao = baseDao;
    }

    private String nextParam() {
        return "lwp" + (paramIndex++);
    }

    private String col(SFunction<T, ?> fn) {
        return LambdaUtils.getColumnName(fn, clazz);
    }

    // =========================================================
    // 比较条件（全部 AND 连接）
    // =========================================================

    public LambdaQueryWrapper<T> eq(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " = :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> ne(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " != :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> gt(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " > :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> ge(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " >= :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> lt(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " < :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> le(SFunction<T, ?> fn, Object val) {
        String p = nextParam();
        conditions.add(col(fn) + " <= :" + p);
        params.put(p, val);
        return this;
    }

    public LambdaQueryWrapper<T> like(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, "%" + val + "%");
        return this;
    }

    public LambdaQueryWrapper<T> likeLeft(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, "%" + val);
        return this;
    }

    public LambdaQueryWrapper<T> likeRight(SFunction<T, ?> fn, String val) {
        String p = nextParam();
        conditions.add(col(fn) + " LIKE :" + p);
        params.put(p, val + "%");
        return this;
    }

    public LambdaQueryWrapper<T> in(SFunction<T, ?> fn, Collection<?> vals) {
        String p = nextParam();
        conditions.add(col(fn) + " IN (:" + p + ")");
        params.put(p, vals);
        return this;
    }

    public LambdaQueryWrapper<T> notIn(SFunction<T, ?> fn, Collection<?> vals) {
        String p = nextParam();
        conditions.add(col(fn) + " NOT IN (:" + p + ")");
        params.put(p, vals);
        return this;
    }

    public LambdaQueryWrapper<T> between(SFunction<T, ?> fn, Object v1, Object v2) {
        String p1 = nextParam();
        String p2 = nextParam();
        conditions.add(col(fn) + " BETWEEN :" + p1 + " AND :" + p2);
        params.put(p1, v1);
        params.put(p2, v2);
        return this;
    }

    public LambdaQueryWrapper<T> isNull(SFunction<T, ?> fn) {
        conditions.add(col(fn) + " IS NULL");
        return this;
    }

    public LambdaQueryWrapper<T> isNotNull(SFunction<T, ?> fn) {
        conditions.add(col(fn) + " IS NOT NULL");
        return this;
    }

    // =========================================================
    // 列选择 & 排序
    // =========================================================

    @SafeVarargs
    public final LambdaQueryWrapper<T> select(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            selectColumns.add(col(fn));
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T> orderByAsc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " ASC");
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T> orderByDesc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " DESC");
        }
        return this;
    }

    // =========================================================
    // SQL 构建（可供测试直接断言）
    // =========================================================

    public String buildSql() {
        String tableName = TableCacheManager.getTableNameByClass(clazz);
        if (tableName == null) {
            throw new IllegalStateException(
                    "未找到实体类 " + clazz.getName() + " 对应的表名，请检查 @MyTable 注解");
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
    // 终结方法
    // =========================================================

    public List<T> list() {
        return baseDao.queryListForSql(buildSql(), params, clazz);
    }

    public T one() {
        List<T> results = list();
        return results.isEmpty() ? null : results.get(0);
    }

    public long count() {
        String countSql = "SELECT count(*) FROM (" + buildSql() + ") _lqw_count";
        Long result = baseDao.querySingleForSql(countSql, params, Long.class);
        return result == null ? 0L : result;
    }

    public Pager<T> page(Pager<T> pager) {
        return baseDao.queryPageForSql(buildSql(), params, pager, clazz);
    }

    public boolean exists() {
        return count() > 0;
    }
}
