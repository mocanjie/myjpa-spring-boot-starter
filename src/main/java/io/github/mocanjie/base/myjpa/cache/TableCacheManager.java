package io.github.mocanjie.base.myjpa.cache;

import io.github.mocanjie.base.myjpa.annotation.MyTable;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyTable注解信息缓存管理器
 * 在项目启动时扫描所有标注@MyTable的实体类，缓存删除条件信息
 */
public class TableCacheManager {
    
    private static final Logger log = LoggerFactory.getLogger(TableCacheManager.class);
    
    /**
     * 缓存表名到删除条件信息的映射
     * key: 表名(tableName)
     * value: DeleteInfo对象，包含删除字段名和删除值
     */
    private static final Map<String, DeleteInfo> TABLE_DELETE_INFO_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 缓存类名到删除条件信息的映射
     * key: 类的全限定名(className)
     * value: DeleteInfo对象，包含删除字段名和删除值
     */
    private static final Map<String, DeleteInfo> CLASS_DELETE_INFO_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 缓存类名到表名的映射
     * key: 类的全限定名(className)
     * value: 表名(tableName)
     */
    private static final Map<String, String> CLASS_TABLE_NAME_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 删除条件信息
     */
    public static class DeleteInfo {
        private final String delColumn;  // 删除字段列名
        private final String delField;   // 删除字段属性名
        private final int delValue;      // 删除标记值
        
        public DeleteInfo(String delColumn, String delField, int delValue) {
            this.delColumn = delColumn;
            this.delField = delField;
            this.delValue = delValue;
        }
        
        public String getDelColumn() {
            return delColumn;
        }
        
        public String getDelField() {
            return delField;
        }
        
        public int getDelValue() {
            return delValue;
        }
        
        public int getUnDelValue() {
            return delValue == 0 ? 1 : 0;  // 返回未删除状态的值
        }
    }
    
    /**
     * 初始化缓存，扫描所有@MyTable注解的类
     * 
     * @param basePackages 要扫描的基础包路径
     */
    public static void initCache(String... basePackages) {
        log.info("开始扫描@MyTable注解的类并构建缓存...");
        
        try {
            for (String basePackage : basePackages) {
                scanPackage(basePackage);
            }
            log.info("@MyTable注解缓存构建完成，共缓存{}个表的删除信息", TABLE_DELETE_INFO_CACHE.size());
        } catch (Exception e) {
            log.error("初始化@MyTable注解缓存时发生异常", e);
        }
    }
    
    /**
     * 扫描指定包路径下的@MyTable注解
     */
    private static void scanPackage(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(MyTable.class);
        
        for (Class<?> clazz : annotatedClasses) {
            try {
                MyTable myTable = clazz.getAnnotation(MyTable.class);
                if (myTable != null) {
                    String tableName = myTable.value();
                    String className = clazz.getName();
                    
                    DeleteInfo deleteInfo = new DeleteInfo(
                        myTable.delColumn(),
                        myTable.delField(),
                        myTable.delValue()
                    );
                    
                    TABLE_DELETE_INFO_CACHE.put(tableName.toLowerCase(), deleteInfo);
                    CLASS_DELETE_INFO_CACHE.put(className, deleteInfo);
                    CLASS_TABLE_NAME_CACHE.put(className, tableName);
                    
                    log.info("缓存表删除信息: table={}, class={}, delColumn={}, delValue={}",
                             tableName, className, myTable.delColumn(), myTable.delValue());
                }
            } catch (Exception e) {
                log.warn("处理类{}的@MyTable注解时发生异常: {}", clazz.getName(), e.getMessage());
            }
        }
    }
    
    /**
     * 根据表名获取删除条件信息
     * 
     * @param tableName 表名
     * @return 删除条件信息，如果未找到返回null
     */
    public static DeleteInfo getDeleteInfoByTableName(String tableName) {
        if (tableName == null) return null;
        return TABLE_DELETE_INFO_CACHE.get(tableName.toLowerCase());
    }
    
    /**
     * 根据类名获取删除条件信息
     * 
     * @param className 类的全限定名
     * @return 删除条件信息，如果未找到返回null
     */
    public static DeleteInfo getDeleteInfoByClassName(String className) {
        if (className == null) return null;
        return CLASS_DELETE_INFO_CACHE.get(className);
    }
    
    /**
     * 根据实体类获取删除条件信息
     * 
     * @param clazz 实体类
     * @return 删除条件信息，如果未找到返回null
     */
    public static DeleteInfo getDeleteInfoByClass(Class<?> clazz) {
        if (clazz == null) return null;
        return CLASS_DELETE_INFO_CACHE.get(clazz.getName());
    }
    
    /**
     * 根据实体类获取对应的表名
     * 
     * @param clazz 实体类
     * @return 表名，如果未找到返回null
     */
    public static String getTableNameByClass(Class<?> clazz) {
        if (clazz == null) return null;
        return CLASS_TABLE_NAME_CACHE.get(clazz.getName());
    }
    
    /**
     * 检查表是否有删除条件配置
     * 
     * @param tableName 表名
     * @return 如果有删除条件配置返回true，否则返回false
     */
    public static boolean hasDeleteCondition(String tableName) {
        return getDeleteInfoByTableName(tableName) != null;
    }
    
    /**
     * 检查类是否有删除条件配置
     * 
     * @param clazz 实体类
     * @return 如果有删除条件配置返回true，否则返回false
     */
    public static boolean hasDeleteCondition(Class<?> clazz) {
        return getDeleteInfoByClass(clazz) != null;
    }
    
    /**
     * 清空缓存
     */
    public static void clearCache() {
        TABLE_DELETE_INFO_CACHE.clear();
        CLASS_DELETE_INFO_CACHE.clear();
        CLASS_TABLE_NAME_CACHE.clear();
        log.info("@MyTable注解缓存已清空");
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("TableCache: %d tables, ClassCache: %d classes", 
                           TABLE_DELETE_INFO_CACHE.size(), CLASS_DELETE_INFO_CACHE.size());
    }
}