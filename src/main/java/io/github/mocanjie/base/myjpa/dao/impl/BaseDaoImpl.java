package io.github.mocanjie.base.myjpa.dao.impl;

import io.github.mocanjie.base.mycommon.exception.BusinessException;
import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import io.github.mocanjie.base.myjpa.parser.SqlParser;
import io.github.mocanjie.base.myjpa.rowmapper.MyBeanPropertyRowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.lang.Nullable;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BaseDaoImpl implements IBaseDao {

	protected static Logger log = LoggerFactory.getLogger(BaseDaoImpl.class);

	public boolean isWrapClass(Class<?> clz) {
		return BeanUtils.isSimpleValueType(clz) || clz == java.sql.Date.class;
	}

	private <T> RowMapper<T> getRowMapper(Class<T> clazz){
		if(isWrapClass(clazz)) return new SingleColumnRowMapper<T>(clazz);
		return new MyBeanPropertyRowMapper<T>(clazz);
	}
	

	@Resource
	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	protected JdbcTemplate getJdbcTemplate(){
		return (JdbcTemplate) this.namedParameterJdbcTemplate.getJdbcOperations();
	}
	
	@Override
	public <T> List<T> queryListForSql(String sql, Object param,Class<T> clazz) {
		SqlParameterSource sps = null;
		if(param==null){
			sps = new EmptySqlParameterSource();
		}else{
			sps = new BeanPropertySqlParameterSource(param);
		}
		return namedParameterJdbcTemplate.query(sql,sps,getRowMapper(clazz));
	}
	
	@Override
	public <T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz) {
		SqlParameterSource sps = null;
		if(param==null){
			sps = new EmptySqlParameterSource();
		}else{
			sps = new BeanPropertySqlParameterSource(param);
		}
		String pagerSql = SqlBuilder.buildPagerSql(sql,pager);
		pager.setPageData(this.queryListForSql(pagerSql,param,clazz));
		if(!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( "+sql+" ) mkt_page_count";
			pager.setTotalRows(namedParameterJdbcTemplate.queryForObject(countSql, sps, new SingleColumnRowMapper<Long>(Long.class)));
		}
		return pager;
	}

	@Override
	public <T> T querySingleForSql(String sql, Object param,Class<T> clazz) {
		List<T> list = this.queryListForSql(sql, param,clazz);
		return (list==null || list.isEmpty())?null:list.get(0);
	}

	@Override
	public <PO> PO querySingleByField(String fieldName, String fieldValue, Class<PO> clazz) {
		String sql = SqlParser.getSelectByFieldSql(TableInfoBuilder.getTableInfo(clazz), fieldName);
		Map<String,Object> param = new HashMap<>();
		param.put(fieldName,fieldValue);
		List<PO> list = this.queryListForSql(sql, param,clazz);
		return (list==null || list.isEmpty())?null:list.get(0);
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz) {
		SqlParameterSource sps = (param==null|| param.isEmpty())? new EmptySqlParameterSource():new MapSqlParameterSource(param);
		pager.setPageData(this.queryListForSql(SqlBuilder.buildPagerSql(sql,pager),param,clazz));
		if(!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( "+sql+" ) mkt_page_count";
			pager.setTotalRows(namedParameterJdbcTemplate.queryForObject(countSql, sps, new SingleColumnRowMapper<Long>(Long.class)));
		}
		return pager;
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		SqlParameterSource sps = (param==null|| param.isEmpty())? new EmptySqlParameterSource():new MapSqlParameterSource(param);
		return namedParameterJdbcTemplate.query(sql,sps,getRowMapper(clazz));
	}


	@Override
	public <T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		List<T> list = this.queryListForSql(sql, param,clazz);
		return (list==null || list.isEmpty())?null:list.get(0);
	}

	@Override
	public <PO> Serializable insertPO(PO po, boolean autoCreateId) {
		try{
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			if(autoCreateId) {
				tableInfo.setPkValue(po);
			}
			SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);
			namedParameterJdbcTemplate.update(SqlParser.getInsertSql(tableInfo,po), paramSource);
			return (Serializable) tableInfo.getPkValue(po);
		}catch(Exception e){
			log.error("插入异常",e);
			if(e instanceof DuplicateKeyException){
				throw (DuplicateKeyException)e;
			}else{
				throw new BusinessException("系统错误,请联系管理员");
			}
		}
	}

	@Override
	public <PO> int updatePO(PO po) {
		return updatePO(po, true);
	}

	@Override
	public <PO> PO queryById(Object id, Class<PO> clazz) {
		TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
		String sql = SqlParser.getSelectByIdSql(tableInfo);
		Map<String,Object> param = new HashMap<String,Object>();
		param.put(tableInfo.getPkFieldName(), id);
		return querySingleForSql(sql, param, clazz);
	}

	@Override
	public <PO> int delPO(PO po) {
		try{
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			String sql = SqlParser.getDelByIdSql(tableInfo);
			return this.getJdbcTemplate().update(sql,tableInfo.getPkValue(po));
		}catch(Exception e){
			throw new BusinessException("delPO error!");
		}
	}

	@Override
	public <PO> int delByIds(Class<PO> clazz, Object... id) {
		try{
			List<Map<String,Object>> beanList = new ArrayList<>();
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
			for (Object o : id) {
				Map<String,Object> map = new HashMap<>();
				map.put(tableInfo.getPkFieldName(),o);
				beanList.add(map);
			}
			String sql = SqlParser.getDelByIdsSql(tableInfo);
			SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(beanList);
			return namedParameterJdbcTemplate.batchUpdate(sql, params).length;
		}catch(Exception e){
			throw new BusinessException("del error!");
		}
	}

	@Override
	public <PO> int delById4logic(Class<PO> clazz, Object... id) {
		try{
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
			String sql = SqlParser.getDelByIds4logicSql(tableInfo);
			List<Map<String,Object>> beanList = new ArrayList<>();
			for (Object o : id) {
				Map<String,Object> map = new HashMap<>();
				map.put(tableInfo.getPkFieldName(),o);
				beanList.add(map);
			}
			SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(beanList);
			return namedParameterJdbcTemplate.batchUpdate(sql, params).length;
		}catch(Exception e){
			e.printStackTrace();
			throw new BusinessException("del4logic error!");
		}
	}

	@Override
	public <PO> int updatePO(PO po, boolean ignoreNull) {
		return updatePO(po, ignoreNull, (String[])null);
	}
	
	private <PO> int updatePO(PO po, boolean ignoreNull,@Nullable String... forceUpdateFields) {
		String sql = SqlParser.getUpdateSql(TableInfoBuilder.getTableInfo(po.getClass()), po, ignoreNull, forceUpdateFields);
		SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);
		return namedParameterJdbcTemplate.update(sql, paramSource);
	}

	@Override
	public <PO> int updatePO(PO po, String... forceUpdateFields) {
		return updatePO(po, true, forceUpdateFields);
	}

	@Override
	public <PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		if(pos==null || pos.isEmpty()) return 0;
		try{
			List<PO> beanList = new ArrayList<>();
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(pos.get(0).getClass());
			String sql = SqlParser.getInsertSql(tableInfo, pos.get(0),false);
			for (PO po : pos) {
				if(autoCreateId) {
					TableInfo tif = TableInfoBuilder.getTableInfo(po.getClass());
					tif.setPkValue(po);
				}
				beanList.add(po);
			}
			SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(beanList);
			namedParameterJdbcTemplate.batchUpdate(sql, params);
		}catch(Exception e) {
			log.error("批量新增异常",e);
			throw new BusinessException("系统错误,请联系管理员");
		}
		return null;
	}

}
