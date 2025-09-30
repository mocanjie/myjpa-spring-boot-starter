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
        // 保存
        User user = new User();
        user.setUsername("test");
        save(user);

        // 查询
        User found = getById(1L);

        // 自定义 SQL 查询（自动添加逻辑删除条件）
        List<User> users = queryListForSqlWithDeleteCondition(
            "SELECT * FROM sys_user WHERE age > ?",
            18
        );

        // 逻辑删除
        deleteById(1L);
    }
}
```

## 🔧 核心 API

### IBaseService 接口

#### 基础 CRUD
- `save(T entity)` - 保存实体
- `update(T entity)` - 更新实体
- `deleteById(ID id)` - 删除（支持逻辑删除）
- `getById(ID id)` - 根据 ID 查询
- `listAll()` - 查询所有

#### 批量操作
- `batchSave(List<T> entities)` - 批量保存
- `batchUpdate(List<T> entities)` - 批量更新
- `batchDeleteByIds(List<ID> ids)` - 批量删除

#### 自定义查询
- `queryListForSql(String sql, Object... params)` - 原始 SQL 查询
- `queryListForSqlWithDeleteCondition(String sql, Object... params)` - 自动添加逻辑删除条件
- `queryPageForSqlWithDeleteCondition(String sql, int pageNum, int pageSize, Object... params)` - 分页查询

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

- Java 17+
- Spring Boot 3.x
- Maven 3.6+

## 📄 许可证

本项目采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证。

## 👥 作者

- **mocanjie** - [GitHub](https://github.com/mocanjie)

## 🔗 相关链接

- [GitHub 仓库](https://github.com/mocanjie/myjpa-spring-boot-starter)
- [问题反馈](https://github.com/mocanjie/myjpa-spring-boot-starter/issues)
