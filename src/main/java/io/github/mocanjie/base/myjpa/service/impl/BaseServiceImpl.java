package io.github.mocanjie.base.myjpa.service.impl;


import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
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
	public <T> T querySingleByField(String fieldName, String fieldValue, Class<T> clazz) {
		return baseDao.querySingleByField(fieldName,fieldValue, clazz);
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
	public <PO> Serializable insertPO(PO po, boolean autoCreateId) {
		return baseDao.insertPO(po, autoCreateId);
	}

	@Transactional
	public <PO> int updatePO(PO po) {
		return baseDao.updatePO(po);
	}


	@Override
	public <PO> PO queryById(String id, Class<PO> clazz) {
		return baseDao.queryById(id,clazz);
	}

	@Override
	public  <PO> PO queryById(Long id, Class<PO> clazz) {
		return baseDao.queryById(String.valueOf(id), clazz);
	}

	@Override
	@Transactional
	public <PO> int delPO(PO po) {
		return baseDao.delPO(po);
	}

	@Override
	@Transactional
	public <PO> int delByIds(Class<PO> clazz, Object... id) {
		return baseDao.delByIds(clazz, id);
	}

	@Override
	@Transactional
	public <PO> int delById4logic(Class<PO> clazz, Object... id) {
		return baseDao.delById4logic(clazz, id);
	}

	@Override
	@Transactional
	public <PO> Serializable insertPO(PO po) {
		return baseDao.insertPO(po, true);
	}


	@Transactional
	public <PO> int updatePO(PO po, boolean ignoreNull) {
		return baseDao.updatePO(po, ignoreNull);
	}

	@Override
	@Transactional
	public <PO> int updatePO(PO po, @Nullable String... forceUpdateProperties) {
		return baseDao.updatePO(po, forceUpdateProperties);
	}

	@Override
	@Transactional
	public <PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		return baseDao.batchInsertPO(pos, autoCreateId);
	}

	@Override
	@Transactional
	public <PO> Serializable batchInsertPO(List<PO> pos) {
		return baseDao.batchInsertPO(pos, true);
	}

	@Override
	public <PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize) {
		return baseDao.batchInsertPO(pos, autoCreateId, batchSize);
	}

	@Override
	public <PO> Serializable batchInsertPO(List<PO> pos, int batchSize) {
		return this.batchInsertPO(pos, true, batchSize);
	}

}
