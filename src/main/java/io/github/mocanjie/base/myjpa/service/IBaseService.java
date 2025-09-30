package io.github.mocanjie.base.myjpa.service;

import io.github.mocanjie.base.mycommon.pager.Pager;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


public interface IBaseService {

	<T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Object param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Object param, Class<T> clazz);

	<T> T querySingleByField(String fieldName,String fieldValue, Class<T> clazz);

	<T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<PO> Serializable insertPO(PO po, boolean autoCreateId);

	<PO> Serializable insertPO(PO po);

	<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);

	<PO> Serializable batchInsertPO(List<PO> pos);

	<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);

	<PO> Serializable batchInsertPO(List<PO> pos, int batchSize);

	<PO> int updatePO(PO po);

	<PO> int updatePO(PO po,boolean ignoreNull);

	<PO> int updatePO(PO po,@Nullable String... forceUpdateProperties);

	<PO> PO queryById(String id, Class<PO> clazz);

	<PO> PO queryById(Long id, Class<PO> clazz);

	<PO> int delPO(PO po);

	<PO> int delByIds(Class<PO> clazz, Object... id);

}
