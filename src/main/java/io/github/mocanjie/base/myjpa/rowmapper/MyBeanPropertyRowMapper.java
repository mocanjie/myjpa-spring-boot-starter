package io.github.mocanjie.base.myjpa.rowmapper;

import io.github.mocanjie.base.myjpa.annotation.MyField;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.metadata.TableInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.*;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;


public class MyBeanPropertyRowMapper<T> implements RowMapper<T> {


    protected final Log logger = LogFactory.getLog(getClass());

    /** The class we are mapping to. */
    @Nullable
    private Class<T> mappedClass;

    /** Whether we're strictly validating. */
    private boolean checkFullyPopulated = false;

    /** Whether we're defaulting primitives when mapping a null value. */
    private boolean primitivesDefaultedForNullValue = false;

    /** ConversionService for binding JDBC values to bean properties. */
    @Nullable
    private ConversionService conversionService = DefaultConversionService.getSharedInstance();

    /** Map of the fields we provide mapping for. */
    @Nullable
    private Map<String, PropertyDescriptor> mappedFields;

    /** Set of bean properties we provide mapping for. */
    @Nullable
    private Set<String> mappedProperties;


    /**
     * Create a new {@code BeanPropertyRowMapper} for bean-style configuration.
     * @see #setMappedClass
     * @see #setCheckFullyPopulated
     */
    public MyBeanPropertyRowMapper() {
    }

    /**
     * Create a new {@code BeanPropertyRowMapper}, accepting unpopulated
     * properties in the target bean.
     * @param mappedClass the class that each row should be mapped to
     */
    public MyBeanPropertyRowMapper(Class<T> mappedClass) {
        initialize(mappedClass);
    }

    /**
     * Create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param checkFullyPopulated whether we're strictly validating that
     * all bean properties have been mapped from corresponding database fields
     */
    public MyBeanPropertyRowMapper(Class<T> mappedClass, boolean checkFullyPopulated) {
        initialize(mappedClass);
        this.checkFullyPopulated = checkFullyPopulated;
    }


    /**
     * Set the class that each row should be mapped to.
     */
    public void setMappedClass(Class<T> mappedClass) {
        if (this.mappedClass == null) {
            initialize(mappedClass);
        }
        else {
            if (this.mappedClass != mappedClass) {
                throw new InvalidDataAccessApiUsageException("The mapped class can not be reassigned to map to " +
                        mappedClass + " since it is already providing mapping for " + this.mappedClass);
            }
        }
    }

    /**
     * Get the class that we are mapping to.
     */
    @Nullable
    public final Class<T> getMappedClass() {
        return this.mappedClass;
    }

    /**
     * Set whether we're strictly validating that all bean properties have been mapped
     * from corresponding database fields.
     * <p>Default is {@code false}, accepting unpopulated properties in the target bean.
     */
    public void setCheckFullyPopulated(boolean checkFullyPopulated) {
        this.checkFullyPopulated = checkFullyPopulated;
    }

    /**
     * Return whether we're strictly validating that all bean properties have been
     * mapped from corresponding database fields.
     */
    public boolean isCheckFullyPopulated() {
        return this.checkFullyPopulated;
    }

    /**
     * Set whether we're defaulting Java primitives in the case of mapping a null value
     * from corresponding database fields.
     * <p>Default is {@code false}, throwing an exception when nulls are mapped to Java primitives.
     */
    public void setPrimitivesDefaultedForNullValue(boolean primitivesDefaultedForNullValue) {
        this.primitivesDefaultedForNullValue = primitivesDefaultedForNullValue;
    }

    /**
     * Return whether we're defaulting Java primitives in the case of mapping a null value
     * from corresponding database fields.
     */
    public boolean isPrimitivesDefaultedForNullValue() {
        return this.primitivesDefaultedForNullValue;
    }

    /**
     * Set a {@link ConversionService} for binding JDBC values to bean properties,
     * or {@code null} for none.
     * <p>Default is a {@link DefaultConversionService}, as of Spring 4.3. This
     * provides support for {@code java.time} conversion and other special types.
     * @since 4.3
     * @see #initBeanWrapper(BeanWrapper)
     */
    public void setConversionService(@Nullable ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Return a {@link ConversionService} for binding JDBC values to bean properties,
     * or {@code null} if none.
     * @since 4.3
     */
    @Nullable
    public ConversionService getConversionService() {
        return this.conversionService;
    }


    /**
     * Initialize the mapping meta-data for the given class.
     * @param mappedClass the mapped class
     */
    protected void initialize(Class<T> mappedClass) {
        this.mappedClass = mappedClass;
        this.mappedFields = new HashMap<>();
        this.mappedProperties = new HashSet<>();

        try {
            TableInfo tableInfo = TableInfoBuilder.getTableInfo(mappedClass);
            for (Field field : tableInfo.getFieldList()) {
                String name;
                if(field.getName().equals(tableInfo.getPkFieldName())){
                    name =tableInfo.getPkColumnName();
                }else{
                    MyField annotation = field.getAnnotation(MyField.class);
                    name = (annotation!=null&&StringUtils.hasText(annotation.value()))?
                            annotation.value().trim():field.getName();
                }
                PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(mappedClass, field.getName());
                String lowerCaseName = lowerCaseName(name);
                this.mappedFields.put(lowerCaseName, pd);
                String underscoreName = underscoreName(name);
                if (!lowerCaseName.equals(underscoreName)) {
                    this.mappedFields.put(underscoreName, pd);
                }
                this.mappedProperties.add(field.getName());
            }
        }catch(Exception e){
            for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(mappedClass)) {
                if (pd.getWriteMethod() != null) {
                    String lowerCaseName = lowerCaseName(pd.getName());
                    this.mappedFields.put(lowerCaseName, pd);
                    String underscoreName = underscoreName(pd.getName());
                    if (!lowerCaseName.equals(underscoreName)) {
                        this.mappedFields.put(underscoreName, pd);
                    }
                    this.mappedProperties.add(pd.getName());
                }
            }
        }
    }

    /**
     * Remove the specified property from the mapped fields.
     * @param propertyName the property name (as used by property descriptors)
     * @since 5.3.9
     */
    protected void suppressProperty(String propertyName) {
        if (this.mappedFields != null) {
            this.mappedFields.remove(lowerCaseName(propertyName));
            this.mappedFields.remove(underscoreName(propertyName));
        }
    }

    /**
     * Convert the given name to lower case.
     * By default, conversions will happen within the US locale.
     * @param name the original name
     * @return the converted name
     * @since 4.2
     */
    protected String lowerCaseName(String name) {
        return name.toLowerCase(Locale.US);
    }

    /**
     * Convert a name in camelCase to an underscored name in lower case.
     * Any upper case letters are converted to lower case with a preceding underscore.
     * @param name the original name
     * @return the converted name
     * @since 4.2
     * @see #lowerCaseName
     */
    protected String underscoreName(String name) {
        if (!StringUtils.hasLength(name)) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }


    /**
     * Extract the values for all columns in the current row.
     * <p>Utilizes public setters and result set meta-data.
     * @see java.sql.ResultSetMetaData
     */
    @Override
    public T mapRow(ResultSet rs, int rowNumber) throws SQLException {
        BeanWrapperImpl bw = new BeanWrapperImpl();
        initBeanWrapper(bw);

        T mappedObject = constructMappedInstance(rs, bw);
        bw.setBeanInstance(mappedObject);

        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        Set<String> populatedProperties = (isCheckFullyPopulated() ? new HashSet<>() : null);

        for (int index = 1; index <= columnCount; index++) {
            String column = JdbcUtils.lookupColumnName(rsmd, index);
            String field = lowerCaseName(StringUtils.delete(column, " "));
            PropertyDescriptor pd = (this.mappedFields != null ? this.mappedFields.get(field) : null);
            if (pd != null) {
                try {
                    Object value = getColumnValue(rs, index, pd);
                    if (rowNumber == 0 && logger.isDebugEnabled()) {
                        logger.debug("Mapping column '" + column + "' to property '" + pd.getName() +
                                "' of type '" + ClassUtils.getQualifiedName(pd.getPropertyType()) + "'");
                    }
                    try {
                        bw.setPropertyValue(pd.getName(), value);
                    }
                    catch (TypeMismatchException ex) {
                        if (value == null && this.primitivesDefaultedForNullValue) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Intercepted TypeMismatchException for row " + rowNumber +
                                        " and column '" + column + "' with null value when setting property '" +
                                        pd.getName() + "' of type '" +
                                        ClassUtils.getQualifiedName(pd.getPropertyType()) +
                                        "' on object: " + mappedObject, ex);
                            }
                        }
                        else {
                            throw ex;
                        }
                    }
                    if (populatedProperties != null) {
                        populatedProperties.add(pd.getName());
                    }
                }
                catch (NotWritablePropertyException ex) {
                    throw new DataRetrievalFailureException(
                            "Unable to map column '" + column + "' to property '" + pd.getName() + "'", ex);
                }
            }
            else {
                // No PropertyDescriptor found
                if (rowNumber == 0 && logger.isDebugEnabled()) {
                    logger.debug("No property found for column '" + column + "' mapped to field '" + field + "'");
                }
            }
        }

        if (populatedProperties != null && !populatedProperties.equals(this.mappedProperties)) {
            throw new InvalidDataAccessApiUsageException("Given ResultSet does not contain all fields " +
                    "necessary to populate object of " + this.mappedClass + ": " + this.mappedProperties);
        }

        return mappedObject;
    }

    /**
     * Construct an instance of the mapped class for the current row.
     * @param rs the ResultSet to map (pre-initialized for the current row)
     * @param tc a TypeConverter with this RowMapper's conversion service
     * @return a corresponding instance of the mapped class
     * @throws SQLException if an SQLException is encountered
     * @since 5.3
     */
    protected T constructMappedInstance(ResultSet rs, TypeConverter tc) throws SQLException  {
        Assert.state(this.mappedClass != null, "Mapped class was not specified");
        return BeanUtils.instantiateClass(this.mappedClass);
    }

    /**
     * Initialize the given BeanWrapper to be used for row mapping.
     * To be called for each row.
     * <p>The default implementation applies the configured {@link ConversionService},
     * if any. Can be overridden in subclasses.
     * @param bw the BeanWrapper to initialize
     * @see #getConversionService()
     * @see BeanWrapper#setConversionService
     */
    protected void initBeanWrapper(BeanWrapper bw) {
        ConversionService cs = getConversionService();
        if (cs != null) {
            bw.setConversionService(cs);
        }
    }

    /**
     * Retrieve a JDBC object value for the specified column.
     * <p>The default implementation delegates to
     * {@link #getColumnValue(ResultSet, int, Class)}.
     * @param rs is the ResultSet holding the data
     * @param index is the column index
     * @param pd the bean property that each result object is expected to match
     * @return the Object value
     * @throws SQLException in case of extraction failure
     * @see #getColumnValue(ResultSet, int, Class)
     */
    @Nullable
    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
        return JdbcUtils.getResultSetValue(rs, index, pd.getPropertyType());
    }

    /**
     * Retrieve a JDBC object value for the specified column.
     * <p>The default implementation calls
     * {@link JdbcUtils#getResultSetValue(java.sql.ResultSet, int, Class)}.
     * Subclasses may override this to check specific value types upfront,
     * or to post-process values return from {@code getResultSetValue}.
     * @param rs is the ResultSet holding the data
     * @param index is the column index
     * @param paramType the target parameter type
     * @return the Object value
     * @throws SQLException in case of extraction failure
     * @since 5.3
     * @see org.springframework.jdbc.support.JdbcUtils#getResultSetValue(java.sql.ResultSet, int, Class)
     */
    @Nullable
    protected Object getColumnValue(ResultSet rs, int index, Class<?> paramType) throws SQLException {
        return JdbcUtils.getResultSetValue(rs, index, paramType);
    }


    /**
     * Static factory method to create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @see #newInstance(Class, ConversionService)
     */
    public static <T> MyBeanPropertyRowMapper<T> newInstance(Class<T> mappedClass) {
        return new MyBeanPropertyRowMapper<>(mappedClass);
    }

    /**
     * Static factory method to create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param conversionService the {@link ConversionService} for binding
     * JDBC values to bean properties, or {@code null} for none
     * @since 5.2.3
     * @see #newInstance(Class)
     * @see #setConversionService
     */
    public static <T> MyBeanPropertyRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {

        MyBeanPropertyRowMapper<T> rowMapper = newInstance(mappedClass);
        rowMapper.setConversionService(conversionService);
        return rowMapper;
    }
}
