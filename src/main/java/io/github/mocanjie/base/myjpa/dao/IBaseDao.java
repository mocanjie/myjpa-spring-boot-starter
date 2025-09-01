package io.github.mocanjie.base.myjpa.dao;

import io.github.mocanjie.base.mycommon.pager.Pager;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


public interface IBaseDao {
	
	<T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Object param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Object param, Class<T> clazz);

	<T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);

	<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);

	<PO> PO querySingleByField(String fieldName,String fieldValue, Class<PO> clazz);

	<PO> Serializable insertPO(PO po, boolean autoCreateId);
	
	<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);

	<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);
	
	<PO> int updatePO(PO po);
	
	<PO> int updatePO(PO po, boolean ignoreNull);
	
	<PO> int updatePO(PO po, @Nullable String... forceUpdateProperties);

	<PO> PO queryById(Object id, Class<PO> clazz);

	<PO> int delPO(PO po);

	<PO> int delByIds(Class<PO> clazz, Object... id);
	
	<PO> int delById4logic(Class<PO> clazz, Object... id);

	// ======================= 新增支持动态拼接删除条件的查询方法 =======================
	
	/**
	 * 查询列表，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param clazz 返回结果类型
	 * @return 查询结果列表
	 */
	<T> List<T> queryListForSqlWithDeleteCondition(String sql, Object param, Class<T> clazz);
	
	/**
	 * 查询列表，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param clazz 返回结果类型
	 * @return 查询结果列表
	 */
	<T> List<T> queryListForSqlWithDeleteCondition(String sql, Map<String, Object> param, Class<T> clazz);
	
	/**
	 * 查询单条记录，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param clazz 返回结果类型
	 * @return 查询结果
	 */
	<T> T querySingleForSqlWithDeleteCondition(String sql, Object param, Class<T> clazz);
	
	/**
	 * 查询单条记录，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param clazz 返回结果类型
	 * @return 查询结果
	 */
	<T> T querySingleForSqlWithDeleteCondition(String sql, Map<String, Object> param, Class<T> clazz);
	
	/**
	 * 分页查询，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param pager 分页对象
	 * @param clazz 返回结果类型
	 * @return 分页结果
	 */
	<T> Pager<T> queryPageForSqlWithDeleteCondition(String sql, Object param, Pager<T> pager, Class<T> clazz);
	
	/**
	 * 分页查询，自动拼接删除条件（基于SQL中的表名自动识别）
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param pager 分页对象
	 * @param clazz 返回结果类型
	 * @return 分页结果
	 */
	<T> Pager<T> queryPageForSqlWithDeleteCondition(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);
	
	/**
	 * 查询列表，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 查询结果列表
	 */
	<T> List<T> queryListForSqlWithEntityDeleteCondition(String sql, Object param, Class<T> clazz, Class<?> entityClass, String tableAlias);
	
	/**
	 * 查询列表，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 查询结果列表
	 */
	<T> List<T> queryListForSqlWithEntityDeleteCondition(String sql, Map<String, Object> param, Class<T> clazz, Class<?> entityClass, String tableAlias);
	
	/**
	 * 查询单条记录，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 查询结果
	 */
	<T> T querySingleForSqlWithEntityDeleteCondition(String sql, Object param, Class<T> clazz, Class<?> entityClass, String tableAlias);
	
	/**
	 * 查询单条记录，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 查询结果
	 */
	<T> T querySingleForSqlWithEntityDeleteCondition(String sql, Map<String, Object> param, Class<T> clazz, Class<?> entityClass, String tableAlias);
	
	/**
	 * 分页查询，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数对象
	 * @param pager 分页对象
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 分页结果
	 */
	<T> Pager<T> queryPageForSqlWithEntityDeleteCondition(String sql, Object param, Pager<T> pager, Class<T> clazz, Class<?> entityClass, String tableAlias);
	
	/**
	 * 分页查询，基于指定实体类拼接删除条件
	 * @param sql 原始SQL语句
	 * @param param 参数Map
	 * @param pager 分页对象
	 * @param clazz 返回结果类型
	 * @param entityClass 用于获取删除条件的实体类
	 * @param tableAlias 表别名（可选）
	 * @return 分页结果
	 */
	<T> Pager<T> queryPageForSqlWithEntityDeleteCondition(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz, Class<?> entityClass, String tableAlias);

	/**
	 * 根据ID查询，自动拼接删除条件
	 * @param id 主键ID
	 * @param clazz 实体类型
	 * @return 查询结果
	 */
	<PO> PO queryByIdWithDeleteCondition(Object id, Class<PO> clazz);
	
	/**
	 * 根据字段查询单条记录，自动拼接删除条件
	 * @param fieldName 字段名
	 * @param fieldValue 字段值
	 * @param clazz 实体类型
	 * @return 查询结果
	 */
	<PO> PO querySingleByFieldWithDeleteCondition(String fieldName, String fieldValue, Class<PO> clazz);

}
