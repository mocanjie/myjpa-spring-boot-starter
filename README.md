# myjpa-spring-boot-starter

一个基于 Spring JDBC Template 的轻量级 ORM 框架，提供类似 JPA 的注解驱动开发体验，支持多数据库和智能 SQL 增强。

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mocanjie/myjpa-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.mocanjie/myjpa-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## ✨ 核心特性

### 📝 注解驱动开发
- `@MyTable` - 实体类与数据库表映射，支持逻辑删除配置
- `@MyField` - 字段与列映射，支持序列化控制
- `MyTableEntity` - 标记接口，编译期强制规范（APT 自动校验）
- 零 XML 配置，开箱即用

### 🔗 Lambda 链式查询 API
类型安全的条件构造器，告别手写 SQL 字符串：

```java
// 查询列表
List<UserPO> users = lambdaQuery(UserPO.class)
    .eq(UserPO::getStatus, 1)
    .like(UserPO::getName, "张")
    .orderByDesc(UserPO::getCreateTime)
    .list();

// 查询单条
UserPO user = lambdaQuery(UserPO.class)
    .eq(UserPO::getId, 1L)
    .one();

// 统计
long count = lambdaQuery(UserPO.class)
    .eq(UserPO::getStatus, 1)
    .count();

// 分页
Pager<UserPO> page = lambdaQuery(UserPO.class)
    .ge(UserPO::getAge, 18)
    .page(new Pager<>(1, 10));

// 存在性判断
boolean exists = lambdaQuery(UserPO.class)
    .eq(UserPO::getPhone, "138...")
    .exists();
```

生成的 SQL 自动经过逻辑删除 + 租户隔离管道，无需额外处理。

### 🗄️ 多数据库支持
- MySQL
- Oracle
- SQL Server
- PostgreSQL
- KingbaseES（人大金仓）

自动识别数据库类型，生成对应的 SQL 方言。

### 🚀 智能 SQL 增强

#### 自动逻辑删除条件注入
框架基于 JSqlParser 5.3 实现智能 SQL 解析和改写，自动为查询语句添加逻辑删除条件：

```sql
-- 原始 SQL
SELECT * FROM user

-- 自动转换为
SELECT * FROM user WHERE user.delete_flag = 0

-- JOIN 查询智能处理
SELECT u.*, r.role_name
FROM user u
LEFT JOIN role r ON u.role_id = r.id

-- 自动转换为
SELECT u.*, r.role_name
FROM user u
LEFT JOIN role r ON u.role_id = r.id AND r.is_deleted = 0
WHERE u.delete_flag = 0
```

#### JOIN 条件优化策略
- **主表（FROM）**：逻辑删除条件添加到 WHERE 子句
- **LEFT/RIGHT JOIN**：条件添加到 ON 子句，保留外连接语义
- **INNER JOIN**：条件添加到 WHERE 子句，优化查询性能

### 🏢 多租户隔离（可选）

基于数据库列自动检测，零侵入接入多租户支持：
- 启动时扫描数据库，自动发现含有 `tenant_id` 列（可配置）的表
- 查询时自动追加 `AND table.tenant_id = :tenantId` 条件，无需手动拼接
- `tenantId = null` 时视为超级管理员，跳过过滤
- 支持方法级临时跳过

### 📦 零配置包扫描
- 自动检测主应用包路径
- 智能扫描 `@MyTable` 注解的实体类
- 无需手动配置扫描路径

### 🔍 表结构校验
- 启动时自动校验实体类与数据库表结构一致性
- 发现不匹配时输出警告信息
- 支持通过配置开启/关闭

---

## 📦 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>io.github.mocanjie</groupId>
    <artifactId>myjpa-spring-boot-starter</artifactId>
    <version>spring3.jsql</version>
</dependency>
```

### 配置数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver

# 可选配置
myjpa:
  showsql: true          # 显示 SQL 日志（默认 true）
  show-sql-time: false   # 打印每条 SQL 的实际执行耗时，INFO 级别输出（默认 false）
  validate-schema: true  # 启动时校验表结构
  tenant:
    enabled: false       # 多租户隔离开关（默认关闭，按需开启）
    column: tenant_id    # 租户字段列名（可自定义，如 org_id）
```

### 定义实体类

实体类必须同时满足两个条件（APT 编译期校验，缺一报错）：
1. 标注 `@MyTable`
2. 实现 `MyTableEntity` 接口

```java
import io.github.mocanjie.base.myjpa.MyTableEntity;
import io.github.mocanjie.base.myjpa.annotation.MyTable;
import io.github.mocanjie.base.myjpa.annotation.MyField;

@MyTable(
    value      = "sys_user",      // 表名（必填）
    pkColumn   = "id",            // 主键列名（默认 "id"）
    pkField    = "id",            // 主键字段名（默认 "id"）
    delColumn  = "delete_flag",   // 逻辑删除列名（默认 "delete_flag"）
    delField   = "deleteFlag",    // 逻辑删除字段名（默认 "deleteFlag"）
    delValue   = 1                // 已删除标记值（默认 1）
)
public class UserPO implements MyTableEntity {

    private Long id;

    @MyField("user_name")   // 列名与字段名不同时使用
    private String userName;

    private Integer status;

    @MyField(serialize = false)  // serialize=false：排除在 INSERT/UPDATE 之外
    private String deleteFlag;

    // getters and setters
}
```

> **说明：** 若字段名符合驼峰转下划线规则（如 `userName` → `user_name`），无需标注 `@MyField`，框架自动转换。

### 创建 Service

```java
@Service
public class UserService extends BaseServiceImpl {

    public void example() {
        // ─────────────────────────────────────────────
        // Lambda 链式查询（推荐，类型安全）
        // ─────────────────────────────────────────────

        // 条件查询列表
        List<UserPO> users = lambdaQuery(UserPO.class)
            .eq(UserPO::getStatus, 1)
            .like(UserPO::getUserName, "张")
            .orderByDesc(UserPO::getId)
            .list();

        // 查询单条
        UserPO user = lambdaQuery(UserPO.class)
            .eq(UserPO::getId, 1L)
            .one();

        // 统计
        long count = lambdaQuery(UserPO.class)
            .eq(UserPO::getStatus, 1)
            .count();

        // 分页
        Pager<UserPO> page = lambdaQuery(UserPO.class)
            .ge(UserPO::getStatus, 0)
            .orderByAsc(UserPO::getId)
            .page(new Pager<>(1, 10));

        // 指定返回列
        List<UserPO> partial = lambdaQuery(UserPO.class)
            .select(UserPO::getId, UserPO::getUserName)
            .eq(UserPO::getStatus, 1)
            .list();

        // ─────────────────────────────────────────────
        // 写操作
        // ─────────────────────────────────────────────

        // 插入（自动生成 ID）
        UserPO newUser = new UserPO();
        newUser.setUserName("张三");
        insertPO(newUser);

        // 批量插入（指定批次大小）
        List<UserPO> batch = List.of(user1, user2, user3);
        batchInsertPO(batch, true, 100);

        // 更新（忽略 null 字段）
        user.setUserName("李四");
        updatePO(user);

        // 强制更新指定字段（即使为 null）
        updatePO(user, "userName", "status");

        // 根据 ID 查询
        UserPO found = queryById(1L, UserPO.class);

        // 删除（取决于 @MyTable 配置：逻辑删除或物理删除）
        delPO(user);
        delByIds(UserPO.class, 1L, 2L, 3L);

        // ─────────────────────────────────────────────
        // 自定义 SQL 查询（Map 参数）
        // ─────────────────────────────────────────────
        Map<String, Object> params = new HashMap<>();
        params.put("age", 18);
        List<UserPO> activeUsers = queryListForSql(
            "SELECT * FROM sys_user WHERE age > :age",
            params,
            UserPO.class
        );

        // 分页（自定义 SQL）
        Pager<UserPO> pager = new Pager<>(1, 10);
        queryPageForSql("SELECT * FROM sys_user WHERE age > :age", params, pager, UserPO.class);
    }
}
```

---

## 🔧 核心 API

### Lambda 链式查询

通过 `lambdaQuery(Class<T>)` 获得 `LambdaQueryWrapper<T>`，链式拼接条件后调用终结方法执行查询。

#### 条件方法（全部 AND 连接）

| 方法 | 示例 | 生成条件 |
|---|---|---|
| `eq(fn, val)` | `.eq(User::getName, "张三")` | `name = :p` |
| `ne(fn, val)` | `.ne(User::getStatus, 0)` | `status != :p` |
| `gt(fn, val)` | `.gt(User::getAge, 18)` | `age > :p` |
| `ge(fn, val)` | `.ge(User::getAge, 18)` | `age >= :p` |
| `lt(fn, val)` | `.lt(User::getAge, 60)` | `age < :p` |
| `le(fn, val)` | `.le(User::getAge, 60)` | `age <= :p` |
| `like(fn, val)` | `.like(User::getName, "张")` | `name LIKE '%张%'` |
| `likeLeft(fn, val)` | `.likeLeft(User::getName, "三")` | `name LIKE '%三'` |
| `likeRight(fn, val)` | `.likeRight(User::getName, "张")` | `name LIKE '张%'` |
| `in(fn, collection)` | `.in(User::getId, ids)` | `id IN (:p)` |
| `notIn(fn, collection)` | `.notIn(User::getId, ids)` | `id NOT IN (:p)` |
| `between(fn, v1, v2)` | `.between(User::getAge, 18, 60)` | `age BETWEEN :p0 AND :p1` |
| `isNull(fn)` | `.isNull(User::getRemark)` | `remark IS NULL` |
| `isNotNull(fn)` | `.isNotNull(User::getRemark)` | `remark IS NOT NULL` |

#### 辅助方法

```java
// 指定返回列（默认 SELECT *）
.select(User::getId, User::getName)

// 排序
.orderByAsc(User::getAge)
.orderByDesc(User::getCreateTime)
```

#### 终结方法

```java
List<T>    .list()         // 查询列表
T          .one()          // 查询单条（无结果返回 null）
long       .count()        // 统计数量
Pager<T>   .page(pager)    // 分页查询
boolean    .exists()       // 存在性判断
```

---

### IBaseService 接口

#### 插入操作
```java
<PO extends MyTableEntity> Serializable insertPO(PO po);
<PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId);
<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos);
<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);
<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, int batchSize);
<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);
```

#### 更新操作
```java
<PO extends MyTableEntity> int updatePO(PO po);
<PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull);
<PO extends MyTableEntity> int updatePO(PO po, String... forceUpdateProperties);
```

#### 查询操作
```java
// 根据 ID 查询
<PO extends MyTableEntity> PO queryById(String id, Class<PO> clazz);
<PO extends MyTableEntity> PO queryById(Long id, Class<PO> clazz);

// 自定义 SQL 查询（Object 参数）
<T> List<T>    queryListForSql(String sql, Object param, Class<T> clazz);
<T> T          querySingleForSql(String sql, Object param, Class<T> clazz);
<T> Pager<T>   queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

// 自定义 SQL 查询（Map 参数）
<T> List<T>    queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);
<T> T          querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);
<T> Pager<T>   queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);
```

> **说明：** `queryXxxForSql` 系列方法的返回类型 `<T>` 不要求 `extends MyTableEntity`，可直接映射到 DTO/VO 等任意 POJO。

#### 删除操作
```java
<PO extends MyTableEntity> int delPO(PO po);
<PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id);
```

---

### 自动逻辑删除条件注入

所有查询方法均已内置智能删除条件注入，**无需任何额外调用**。

```java
// 写普通 SQL 即可，框架自动追加删除条件
// 实际执行：SELECT * FROM sys_user WHERE age > :age AND sys_user.delete_flag = 0
List<UserPO> users = queryListForSql(
    "SELECT * FROM sys_user WHERE age > :age",
    params,
    UserPO.class
);

// JOIN 查询：LEFT JOIN 条件追加到 ON 子句
// 实际执行：
//   SELECT u.*, r.role_name
//   FROM sys_user u
//   LEFT JOIN role r ON u.role_id = r.id AND r.is_deleted = 0
//   WHERE u.age > :age AND u.delete_flag = 0
List<UserVO> userWithRoles = queryListForSql(
    "SELECT u.*, r.role_name FROM sys_user u LEFT JOIN role r ON u.role_id = r.id WHERE u.age > :age",
    params,
    UserVO.class
);
```

> **注意：** 若某张表未配置 `@MyTable` 逻辑删除字段，框架不会对该表追加任何条件，行为与普通查询完全一致。

---

### 多租户隔离（可选）

> 默认**关闭**，需显式配置 `myjpa.tenant.enabled=true` 开启。

#### 快速接入

**第一步：开启配置**

```yaml
myjpa:
  tenant:
    enabled: true
    column: tenant_id  # 与数据库实际列名一致
```

**第二步：实现 `TenantIdProvider` 接口**（只需一个 `@Bean`）

```java
@Bean
public TenantIdProvider tenantIdProvider() {
    // 从当前登录上下文获取租户ID，null 表示超管
    return () -> SecurityContextHolder.getContext().getTenantId();
}
```

> 若不使用 Spring Security，也可通过 `TenantContext.setTenantId(id)` 在拦截器中手动设置（ThreadLocal 方式）。

#### SQL 自动改写示例

```sql
-- 原始 SQL
SELECT * FROM user WHERE age > 18

-- 自动转换为（tenantId = 5）
SELECT * FROM user WHERE age > 18 AND user.tenant_id = 5

-- JOIN 查询（user 和 order 都有 tenant_id）
SELECT u.id, o.amount FROM user u LEFT JOIN `order` o ON u.id = o.user_id

-- 自动转换为
SELECT u.id, o.amount
FROM user u
LEFT JOIN `order` o ON u.id = o.user_id AND o.tenant_id = 5
WHERE u.tenant_id = 5
```

#### 临时跳过租户条件

```java
// 方式一：Lambda 形式（推荐，自动恢复）
List<UserPO> allUsers = TenantContext.withoutTenant(
    () -> queryListForSql("SELECT * FROM user", null, UserPO.class)
);

// 方式二：手动控制
TenantContext.skip();
try {
    return queryListForSql("SELECT * FROM user", null, UserPO.class);
} finally {
    TenantContext.restore();
}
```

---

### 参数绑定说明

**重要：** 本框架使用**命名参数**而非 JDBC 的 `?` 占位符。

```java
// ✅ 正确：命名参数
Map<String, Object> params = Map.of("username", "test", "age", 18);
queryListForSql(
    "SELECT * FROM sys_user WHERE username = :username AND age > :age",
    params,
    UserPO.class
);

// ❌ 错误：不支持 ? 占位符
queryListForSql("SELECT * FROM sys_user WHERE username = ?", ...);
```

也可以使用 POJO 对象作为参数，框架自动从属性中提取命名参数值：

```java
UserQueryParam param = new UserQueryParam();
param.setUsername("test");
param.setAge(18);
queryListForSql(
    "SELECT * FROM sys_user WHERE username = :username AND age > :age",
    param,
    UserPO.class
);
```

---

## 🛡️ 编译期规范校验（APT）

框架内置 APT 注解处理器，在**编译时**强制执行实体类规范，防止配置遗漏导致的运行时异常。

### 双向绑定规则

| 规则 | 说明 |
|---|---|
| **Rule-1** | 标注了 `@MyTable` 的类必须实现 `MyTableEntity` 接口 |
| **Rule-2** | 实现了 `MyTableEntity` 的具体类必须标注 `@MyTable` |

违反任一规则将在 `mvn compile` 时产生**编译错误**：

```
// 违反 Rule-1：有 @MyTable 但未实现 MyTableEntity
error: [@MyTable 规范] com.example.UserPO 标注了 @MyTable 但未实现 MyTableEntity 接口

// 违反 Rule-2：实现了 MyTableEntity 但无 @MyTable
error: [@MyTable 规范] com.example.UserPO 实现了 MyTableEntity 接口但未标注 @MyTable 注解
```

> **说明：** 抽象类和接口不受 Rule-2 约束，可作为中间基类使用。

---

## 🏗️ 架构设计

```
MyJpaAutoConfiguration（自动配置）
    ↓
TableInfoBuilder（元数据构建 + APT 规范校验）
    ↓
BaseServiceImpl（服务层）
    ├── lambdaQuery() → LambdaQueryWrapper（链式条件构造）
    └── queryXxxForSql / insertPO / updatePO / delPO ...
    ↓
BaseDaoImpl（DAO 层）
    ↓
JSqlDynamicSqlParser（逻辑删除 + 租户条件注入）
    ↓
SqlBuilder（多数据库 SQL 方言）
    ↓
JdbcTemplate（数据访问）
```

### 核心组件

| 组件 | 说明 |
|---|---|
| `MyTableEntity` | 实体标记接口，APT 与泛型边界双重规范 |
| `MyTableAnnotationProcessor` | APT 处理器，编译期校验 `@MyTable` ↔ `MyTableEntity` 双向绑定 |
| `LambdaQueryWrapper` | Lambda 链式查询构造器，类型安全的条件拼接 |
| `TableCacheManager` | 缓存 `@MyTable` 注解信息及租户表集合 |
| `JSqlDynamicSqlParser` | 基于 JSqlParser 的 SQL 解析和改写（逻辑删除 + 租户隔离） |
| `SqlBuilder` | 多数据库 SQL 方言生成器 |
| `DatabaseSchemaValidator` | 启动时校验表结构，同步扫描并注册租户表 |
| `TenantIdProvider` | 租户 ID 获取 SPI 接口 |
| `TenantContext` | ThreadLocal 工具类，支持编程式设置租户 ID 及临时跳过 |

---

## 🔨 开发命令

```bash
# 编译项目（JDK 21）
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn clean compile

# 运行测试
JAVA_HOME=... mvn test

# 打包
JAVA_HOME=... mvn clean package

# 安装到本地仓库
JAVA_HOME=... mvn clean install

# 发布到 Maven Central
JAVA_HOME=... mvn clean deploy -P release
```

## 📋 系统要求

- Java 21+
- Spring Boot 3.x
- Maven 3.6+

## 📄 许可证

本项目采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证。

## 👥 作者

- **mocanjie** - [GitHub](https://github.com/mocanjie)

## 🔗 相关链接

- [GitHub 仓库](https://github.com/mocanjie/myjpa-spring-boot-starter)
- [问题反馈](https://github.com/mocanjie/myjpa-spring-boot-starter/issues)
