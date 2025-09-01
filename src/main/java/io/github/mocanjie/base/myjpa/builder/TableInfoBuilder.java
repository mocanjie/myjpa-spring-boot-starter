package io.github.mocanjie.base.myjpa.builder;

import io.github.mocanjie.base.mycommon.exception.BusinessException;
import io.github.mocanjie.base.myjpa.annotation.MyTable;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import io.github.mocanjie.base.myjpa.utils.MyReflectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Slf4j
public class TableInfoBuilder implements BeanPostProcessor, Ordered {

    private static final Map<Class<?>, TableInfo> tableInfoMap = new HashMap<>();

    @PostConstruct
    private void init() {
        log.info("初始化@MyTable信息...");
        Reflections reflections = null;
        String osName = System.getProperty("os.name");
        log.info("系统信息:{}",osName);
        if (osName.contains("Mac")) {
            // Mac系统
            reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forJavaClassPath())
                    .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner()));
        } else {
            // 其他
//            reflections = new Reflections();
            reflections = new Reflections("", new TypeAnnotationsScanner(), new SubTypesScanner());
        }
        Set<Class<?>> classSet = reflections.getTypesAnnotatedWith(MyTable.class);
        log.info("共找到@MyTable注解的类{}个",classSet.size());
        Iterator<Class<?>> iterator = classSet.iterator();
        while (iterator.hasNext()){
            Class<?> aClass = iterator.next();
            log.info("{}",aClass.toString());
            MyTable annotation = aClass.getAnnotation(MyTable.class);
            Field pkField = null;
            try {
                pkField = aClass.getDeclaredField(annotation.pkField());
            }catch (NoSuchFieldException e) {
                throw new BusinessException(aClass+"没有找到对应的主键");
            }
            TableInfo tableInfo = new TableInfo()
                    .setTableName(annotation.value())
                    .setClazz(aClass)
                    .setPkField(pkField)
                    .setPkFieldName(annotation.pkField())
                    .setPkColumnName(annotation.pkColumn())
                    .setFieldList(MyReflectionUtils.getFieldList(aClass))
                    .setDelColumnName(annotation.delColumn())
                    .setDelFieldName(annotation.delField())
                    .setDelValue(annotation.delValue());
            tableInfoMap.put(aClass,tableInfo);
        }
        
        // TableInfoBuilder初始化完成后立即初始化TableCacheManager
        // 确保缓存在任何SQL执行之前就已经准备好
        initTableCacheManager();
    }
    
    /**
     * 在TableInfoBuilder初始化完成后立即初始化TableCacheManager
     * 这样可以确保缓存优先于SQL执行进行初始化
     */
    private void initTableCacheManager() {
        try {
            String basePackage = deduceMainApplicationPackage();
            TableCacheManager.initCache(basePackage);
            
            log.info("TableCacheManager已在TableInfoBuilder后初始化完成，智能扫描包: {}", basePackage);
            log.info("缓存统计: {}", TableCacheManager.getCacheStats());
        } catch (Exception e) {
            log.warn("初始化TableCacheManager时发生异常，使用fallback扫描: {}", e.getMessage());
            // fallback: 如果无法推导包路径，使用常见的业务包前缀
            fallbackScan();
        }
    }
    
    /**
     * 智能推导Spring Boot应用的主包路径
     * 通过堆栈跟踪找到main方法所在的类的包路径
     */
    private String deduceMainApplicationPackage() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if ("main".equals(element.getMethodName())) {
                String className = element.getClassName();
                int lastDotIndex = className.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    return className.substring(0, lastDotIndex);
                }
            }
        }
        
        // 如果堆栈跟踪方法失败，尝试其他方式
        return deduceFromSystemProperties();
    }
    
    /**
     * 从系统属性推导包路径
     */
    private String deduceFromSystemProperties() {
        // 尝试从系统属性获取应用主类
        String mainClass = System.getProperty("sun.java.command");
        if (mainClass != null && mainClass.contains(".")) {
            String[] parts = mainClass.split("\\s+");
            if (parts.length > 0 && parts[0].contains(".")) {
                String className = parts[0];
                int lastDotIndex = className.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    return className.substring(0, lastDotIndex);
                }
            }
        }
        
        // 最后的fallback，返回常见的业务包
        return "com.example";
    }
    
    /**
     * fallback扫描策略
     */
    private void fallbackScan() {
        try {
            TableCacheManager.initCache("com.example", "com", "cn", "org");
            log.info("使用fallback包路径扫描@MyTable注解");
        } catch (Exception e) {
            log.error("Fallback扫描也失败了: {}", e.getMessage());
        }
    }

    public static TableInfo getTableInfo(Class<?> aClass){
        TableInfo tableInfo = tableInfoMap.get(aClass);
        if(tableInfo==null) throw new BusinessException(aClass+" 缺少@MyTable注解");
        return tableInfo;
    }


    @Override
    public int getOrder() {
        return 0;
    }
}
