package io.github.mocanjie.base.myjpa.tenant;

import java.util.function.Supplier;

/**
 * 租户上下文工具类（ThreadLocal）
 *
 * <p>提供两类功能：
 * <ol>
 *   <li><b>编程式设置租户ID</b>：适合非 Spring 托管场景（如批处理、定时任务），
 *       作为 {@link TenantIdProvider} SPI 的备选方式。优先级低于 SPI。</li>
 *   <li><b>临时跳过租户隔离</b>：用于需要查询全租户数据的场景，例如后台管理功能。</li>
 * </ol>
 *
 * <p>跳过租户隔离示例：
 * <pre>{@code
 * // Lambda 形式（推荐）
 * List<User> all = TenantContext.withoutTenant(
 *     () -> userService.queryListForSql("SELECT * FROM user", null, User.class)
 * );
 *
 * // 手动控制
 * TenantContext.skip();
 * try {
 *     return dao.queryListForSql(...);
 * } finally {
 *     TenantContext.restore();
 * }
 * }</pre>
 */
public class TenantContext {

    /** ThreadLocal：编程式设置的租户ID（SPI 备选） */
    private static final ThreadLocal<Object> TENANT_ID = new ThreadLocal<>();

    /** ThreadLocal：是否跳过租户条件注入 */
    private static final ThreadLocal<Boolean> SKIP_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    // ===================== 编程式租户ID设置 =====================

    /**
     * 编程式设置当前线程的租户ID
     * 优先级低于 {@link TenantIdProvider} SPI Bean
     */
    public static void setTenantId(Object tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前线程通过 ThreadLocal 设置的租户ID
     */
    public static Object getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 清除当前线程的租户ID
     * 建议在请求结束时（Filter/Interceptor afterCompletion）调用，防止内存泄漏
     */
    public static void clearTenantId() {
        TENANT_ID.remove();
    }

    // ===================== 跳过租户隔离 =====================

    /**
     * 标记当前线程跳过租户条件注入（之后所有查询不过滤租户）
     * 使用后务必调用 {@link #restore()} 或使用 {@link #withoutTenant(Supplier)}
     */
    public static void skip() {
        SKIP_TENANT.set(Boolean.TRUE);
    }

    /**
     * 恢复租户条件注入
     */
    public static void restore() {
        SKIP_TENANT.remove();
    }

    /**
     * 检查当前线程是否已标记跳过租户条件注入
     */
    public static boolean isSkipped() {
        return Boolean.TRUE.equals(SKIP_TENANT.get());
    }

    /**
     * 在跳过租户条件的上下文中执行代码（有返回值，推荐使用）
     *
     * @param supplier 要执行的代码块
     * @return 执行结果
     */
    public static <T> T withoutTenant(Supplier<T> supplier) {
        skip();
        try {
            return supplier.get();
        } finally {
            restore();
        }
    }

    /**
     * 在跳过租户条件的上下文中执行代码（无返回值）
     *
     * @param runnable 要执行的代码块
     */
    public static void withoutTenant(Runnable runnable) {
        skip();
        try {
            runnable.run();
        } finally {
            restore();
        }
    }

    /**
     * 清除当前线程所有 ThreadLocal 状态
     * 建议在请求结束时调用，防止内存泄漏
     */
    public static void clear() {
        TENANT_ID.remove();
        SKIP_TENANT.remove();
    }
}
