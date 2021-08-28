package io.github.mocanjie.base.myjpa.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyReflectionUtils {

    public static List<Field> getFieldList(Class<?> aClass){
        if (Objects.isNull(aClass)) {
            return Collections.emptyList();
        } else {
            if (aClass.getSuperclass() != null) {
                List<Field> fieldList = (List) Stream.of(aClass.getDeclaredFields()).filter((field) -> {
                    return !Modifier.isStatic(field.getModifiers());
                }).filter((field) -> {
                    return !Modifier.isTransient(field.getModifiers());
                }).collect(Collectors.toCollection(LinkedList::new));
                Class<?> superClass = aClass.getSuperclass();
                return excludeOverrideSuperField(fieldList, getFieldList(superClass));
            } else {
                return Collections.emptyList();
            }
        }

    }

    public static List<Field> excludeOverrideSuperField(List<Field> fieldList, List<Field> superFieldList) {
        Map<String, Field> fieldMap = (Map)fieldList.stream().collect(Collectors.toMap(Field::getName, Function.identity()));
        superFieldList.stream().filter((field) -> {
            return !fieldMap.containsKey(field.getName());
        }).forEach(fieldList::add);
        return fieldList;
    }
}
