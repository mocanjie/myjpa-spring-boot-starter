# myjpa-spring-boot-starter

一个基于 Spring JDBC Template 的轻量级 ORM 框架，提供类似 JPA 的注解驱动开发体验，支持多数据库和智能 SQL 增强。

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mocanjie/myjpa-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.mocanjie/myjpa-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## ✨ 核心特性

### 📝 注解驱动开发
- `@MyTable` - 实体类与数据库表映射，支持逻辑删除配置
- `@MyField` - 字段与列映射，支持序列化控制
- 零 XML 配置，开箱即用

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

### 📦 零配置包扫描
- 自动检测主应用包路径
- 智能扫描 `@MyTable` 注解的实体类
- 无需手动配置扫描路径

### 🔍 表结构校验
- 启动时自动校验实体类与数据库表结构一致性
- 发现不匹配时输出警告信息
- 支持通过配置开启/关闭

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
  showsql: true  # 显示 SQL 日志
```

### 定义实体类

```java
@MyTable(name = "sys_user", deleteField = "delete_flag", deleteValue = "1")
public class User {
    @MyField(name = "id", isPrimaryKey = true)
    private Long id;

    @MyField(name = "username")
    private String username;

    @MyField(name = "delete_flag")
    private Integer deleteFlag;

    // getters and setters
}
```

### 创建 Service

```java
@Service
public class UserService extends BaseServiceImpl<User> {

    // 继承了基础 CRUD 方法
    public void example() {
        // 1. 保存单个实体
        User user = new User();
        user.setUsername("test");
        insertPO(user);  // 自动生成ID
        // 或者手动控制ID生成：insertPO(user, false)

        // 2. 批量保存
        List<User> users = Arrays.asList(user1, user2, user3);
        batchInsertPO(users);  // 自动生成ID
        // 或者：batchInsertPO(users, true, 100)  // 指定批次大小

        // 3. 更新实体
        user.setUsername("updated");
        updatePO(user);  // 更新所有字段
        // 或者：updatePO(user, true)  // 忽略null值
        // 或者：updatePO(user, "username", "email")  // 强制更新指定字段

        // 4. 根据ID查询
        User found = queryById(1L, User.class);
        // 或者：queryById("user123", User.class)

        // 5. 根据字段查询单条记录
        User userByName = querySingleByField("username", "test", User.class);

        // 6. 自定义SQL查询（使用Map参数 + 命名参数）
        Map<String, Object> params = new HashMap<>();
        params.put("age", 18);
        params.put("status", 1);
        List<User> activeUsers = queryListForSql(
            "SELECT * FROM sys_user WHERE age > :age AND status = :status",
            params,
            User.class
        );

        // 7. 自定义SQL查询（使用对象参数）
        UserQueryParam queryParam = new UserQueryParam();
        queryParam.setAge(18);
        List<User> users2 = queryListForSql(
            "SELECT * FROM sys_user WHERE age > :age",
            queryParam,
            User.class
        );

        // 8. 查询单条记录
        User single = querySingleForSql(
            "SELECT * FROM sys_user WHERE username = :username",
            params,
            User.class
        );

        // 9. 分页查询
        Pager<User> pager = new Pager<>(1, 10);  // 第1页，每页10条
        Pager<User> result = queryPageForSql(
            "SELECT * FROM sys_user WHERE age > :age",
            params,
            pager,
            User.class
        );
        List<User> pageData = result.getRows();
        long total = result.getTotalRows();

        // 10. 删除实体
        delPO(user);  // 逻辑删除或物理删除（取决于@MyTable配置）

        // 11. 批量删除
        delByIds(User.class, 1L, 2L, 3L);  // 可变参数
    }
}
```

## 🔧 核心 API

### IBaseService 接口

所有 Service 继承 `BaseServiceImpl` 后自动拥有以下方法：

#### 插入操作
```java
<PO> Serializable insertPO(PO po);  // 插入单条，自动生成ID
<PO> Serializable insertPO(PO po, boolean autoCreateId);  // 控制是否自动生成ID
<PO> Serializable batchInsertPO(List<PO> pos);  // 批量插入，自动生成ID
<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);  // 批量插入，控制ID生成
<PO> Serializable batchInsertPO(List<PO> pos, int batchSize);  // 批量插入，指定批次大小
<PO> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);  // 完整控制
```

#### 更新操作
```java
<PO> int updatePO(PO po);  // 更新所有字段
<PO> int updatePO(PO po, boolean ignoreNull);  // ignoreNull=true时不更新null字段
<PO> int updatePO(PO po, String... forceUpdateProperties);  // 强制更新指定字段（即使为null）
```

#### 查询操作
```java
// 根据ID查询
<PO> PO queryById(String id, Class<PO> clazz);
<PO> PO queryById(Long id, Class<PO> clazz);

// 根据字段查询单条记录
<T> T querySingleByField(String fieldName, String fieldValue, Class<T> clazz);

// 自定义SQL查询（Object参数方式）
<T> List<T> queryListForSql(String sql, Object param, Class<T> clazz);
<T> T querySingleForSql(String sql, Object param, Class<T> clazz);
<T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

// 自定义SQL查询（Map参数方式）
<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);
<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);
<T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);
```

#### 删除操作
```java
<PO> int delPO(PO po);  // 删除单个实体（物理删除或逻辑删除取决于@MyTable配置）
<PO> int delByIds(Class<PO> clazz, Object... id);  // 批量删除（支持可变参数）
```

### 自动逻辑删除条件注入

所有查询方法均已内置智能删除条件注入，**无需任何额外调用**。框架会自动解析 SQL 中涉及的表名，对配置了 `@MyTable` 逻辑删除字段的表追加对应条件。

```java
@Service
public class UserService extends BaseServiceImpl<User> {

    public void example() {
        Map<String, Object> params = new HashMap<>();
        params.put("age", 18);

        // 写普通 SQL 即可，框架自动追加删除条件
        // 实际执行：SELECT * FROM sys_user WHERE age > :age AND sys_user.delete_flag = 0
        List<User> users = queryListForSql(
            "SELECT * FROM sys_user WHERE age > :age",
            params,
            User.class
        );

        // JOIN 查询同样自动处理，LEFT JOIN 条件追加到 ON 子句
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
    }
}
```

> **注意：** 若某张表未配置 `@MyTable` 逻辑删除字段，框架不会对该表追加任何条件，行为与普通查询完全一致。

### 参数绑定说明

**重要：** 本框架使用 **命名参数** 而非 JDBC 的 `?` 占位符。

#### ✅ 正确写法（命名参数）
```java
Map<String, Object> params = new HashMap<>();
params.put("username", "test");
params.put("age", 18);

queryListForSql(
    "SELECT * FROM sys_user WHERE username = :username AND age > :age",
    params,
    User.class
);
```

#### ❌ 错误写法（不支持）
```java
// ❌ 不支持 ? 占位符 + 可变参数
queryListForSql(
    "SELECT * FROM sys_user WHERE username = ? AND age > ?",
    "test", 18  // 这种方式不支持！
);
```

#### 使用对象作为参数
```java
public class UserQueryParam {
    private String username;
    private Integer age;
    // getters and setters
}

UserQueryParam param = new UserQueryParam();
param.setUsername("test");
param.setAge(18);

// 对象的属性名对应SQL中的命名参数
queryListForSql(
    "SELECT * FROM sys_user WHERE username = :username AND age > :age",
    param,  // 框架会自动从对象中提取属性值
    User.class
);
```

## 🏗️ 架构设计

```
MyJpaAutoConfiguration (自动配置)
    ↓
TableInfoBuilder (元数据构建)
    ↓
BaseServiceImpl (服务层)
    ↓
BaseDaoImpl (DAO 层)
    ↓
SqlBuilder (SQL 生成)
    ↓
JdbcTemplate (数据访问)
```

### 核心组件

- **TableCacheManager** - 缓存 `@MyTable` 注解信息
- **JSqlDynamicSqlParser** - 基于 JSqlParser 的 SQL 解析和改写
- **SqlBuilder** - 多数据库 SQL 方言生成器
- **DatabaseSchemaValidator** - 数据库表结构校验

## 🔨 开发命令

```bash
# 编译项目
mvn clean compile

# 打包
mvn clean package

# 安装到本地仓库
mvn clean install

# 发布到 Maven Central
mvn clean deploy -P release
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
