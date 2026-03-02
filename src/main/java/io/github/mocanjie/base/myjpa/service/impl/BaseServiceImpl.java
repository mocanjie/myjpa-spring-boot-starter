package io.github.mocanjie.base.myjpa.service.impl;


import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.MyTableEntity;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.lambda.LambdaQueryWrapper;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Transactional(readOnly = true)
@Service
public class BaseServiceImpl implements IBaseService {

	@Autowired
	protected IBaseDao baseDao;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz) {
		return baseDao.queryPageForSql(sql, param, pager,clazz);
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Object param,Class<T> clazz) {
		return baseDao.queryListForSql(sql, param,clazz);
	}

	@Override
	public <T> T querySingleForSql(String sql, Object param,Class<T> clazz) {
		return baseDao.querySingleForSql(sql, param,clazz);
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz) {
		return baseDao.queryPageForSql(sql, param, pager, clazz);
	}


	@Override
	public <T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		return baseDao.queryListForSql(sql, param, clazz);
	}

	@Override
	public <T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		return baseDao.querySingleForSql(sql, param, clazz);
	}

	@Transactional
	public <PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId) {
		return baseDao.insertPO(po, autoCreateId);
	}

	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po) {
		return baseDao.updatePO(po);
	}


	@Override
	public <PO extends MyTableEntity> PO queryById(String id, Class<PO> clazz) {
		return baseDao.queryById(id, clazz);
	}

	@Override
	public <PO extends MyTableEntity> PO queryById(Long id, Class<PO> clazz) {
		return baseDao.queryById(String.valueOf(id), clazz);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int delPO(PO po) {
		return baseDao.delPO(po);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id) {
		return baseDao.delByIds(clazz, id);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable insertPO(PO po) {
		return baseDao.insertPO(po, true);
	}


	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull) {
		return baseDao.updatePO(po, ignoreNull);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po, @Nullable String... forceUpdateProperties) {
		return baseDao.updatePO(po, forceUpdateProperties);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		return baseDao.batchInsertPO(pos, autoCreateId);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos) {
		return baseDao.batchInsertPO(pos, true);
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize) {
		return baseDao.batchInsertPO(pos, autoCreateId, batchSize);
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, int batchSize) {
		return this.batchInsertPO(pos, true, batchSize);
	}

	/** 实体即结果类型（最常用） */
	protected <T extends MyTableEntity> LambdaQueryWrapper<T, T> lambdaQuery(Class<T> clazz) {
		return new LambdaQueryWrapper<>(clazz, clazz, baseDao);
	}

	/** 实体用于条件构造，结果映射到指定 DTO/VO */
	protected <T extends MyTableEntity, R> LambdaQueryWrapper<T, R> lambdaQuery(Class<T> entityClazz, Class<R> resultClazz) {
		return new LambdaQueryWrapper<>(entityClazz, resultClazz, baseDao);
	}

}
