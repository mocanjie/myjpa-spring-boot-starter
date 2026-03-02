package io.github.mocanjie.base.myjpa.lambda;

import io.github.mocanjie.base.myjpa.annotation.MyField;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import io.github.mocanjie.base.myjpa.utils.CommonUtils;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LambdaUtils {

    /**
     * 从 Lambda 方法引用中提取字段名
     * 例如：User::getName → "name"，User::isActive → "active"
     */
    public static <T, R> String getFieldName(SFunction<T, R> fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(fn);
            String methodName = serializedLambda.getImplMethodName();
            if (methodName.startsWith("get") && methodName.length() > 3) {
                return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            }
            if (methodName.startsWith("is") && methodName.length() > 2) {
                return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }
            return methodName;
        } catch (Exception e) {
            throw new RuntimeException("无法从 Lambda 中获取字段名", e);
        }
    }

    /**
     * 从 Lambda 方法引用中提取对应的数据库列名
     * 优先使用 @MyField.value()，否则驼峰转下划线
     */
    public static <T, R> String getColumnName(SFunction<T, R> fn, Class<T> clazz) {
        String fieldName = getFieldName(fn);
        try {
            TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
            for (Field field : tableInfo.getFieldList()) {
                if (field.getName().equals(fieldName)) {
                    MyField myField = field.getAnnotation(MyField.class);
                    if (myField != null && !myField.value().isEmpty()) {
                        return myField.value();
                    }
                    return CommonUtils.camelCaseToUnderscore(fieldName);
                }
            }
        } catch (Exception ignored) {
            // TableInfo 未初始化（如单元测试环境），fallback 到驼峰转下划线
        }
        return CommonUtils.camelCaseToUnderscore(fieldName);
    }
}
