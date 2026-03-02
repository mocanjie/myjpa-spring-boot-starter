package io.github.mocanjie.base.myjpa.service;

import io.github.mocanjie.base.mycommon.pager.Pager;
import io.github.mocanjie.base.myjpa.MyTableEntity;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


public interface IBaseService {

	<T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Object param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Object param, Class<T> clazz);

	<T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId);

	<PO extends MyTableEntity> Serializable insertPO(PO po);

	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);

	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos);

	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);

	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, int batchSize);

	<PO extends MyTableEntity> int updatePO(PO po);

	<PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull);

	<PO extends MyTableEntity> int updatePO(PO po, @Nullable String... forceUpdateProperties);

	<PO extends MyTableEntity> PO queryById(String id, Class<PO> clazz);

	<PO extends MyTableEntity> PO queryById(Long id, Class<PO> clazz);

	<PO extends MyTableEntity> int delPO(PO po);

	<PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id);

}
