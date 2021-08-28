package io.github.mocanjie.base.myjpa.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(TYPE)
public @interface MyTable {
	String value();
	String pkColumn() default "id";
	String pkField() default "id";
	String delColumn() default "delete_flag";
	String delField() default "deleteFlag";
	int delValue() default 1; // 0 未删除，1 已删除
}
