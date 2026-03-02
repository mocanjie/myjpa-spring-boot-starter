package io.github.mocanjie.base.myjpa;

/**
 * 标记接口：所有映射到数据库表的实体类必须实现此接口，同时必须标注 {@code @MyTable}。
 * <p>
 * 编译期规范（APT 自动校验）：
 * <ul>
 *   <li>标注了 {@code @MyTable} 的类 → 必须实现 {@code MyTableEntity}</li>
 *   <li>实现了 {@code MyTableEntity} 的具体类 → 必须标注 {@code @MyTable}</li>
 * </ul>
 * 违反任一规则将在编译时产生错误，而非运行时异常。
 */
public interface MyTableEntity {}
