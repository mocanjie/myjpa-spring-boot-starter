package io.github.mocanjie.base.myjpa.parser;

import io.github.mocanjie.base.myjpa.annotation.MyField;
import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import io.github.mocanjie.base.myjpa.utils.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.Collectors;

public class SqlParser {

    public static final String INSERT_SQL = "INSERT INTO %s(%s) VALUES (%s)";
    public static final String UPDATE_SQL = "UPDATE %s SET %s WHERE %s=:%s";
    public static final String SELECT_BY_SQL = "SELECT * FROM %s WHERE %s=:%s";
    public static final String DEL_BYID_SQL = "DELETE FROM %s WHERE %s=?";
    public static final String DEL_BYIDS_SQL = "DELETE FROM %s WHERE %s in (:%s)";
    public static final String DEL_BYIDS_LOGIC_SQL = "UPDATE %s SET %s = %s WHERE %s in (:%s)";

    public static String getInsertSql(TableInfo tableInfo, Object obj){
        return getInsertSql(tableInfo,obj,true);
    }

    public static String getInsertSql(TableInfo tableInfo,Object obj,boolean ignoreNull){
        LinkedList<String> valueList = new LinkedList<>();
        String columns = tableInfo.getFieldList().stream().filter(f -> {
            MyField annotation = f.getAnnotation(MyField.class);
            if(annotation!=null && !annotation.serialize()) return false;
            if(ignoreNull){
                try {
                    PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), f.getName());
                    Object value =  propertyDescriptor.getReadMethod().invoke(obj);
                    if (value == null || String.valueOf(value).equalsIgnoreCase("null")) return false;
                    if (value instanceof String) return StringUtils.isNotBlank((String) value);
                    return true;
                } catch (Exception e) {
                }
                return false;
            }else{
                return true;
            }
        }).map(f->{
            String fName = f.getName().trim();
            valueList.add(fName);
            if(fName.equals(tableInfo.getPkFieldName())) return tableInfo.getPkColumnName();
            if(fName.equals(tableInfo.getDelFieldName())) return tableInfo.getDelColumnName();
            MyField annotation = f.getAnnotation(MyField.class);
            if(annotation!=null && StringUtils.isNotBlank(annotation.value())) return annotation.value().trim();
            return CommonUtils.camelCaseToUnderscore(String.format("%s",fName));
        }).collect(Collectors.joining(","));
        String values = valueList.stream().map(n->":"+n).collect(Collectors.joining(","));
        return String.format(INSERT_SQL,tableInfo.getTableName(),columns,values);
    }


    public static String getUpdateSql(TableInfo tableInfo,Object obj,boolean ignoreNull,String... forceUpdateFields){
        String columns = tableInfo.getFieldList().stream().filter(f -> {
            if(forceUpdateFields!=null && forceUpdateFields.length>0 && Arrays.asList(forceUpdateFields).contains(f.getName())){
                return true;
            }
            MyField annotation = f.getAnnotation(MyField.class);
            if(annotation!=null && !annotation.serialize()) return false;
            if(ignoreNull){
                try {
                    PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), f.getName());
                    Object value =  propertyDescriptor.getReadMethod().invoke(obj);
                    if (value == null || String.valueOf(value).equalsIgnoreCase("null")) return false;
                    if (value instanceof String) return StringUtils.isNotBlank((String) value);
                    return true;
                } catch (Exception e) {}
                return false;
            }else{
                return true;
            }
        }).map(f->{
            String fieldName = f.getName().trim();
            String colunm = CommonUtils.camelCaseToUnderscore(fieldName);
            if(fieldName.equals(tableInfo.getPkFieldName())) {
                colunm = tableInfo.getPkColumnName();
            }
            if(fieldName.equals(tableInfo.getDelFieldName())) {
                colunm = tableInfo.getDelColumnName();
            }
            MyField annotation = f.getAnnotation(MyField.class);
            if(annotation!=null && StringUtils.isNotBlank(annotation.value())) {
                colunm = annotation.value().trim();
            }
            return String.format("%s=:%s",colunm,fieldName);
        }).collect(Collectors.joining(","));
        return String.format(UPDATE_SQL,tableInfo.getTableName(),columns,tableInfo.getPkColumnName(),tableInfo.getPkFieldName());
    }



    public static String getSelectByIdSql(TableInfo tableInfo){
        return String.format(SELECT_BY_SQL,tableInfo.getTableName(),tableInfo.getPkColumnName(),tableInfo.getPkFieldName());
    }

    public static String getSelectByFieldSql(TableInfo tableInfo,String fieldName){
        Field field = tableInfo.getFieldByName(fieldName);
        String columnName = CommonUtils.camelCaseToUnderscore(fieldName);
        MyField annotation = field.getAnnotation(MyField.class);
        if(annotation!=null && StringUtils.isNotBlank(annotation.value())) columnName = annotation.value();
        if(fieldName.equals(tableInfo.getPkFieldName())) columnName = tableInfo.getPkColumnName();
        if(fieldName.equals(tableInfo.getDelFieldName())) columnName = tableInfo.getDelColumnName();
        return String.format(SELECT_BY_SQL,tableInfo.getTableName(),columnName,fieldName);
    }

    /**
     * 智能生成单条删除SQL（自动判断物理删除还是逻辑删除）
     * @param tableInfo 表信息
     * @return DELETE 或 UPDATE 语句
     */
    public static String getDelByIdSql(TableInfo tableInfo){
        // 检查是否配置了逻辑删除且字段在数据库中有效
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByClassName(tableInfo.getClazz().getName());
        if (deleteInfo != null && deleteInfo.isValid() &&
            StringUtils.isNotBlank(deleteInfo.getDelColumn())) {
            // 逻辑删除：UPDATE table SET delete_flag = 1 WHERE id = ?
            return String.format("UPDATE %s SET %s = %s WHERE %s = ?",
                    tableInfo.getTableName(),
                    deleteInfo.getDelColumn(),
                    deleteInfo.getDelValue(),
                    tableInfo.getPkColumnName());
        } else {
            // 物理删除：DELETE FROM table WHERE id = ?
            return String.format(DEL_BYID_SQL, tableInfo.getTableName(), tableInfo.getPkColumnName());
        }
    }

    /**
     * 智能生成批量删除SQL（自动判断物理删除还是逻辑删除）
     * @param tableInfo 表信息
     * @return DELETE 或 UPDATE 语句
     */
    public static String getDelByIdsSql(TableInfo tableInfo){
        // 检查是否配置了逻辑删除且字段在数据库中有效
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByClassName(tableInfo.getClazz().getName());
        if (deleteInfo != null && deleteInfo.isValid() &&
            StringUtils.isNotBlank(deleteInfo.getDelColumn())) {
            // 逻辑删除：UPDATE table SET delete_flag = 1 WHERE id IN (:ids)
            return String.format(DEL_BYIDS_LOGIC_SQL,
                    tableInfo.getTableName(),
                    deleteInfo.getDelColumn(),
                    deleteInfo.getDelValue(),
                    tableInfo.getPkColumnName(),
                    tableInfo.getPkFieldName());
        } else {
            // 物理删除：DELETE FROM table WHERE id IN (:ids)
            return String.format(DEL_BYIDS_SQL, tableInfo.getTableName(), tableInfo.getPkColumnName(), tableInfo.getPkFieldName());
        }
    }

}
