package io.github.mocanjie.base.myjpa.tenant;

/**
 * 租户ID提供者接口（SPI）
 * 集成方实现此接口并注册为 Spring Bean，myjpa 将自动调用获取当前请求的租户ID
 *
 * <p>示例：
 * <pre>{@code
 * @Bean
 * public TenantIdProvider tenantIdProvider() {
 *     return () -> SecurityContextHolder.getContext().getAuthentication().getTenantId();
 * }
 * }</pre>
 *
 * <p>返回 null 表示超级管理员，不注入租户隔离条件
 */
public interface TenantIdProvider {

    /**
     * 获取当前登录用户的租户ID
     *
     * @return 租户ID，返回 null 表示超级管理员（不过滤）
     */
    Object getTenantId();
}
