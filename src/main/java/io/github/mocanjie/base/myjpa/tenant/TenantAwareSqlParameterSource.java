package io.github.mocanjie.base.myjpa.tenant;

import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * 租户感知的 SQL 参数源包装类
 *
 * <p>在原有 {@link SqlParameterSource} 基础上追加租户 ID 参数，
 * 使得 SQL 中的 {@code :myjpaTenantId} 占位符能被正确解析，
 * 同时不影响原有参数的正常使用。
 */
public class TenantAwareSqlParameterSource implements SqlParameterSource {

    private final SqlParameterSource delegate;
    private final String tenantParamName;
    private final Object tenantId;

    public TenantAwareSqlParameterSource(SqlParameterSource delegate, String tenantParamName, Object tenantId) {
        this.delegate = delegate;
        this.tenantParamName = tenantParamName;
        this.tenantId = tenantId;
    }

    @Override
    public boolean hasValue(String paramName) {
        if (tenantParamName.equals(paramName)) {
            return true;
        }
        return delegate.hasValue(paramName);
    }

    @Override
    public Object getValue(String paramName) throws IllegalArgumentException {
        if (tenantParamName.equals(paramName)) {
            return tenantId;
        }
        return delegate.getValue(paramName);
    }

    @Override
    public Integer getSqlType(String paramName) {
        if (tenantParamName.equals(paramName)) {
            return TYPE_UNKNOWN;
        }
        return delegate.getSqlType(paramName);
    }

    @Override
    public String getTypeName(String paramName) {
        if (tenantParamName.equals(paramName)) {
            return null;
        }
        return delegate.getTypeName(paramName);
    }
}
