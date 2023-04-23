package io.github.mocanjie.base.myjpa.builder;

import io.github.mocanjie.base.mycommon.exception.BusinessException;
import io.github.mocanjie.base.myjpa.annotation.MyTable;
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
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner()));
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
