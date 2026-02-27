package io.github.mocanjie.base.myjpa.dao.impl;

import io.github.mocanjie.base.mycommon.exception.BusinessException;
import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import io.github.mocanjie.base.myjpa.parser.JSqlDynamicSqlParser;
import io.github.mocanjie.base.myjpa.parser.SqlParser;
import io.github.mocanjie.base.myjpa.rowmapper.MyBeanPropertyRowMapper;
import io.github.mocanjie.base.myjpa.tenant.TenantAwareSqlParameterSource;
import io.github.mocanjie.base.myjpa.tenant.TenantContext;
import io.github.mocanjie.base.myjpa.tenant.TenantIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BaseDaoImpl implements IBaseDao {

	protected static Logger log = LoggerFactory.getLogger(BaseDaoImpl.class);

	public boolean isWrapClass(Class<?> clz) {
		return BeanUtils.isSimpleValueType(clz) || clz == java.sql.Date.class;
	}

	private <T> RowMapper<T> getRowMapper(Class<T> clazz) {
		if (isWrapClass(clazz)) return new SingleColumnRowMapper<>(clazz);
		return new MyBeanPropertyRowMapper<>(clazz);
	}

	@Autowired
	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	/** 租户ID提供者（SPI），集成方注册 Bean 后自动注入；未注册则为 null */
	@Autowired(required = false)
	private TenantIdProvider tenantIdProvider;

	protected JdbcTemplate getJdbcTemplate() {
		return (JdbcTemplate) this.namedParameterJdbcTemplate.getJdbcOperations();
	}

	/**
	 * 获取当前租户ID
	 * 优先级：TenantIdProvider SPI > TenantContext ThreadLocal
	 * 返回 null 表示超级管理员，不注入租户条件
	 */
	private Object getCurrentTenantId() {
		if (tenantIdProvider != null) {
			return tenantIdProvider.getTenantId();
		}
		return TenantContext.getTenantId();
	}

	/** 持有租户处理后的 SQL 和参数源 */
	private record TenantResult(String sql, SqlParameterSource sps) {}

	/**
	 * 租户条件处理：仅当全局开启、未跳过、且 tenantId 非空时才改写 SQL 并追加参数。
	 * tenantId=null（超管）时直接返回原 SQL 和原参数，避免出现找不到占位符参数的异常。
	 */
	private TenantResult applyTenant(String sql, SqlParameterSource sps) {
		if (!JSqlDynamicSqlParser.tenantEnabled || TenantContext.isSkipped()) {
			return new TenantResult(sql, sps);
		}
		Object tenantId = getCurrentTenantId();
		if (tenantId == null) {
			return new TenantResult(sql, sps); // 超管，不改写 SQL
		}
		String processedSql = JSqlDynamicSqlParser.appendTenantCondition(sql);
		if (processedSql.contains(":" + JSqlDynamicSqlParser.TENANT_PARAM_NAME)) {
			return new TenantResult(processedSql,
					new TenantAwareSqlParameterSource(sps, JSqlDynamicSqlParser.TENANT_PARAM_NAME, tenantId));
		}
		return new TenantResult(processedSql, sps);
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Object param, Class<T> clazz) {
		String processedSql = JSqlDynamicSqlParser.appendDeleteCondition(sql);
		SqlParameterSource sps = param == null
				? new EmptySqlParameterSource()
				: new BeanPropertySqlParameterSource(param);
		var tenant = applyTenant(processedSql, sps);
		return namedParameterJdbcTemplate.query(tenant.sql(), tenant.sps(), getRowMapper(clazz));
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		String processedSql = JSqlDynamicSqlParser.appendDeleteCondition(sql);
		SqlParameterSource sps = (param == null || param.isEmpty())
				? new EmptySqlParameterSource()
				: new MapSqlParameterSource(param);
		var tenant = applyTenant(processedSql, sps);
		return namedParameterJdbcTemplate.query(tenant.sql(), tenant.sps(), getRowMapper(clazz));
	}

	@Override
	public <T> T querySingleForSql(String sql, Object param, Class<T> clazz) {
		List<T> list = this.queryListForSql(sql, param, clazz);
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	@Override
	public <T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		List<T> list = this.queryListForSql(sql, param, clazz);
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz) {
		String processedSql = JSqlDynamicSqlParser.appendDeleteCondition(sql);
		SqlParameterSource sps = param == null
				? new EmptySqlParameterSource()
				: new BeanPropertySqlParameterSource(param);
		var tenant = applyTenant(processedSql, sps);
		if (!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( " + tenant.sql() + " ) mkt_page_count";
			pager.setTotalRows(namedParameterJdbcTemplate.queryForObject(countSql, tenant.sps(), new SingleColumnRowMapper<>(Long.class)));
			if (pager.getTotalRows() > 0) {
				pager.setPageData(namedParameterJdbcTemplate.query(SqlBuilder.buildPagerSql(tenant.sql(), pager), tenant.sps(), getRowMapper(clazz)));
			} else {
				pager.setPageData(new ArrayList<>());
			}
		} else {
			pager.setPageData(namedParameterJdbcTemplate.query(SqlBuilder.buildPagerSql(tenant.sql(), pager), tenant.sps(), getRowMapper(clazz)));
		}
		return pager;
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz) {
		String processedSql = JSqlDynamicSqlParser.appendDeleteCondition(sql);
		SqlParameterSource sps = (param == null || param.isEmpty())
				? new EmptySqlParameterSource()
				: new MapSqlParameterSource(param);
		var tenant = applyTenant(processedSql, sps);
		if (!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( " + tenant.sql() + " ) mkt_page_count";
			pager.setTotalRows(namedParameterJdbcTemplate.queryForObject(countSql, tenant.sps(), new SingleColumnRowMapper<>(Long.class)));
			if (pager.getTotalRows() > 0) {
				pager.setPageData(namedParameterJdbcTemplate.query(SqlBuilder.buildPagerSql(tenant.sql(), pager), tenant.sps(), getRowMapper(clazz)));
			} else {
				pager.setPageData(new ArrayList<>());
			}
		} else {
			pager.setPageData(namedParameterJdbcTemplate.query(SqlBuilder.buildPagerSql(tenant.sql(), pager), tenant.sps(), getRowMapper(clazz)));
		}
		return pager;
	}

	@Override
	public <PO> PO querySingleByField(String fieldName, String fieldValue, Class<PO> clazz) {
		String sql = SqlParser.getSelectByFieldSql(TableInfoBuilder.getTableInfo(clazz), fieldName);
		Map<String, Object> param = new HashMap<>();
		param.put(fieldName, fieldValue);
		return querySingleForSql(sql, param, clazz);
	}

	@Override
	public <PO> PO queryById(Object id, Class<PO> clazz) {
		TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
		String sql = SqlParser.getSelectByIdSql(tableInfo);
		Map<String, Object> param = new HashMap<>();
		param.put(tableInfo.getPkFieldName(), id);
		return querySingleForSql(sql, param, clazz);
	}

	@Override
	public <PO> Serializable insertPO(PO po, boolean autoCreateId) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);
			if (autoCreateId) {
				tableInfo.setPkValue(po);
				namedParameterJdbcTemplate.update(SqlParser.getInsertSql(tableInfo, po), paramSource);
				return (Serializable) tableInfo.getPkValue(po);
			} else {
				Object pkValue = tableInfo.getPkValue(po);
				if (pkValue != null) {
					namedParameterJdbcTemplate.update(SqlParser.getInsertSql(tableInfo, po), paramSource);
					return (Serializable) pkValue;
				}
				KeyHolder holder = new GeneratedKeyHolder();
				namedParameterJdbcTemplate.update(SqlParser.getInsertSql(tableInfo, po), paramSource, holder);
				long id = holder.getKey().longValue();
				tableInfo.setPkValue(po, id);
				return id;
			}
		} catch (Exception e) {
			log.error("插入异常", e);
			if (e instanceof DuplicateKeyException) {
				throw (DuplicateKeyException) e;
			} else {
				throw new BusinessException("系统错误,请联系管理员");
			}
		}
	}

	@Override
	public <PO> int updatePO(PO po) {
		return updatePO(po, true);
	}

	@Override
	public <PO> int updatePO(PO po, boolean ignoreNull) {
		return updatePO(po, ignoreNull, (String[]) null);
	}

	@Override
	public <PO> int updatePO(PO po, @Nullable String... forceUpdateFields) {
		return updatePO(po, true, forceUpdateFields);
	}

	private <PO> int updatePO(PO po, boolean ignoreNull, @Nullable String... forceUpdateFields) {
		String sql = SqlParser.getUpdateSql(TableInfoBuilder.getTableInfo(po.getClass()), po, ignoreNull, forceUpdateFields);
		SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);
		return namedParameterJdbcTemplate.update(sql, paramSource);
	}

	@Override
	public <PO> int delPO(PO po) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			String sql = SqlParser.getDelByIdSql(tableInfo);
			return this.getJdbcTemplate().update(sql, tableInfo.getPkValue(po));
		} catch (Exception e) {
			throw new BusinessException("delPO error!");
		}
	}

	@Override
	public <PO> int delByIds(Class<PO> clazz, Object... id) {
		try {
			List<Map<String, Object>> beanList = new ArrayList<>();
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
			for (Object o : id) {
				Map<String, Object> map = new HashMap<>();
				map.put(tableInfo.getPkFieldName(), o);
				beanList.add(map);
			}
			String sql = SqlParser.getDelByIdsSql(tableInfo);
			SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(beanList);
			return namedParameterJdbcTemplate.batchUpdate(sql, params).length;
		} catch (Exception e) {
			throw new BusinessException("del error!");
		}
	}

	@Override
	public <PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		if (pos == null || pos.isEmpty()) return 0;
		try {
			List<PO> beanList = new ArrayList<>();
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(pos.get(0).getClass());
			String sql = SqlParser.getInsertSql(tableInfo, pos.get(0), false);
			for (PO po : pos) {
				if (autoCreateId) {
					TableInfo tif = TableInfoBuilder.getTableInfo(po.getClass());
					tif.setPkValue(po);
				}
				beanList.add(po);
			}
			SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(beanList);
			namedParameterJdbcTemplate.batchUpdate(sql, params);
		} catch (Exception e) {
			log.error("批量新增异常", e);
			throw new BusinessException("系统错误,请联系管理员");
		}
		return null;
	}

	@Override
	public <PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize) {
		int totalSize = pos.size();
		int batchCount = (int) Math.ceil((double) totalSize / batchSize);
		int currentIndex = 0;
		for (int i = 0; i < batchCount; i++) {
			int remainingSize = totalSize - currentIndex;
			int currentBatchSize = Math.min(batchSize, remainingSize);
			List<PO> batchList = pos.subList(currentIndex, currentIndex + currentBatchSize);
			this.batchInsertPO(batchList, autoCreateId);
			currentIndex += currentBatchSize;
		}
		return null;
	}

}
